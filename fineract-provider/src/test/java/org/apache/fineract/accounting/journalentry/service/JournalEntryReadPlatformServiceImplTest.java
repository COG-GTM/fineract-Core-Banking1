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
package org.apache.fineract.accounting.journalentry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.service.GLAccountReadPlatformService;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JournalEntryReadPlatformServiceImplTest {

    private JdbcTemplate jdbcTemplate;
    private PaginationHelper paginationHelper;
    private JournalEntryReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        paginationHelper = mock(PaginationHelper.class);
        DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        service = new JournalEntryReadPlatformServiceImpl(jdbcTemplate, mock(GLAccountReadPlatformService.class),
                mock(OfficeReadPlatformService.class), mock(FinancialActivityAccountRepositoryWrapper.class), paginationHelper,
                sqlGenerator);
    }

    @Test
    void allowlistedOrderByAndSortOrderAreEmittedAsFixedSql() {
        String sql = executeRetrieveAll(SearchParameters.builder().orderBy("transactionId").sortOrder("desc").build());

        assertThat(sql).endsWith(" order by journalEntry.transaction_id DESC");
    }

    @Test
    void qualifiedColumnUsedInternallyIsStillAccepted() {
        String sql = executeRetrieveAll(SearchParameters.builder().orderBy("journalEntry.id").sortOrder("ASC").build());

        assertThat(sql).endsWith(" order by journalEntry.id ASC");
    }

    @Test
    void defaultOrderIsUsedWithoutOrderBy() {
        String sql = executeRetrieveAll(SearchParameters.builder().build());

        assertThat(sql).endsWith(" order by journalEntry.entry_date, journalEntry.id");
    }

    @ParameterizedTest
    @ValueSource(strings = { "(case when exists(select 1 from m_appuser u) then journalEntry.id else journalEntry.entry_date end)",
            "journalEntry.id, (select password from m_appuser limit 1)", "if(1,journalEntry.id,journalEntry.amount)", "m_appuser.password",
            "id desc", "unknownColumn" })
    void nonAllowlistedOrderByIsRejectedBeforeQueryExecution(String orderBy) {
        SearchParameters searchParameters = SearchParameters.builder().orderBy(orderBy).build();

        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveAll(searchParameters, null, null, null, null, null, null, null, null, null));

        assertThat(exception.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getParameterName()).isEqualTo("orderBy"));
        verifyNoInteractions(paginationHelper, jdbcTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select 1)", "asc, (select 1)", "DESC NULLS FIRST", "ascending", "asc;" })
    void nonAllowlistedSortOrderIsRejectedBeforeQueryExecution(String sortOrder) {
        SearchParameters searchParameters = SearchParameters.builder().orderBy("id").sortOrder(sortOrder).build();

        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveAll(searchParameters, null, null, null, null, null, null, null, null, null));

        assertThat(exception.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getParameterName()).isEqualTo("sortOrder"));
        verifyNoInteractions(paginationHelper, jdbcTemplate);
    }

    @SuppressWarnings("unchecked")
    private String executeRetrieveAll(SearchParameters searchParameters) {
        service.retrieveAll(searchParameters, null, null, null, null, null, null, null, null, null);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(eq(jdbcTemplate), sqlCaptor.capture(), any(Object[].class), any(RowMapper.class));
        return sqlCaptor.getValue().trim();
    }
}
