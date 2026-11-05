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
package org.apache.fineract.portfolio.savings.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepositAccountOnHoldTransactionReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PaginationHelper paginationHelper;

    private DepositAccountOnHoldTransactionReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        when(sqlGenerator.limit(any(Integer.class))).thenReturn("limit 10");
        service = new DepositAccountOnHoldTransactionReadPlatformServiceImpl(jdbcTemplate, sqlGenerator, paginationHelper);
    }

    @ParameterizedTest
    @ValueSource(strings = { "tr.id * sign(ascii(substr((select password from m_appuser limit 1),1,1)) - 97)",
            "(select 1 from pg_sleep(3)), tr.id", "sleep(3), tr.id", "tr.id, (select 1)", "case when 1 then tr.id else tr.amount end",
            "savingsClientName", "TR.ID", "m_appuser.password" })
    void rejectsOrderByOutsideAllowlist(String orderBy) {
        SearchParameters params = SearchParameters.builder().orderBy(orderBy).build();

        assertThrows(PlatformApiDataValidationException.class, () -> service.retriveAll(1L, null, params));
        verify(paginationHelper, never()).fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any(RowMapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "asc, (select 1 from pg_sleep(3))", "desc nulls first", "sleep(3)", "random" })
    void rejectsSortOrderOtherThanAscOrDesc(String sortOrder) {
        SearchParameters params = SearchParameters.builder().orderBy("transactionDate").sortOrder(sortOrder).build();

        assertThrows(PlatformApiDataValidationException.class, () -> service.retriveAll(1L, null, params));
        verify(paginationHelper, never()).fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any(RowMapper.class));
    }

    @Test
    void mapsAllowedOrderByToFixedColumn() {
        SearchParameters params = SearchParameters.builder().orderBy("transactionDate").sortOrder("desc").limit(10).build();

        service.retriveAll(1L, 2L, params);

        String sql = captureSql();
        assertTrue(sql.contains(" order by tr.transaction_date DESC limit 10"), sql);
    }

    @Test
    void acceptsLegacyQualifiedColumnName() {
        service.retriveAll(1L, null, SearchParameters.builder().orderBy("tr.id").sortOrder("ASC").build());

        assertTrue(captureSql().endsWith(" order by tr.id ASC"));
    }

    @Test
    void omitsOrderByWhenNotRequested() {
        service.retriveAll(1L, null, SearchParameters.builder().build());

        assertFalse(captureSql().contains("order by"));
    }

    @SuppressWarnings("unchecked")
    private String captureSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(any(JdbcTemplate.class), sql.capture(), any(Object[].class), any(RowMapper.class));
        return sql.getValue();
    }
}
