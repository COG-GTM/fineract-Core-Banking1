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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.portfolio.account.data.StandingInstructionDTO;
import org.apache.fineract.portfolio.account.mapper.AccountTransfersMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class StandingInstructionOrderByValidationTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
    private final PaginationHelper paginationHelper = mock(PaginationHelper.class);

    private StandingInstructionReadPlatformServiceImpl standingInstructionService;
    private StandingInstructionHistoryReadPlatformServiceImpl historyService;
    private AccountTransfersReadPlatformServiceImpl accountTransfersService;

    @BeforeEach
    void setUp() {
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        when(sqlGenerator.limit(anyInt())).thenReturn("limit 200");
        when(sqlGenerator.limit(anyInt(), anyInt())).thenReturn("limit 200 offset 0");
        standingInstructionService = new StandingInstructionReadPlatformServiceImpl(jdbcTemplate, null, null, null, null, sqlGenerator,
                paginationHelper);
        historyService = new StandingInstructionHistoryReadPlatformServiceImpl(jdbcTemplate, sqlGenerator, paginationHelper);
        accountTransfersService = new AccountTransfersReadPlatformServiceImpl(jdbcTemplate, null, null, null, sqlGenerator,
                new AccountTransfersMapper(), paginationHelper, mock(SqlValidator.class));
    }

    private static SearchParameters params(String orderBy, String sortOrder) {
        return SearchParameters.builder().orderBy(orderBy).sortOrder(sortOrder).build();
    }

    private static StandingInstructionDTO dto(SearchParameters searchParameters) {
        return new StandingInstructionDTO(searchParameters, null, null, null, null, null, null, null);
    }

    private String executedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(any(JdbcTemplate.class), sql.capture(), any(Object[].class), any(RowMapper.class));
        return sql.getValue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select 1 from pg_sleep(5))", "(select sleep(5))", "name,(select 1)", "unknownColumn" })
    void standingInstructionListRejectsOrderByOutsideAllowlist(String orderBy) {
        assertThrows(PlatformApiDataValidationException.class, () -> standingInstructionService.retrieveAll(dto(params(orderBy, null))));
        verifyNoInteractions(paginationHelper);
    }

    @Test
    void standingInstructionListRejectsInvalidSortOrder() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> standingInstructionService.retrieveAll(dto(params("name", "DESC,(select 1 from pg_sleep(5))"))));
        verifyNoInteractions(paginationHelper);
    }

    @Test
    void standingInstructionListOrdersByAllowlistedColumn() {
        standingInstructionService.retrieveAll(dto(params("name", "desc")));
        assertTrue(executedSql().endsWith(" order by atsi.name DESC"), executedSql());
    }

    @Test
    void standingInstructionTransfersRejectOrderByOutsideAllowlist() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> accountTransfersService.retrieveByStandingInstruction(1L, params("(select 1 from pg_sleep(5))", null)));
        verifyNoInteractions(paginationHelper);
    }

    @Test
    void standingInstructionTransfersOrderByAllowlistedColumn() {
        accountTransfersService.retrieveByStandingInstruction(1L, params("transferDate", "ASC"));
        assertTrue(executedSql().endsWith(" where atsi.id = ? order by att.transaction_date ASC"), executedSql());
    }

    @Test
    void accountTransfersListRejectsOrderByOutsideAllowlist() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> accountTransfersService.retrieveAll(params("(select 1 from pg_sleep(5))", null), null));
        verifyNoInteractions(paginationHelper);
    }

    @Test
    void standingInstructionHistoryRejectsOrderByOutsideAllowlist() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> historyService.retrieveAll(dto(params("(select 1 from pg_sleep(5))", null))));
        verifyNoInteractions(paginationHelper);
    }

    @Test
    void standingInstructionHistoryOrdersByAllowlistedColumn() {
        historyService.retrieveAll(dto(params("executionTime", null)));
        String sql = executedSql();
        assertEquals(1, sql.split(" order by ").length - 1, sql);
        assertTrue(sql.endsWith(" order by atsih.execution_time"), sql);
    }

    @Test
    void standingInstructionListOrdersByAccountDetailId() {
        standingInstructionService.retrieveAll(dto(params("accountDetailId", "ASC")));
        assertTrue(executedSql().endsWith(" order by atd.id ASC"), executedSql());
    }

    @Test
    void standingInstructionHistoryOrdersByErrorLog() {
        historyService.retrieveAll(dto(params("errorLog", "DESC")));
        assertTrue(executedSql().endsWith(" order by atsih.error_log DESC"), executedSql());
    }

    @Test
    void accountTransfersListOrdersByCurrencyName() {
        accountTransfersService.retrieveAll(params("currencyName", null), null);
        assertTrue(executedSql().endsWith(" order by curr.name"), executedSql());
    }
}
