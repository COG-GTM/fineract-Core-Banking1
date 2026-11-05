/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.savings.jobs.generateadhocclientschhedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.adhocquery.data.AdHocData;
import org.apache.fineract.adhocquery.service.AdHocReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.SqlValidationException;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class GenerateAdhocClientScheduleTaskletTest {

    @Mock
    private AdHocReadPlatformService adHocReadPlatformService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SqlValidator sqlValidator;
    @Mock
    private StepContribution contribution;
    @Mock
    private ChunkContext chunkContext;
    @InjectMocks
    private GenerateAdhocClientScheduleTasklet tasklet;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Kampala", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    private static AdHocData adhoc(final String tableName, final String tableFields, final String query) {
        return new AdHocData().setId(1L).setName("report").setTableName(tableName).setTableFields(tableFields).setQuery(query)
                .setActive(true);
    }

    @Test
    void executesValidAdHoc() throws Exception {
        when(adHocReadPlatformService.retrieveAllActiveAdHocQuery())
                .thenReturn(List.of(adhoc("m_adhoc_client", "client_id", "select id from m_client")));

        assertEquals(RepeatStatus.FINISHED, tasklet.execute(contribution, chunkContext));

        verify(jdbcTemplate).update("INSERT INTO m_adhoc_client(client_id) select id from m_client");
    }

    @Test
    void skipsAdHocWithInjectedTableName() throws Exception {
        when(adHocReadPlatformService.retrieveAllActiveAdHocQuery())
                .thenReturn(List.of(adhoc("m_appuser (username) select 'x'; delete from m_client; insert into m_adhoc_client", "client_id",
                        "select id from m_client")));

        assertEquals(RepeatStatus.FINISHED, tasklet.execute(contribution, chunkContext));

        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    void skipsAdHocWithInjectedTableFields() throws Exception {
        when(adHocReadPlatformService.retrieveAllActiveAdHocQuery())
                .thenReturn(List.of(adhoc("m_adhoc_client", "client_id) select 1; drop table m_client; --", "select id from m_client")));

        assertEquals(RepeatStatus.FINISHED, tasklet.execute(contribution, chunkContext));

        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    void skipsAdHocWhoseStoredQueryFailsSqlValidation() throws Exception {
        final String query = "select id from m_client; delete from m_client";
        when(adHocReadPlatformService.retrieveAllActiveAdHocQuery()).thenReturn(List.of(adhoc("m_adhoc_client", "client_id", query)));
        doThrow(new SqlValidationException("stacked")).when(sqlValidator).validate("adhoc", query);

        assertEquals(RepeatStatus.FINISHED, tasklet.execute(contribution, chunkContext));

        verify(jdbcTemplate, never()).update(anyString());
    }
}
