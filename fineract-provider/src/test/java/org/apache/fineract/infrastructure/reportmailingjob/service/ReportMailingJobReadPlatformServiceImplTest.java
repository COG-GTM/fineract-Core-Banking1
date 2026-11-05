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
package org.apache.fineract.infrastructure.reportmailingjob.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ReportMailingJobReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ColumnValidator columnValidator;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PaginationHelper paginationHelper;

    private ReportMailingJobReadPlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new ReportMailingJobReadPlatformServiceImpl(jdbcTemplate, columnValidator, sqlGenerator, paginationHelper);
    }

    @Test
    void orderByIsMappedToAllowlistedColumnAndSortOrderIsNormalized() {
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        when(paginationHelper.fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(new Page<>(List.of(), 0));

        assertTrue(fetchSql("name", "desc").endsWith(" order by rmj.name DESC"));
        assertTrue(fetchSql("nextRunDateTime", " Asc ").endsWith(" order by rmj.next_run_datetime ASC"));
        assertTrue(fetchSql("rmj.START_DATETIME", null).endsWith(" order by rmj.start_datetime"));
    }

    @Test
    void injectedOrderByIsRejectedBeforeQueryRuns() {
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        for (String orderBy : List.of("(select pg_sleep(5))", "(select 1 from pg_sleep(5))", "(select sleep(5))",
                "(select cast((select password from m_appuser limit 1) as int))", "sr.report_sql", "name desc", "1")) {
            PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                    () -> underTest.retrieveAllReportMailingJobs(SearchParameters.builder().orderBy(orderBy).build()));
            assertEquals("validation.msg.reportmailingjob.orderBy.invalid", e.getErrors().get(0).getUserMessageGlobalisationCode());
        }
        verify(paginationHelper, never()).fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any(RowMapper.class));
    }

    @Test
    void sortOrderOtherThanAscOrDescIsRejected() {
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        for (String sortOrder : List.of("sideways", "desc, (select 1 from pg_sleep(5))", "asc nulls first")) {
            PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                    () -> underTest.retrieveAllReportMailingJobs(SearchParameters.builder().orderBy("name").sortOrder(sortOrder).build()));
            assertEquals("validation.msg.reportmailingjob.sortOrder.invalid", e.getErrors().get(0).getUserMessageGlobalisationCode());
        }
        verify(paginationHelper, never()).fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any(RowMapper.class));
    }

    private String fetchSql(String orderBy, String sortOrder) {
        underTest.retrieveAllReportMailingJobs(SearchParameters.builder().orderBy(orderBy).sortOrder(sortOrder).build());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper, atLeastOnce()).fetchPage(any(JdbcTemplate.class), sql.capture(), any(Object[].class),
                any(RowMapper.class));
        return sql.getValue().trim();
    }
}
