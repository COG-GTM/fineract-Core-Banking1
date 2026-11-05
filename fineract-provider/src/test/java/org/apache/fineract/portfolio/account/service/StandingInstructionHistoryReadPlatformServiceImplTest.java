/**
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
package org.apache.fineract.portfolio.account.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.portfolio.account.data.StandingInstructionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

public class StandingInstructionHistoryReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;

    @Mock
    private PaginationHelper paginationHelper;

    private StandingInstructionHistoryReadPlatformServiceImpl service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        service = new StandingInstructionHistoryReadPlatformServiceImpl(jdbcTemplate, sqlGenerator, paginationHelper);
    }

    private static StandingInstructionDTO dto(String orderBy, String sortOrder) {
        SearchParameters searchParameters = SearchParameters.builder().orderBy(orderBy).sortOrder(sortOrder).build();
        return new StandingInstructionDTO(searchParameters, null, null, null, null, null, null, null);
    }

    private String executedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(any(JdbcTemplate.class), sql.capture(), any(Object[].class), any());
        return sql.getValue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select 1)", "id, (case when (select count(*) from m_appuser)>0 then id end)",
            "(case when (select substring(password,1,1) from m_appuser where id=1)='a' then atsi.id else atsi.name end)",
            "atsi.id desc, (select 1)", "unknown_column" })
    public void rejectsOrderByOutsideAllowlist(String orderBy) {
        assertThrows(PlatformApiDataValidationException.class, () -> service.retrieveAll(dto(orderBy, null)));
        verify(paginationHelper, never()).fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "desc, (select 1)", "asc nulls first", "(select 1)", "descending" })
    public void rejectsSortOrderOtherThanAscOrDesc(String sortOrder) {
        assertThrows(PlatformApiDataValidationException.class, () -> service.retrieveAll(dto("id", sortOrder)));
        verify(paginationHelper, never()).fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any());
    }

    @Test
    public void mapsAllowedOrderByToColumnAndNormalizesSortOrder() {
        service.retrieveAll(dto("executionTime", "desc"));
        assertTrue(executedSql().endsWith(" order by atsih.execution_time DESC"), executedSql());
    }

    @Test
    public void acceptsQualifiedColumnWithoutSortOrder() {
        service.retrieveAll(dto("atsih.amount", null));
        assertTrue(executedSql().endsWith(" order by atsih.amount"), executedSql());
    }

    @Test
    public void omitsOrderByWhenNotRequested() {
        service.retrieveAll(dto(null, "DESC"));
        assertTrue(!executedSql().contains(" order by "), executedSql());
    }
}
