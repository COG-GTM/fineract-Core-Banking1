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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.apache.fineract.infrastructure.core.data.PaginationParameters;
import org.apache.fineract.infrastructure.core.data.PaginationParametersDataValidator;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.exception.DepositAccountNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class DepositAccountReadPlatformServiceImplTest {

    private static final String HIERARCHY = ".1.2.";
    private static final String HIERARCHY_SEARCH = HIERARCHY + "%";
    private static final String SCOPE_CONDITION = "(scope_client_office.hierarchy like ? or scope_group_office.hierarchy like ?)";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PaginationParametersDataValidator paginationParametersDataValidator;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PaginationHelper paginationHelper;

    @InjectMocks
    private DepositAccountReadPlatformServiceImpl service;

    @Test
    void retrieveAllIsScopedToUserOfficeHierarchy() {
        when(context.officeHierarchy()).thenReturn(HIERARCHY);
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        when(jdbcTemplate.query(sql.capture(), any(RowMapper.class), args.capture())).thenReturn(Collections.emptyList());

        service.retrieveAll(DepositAccountType.RECURRING_DEPOSIT, PaginationParameters.builder().build());

        assertScoped(sql.getValue());
        assertArrayEquals(new Object[] { DepositAccountType.RECURRING_DEPOSIT.getValue(), HIERARCHY_SEARCH, HIERARCHY_SEARCH },
                args.getValue());
    }

    @Test
    void retrieveAllPagedIsScopedToUserOfficeHierarchy() {
        when(context.officeHierarchy()).thenReturn(HIERARCHY);
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);

        service.retrieveAllPaged(DepositAccountType.RECURRING_DEPOSIT, PaginationParameters.builder().paged(true).limit(10).build());

        verify(paginationHelper).fetchPage(eq(jdbcTemplate), sql.capture(), args.capture(), any(RowMapper.class));
        assertScoped(sql.getValue());
        assertArrayEquals(new Object[] { DepositAccountType.RECURRING_DEPOSIT.getValue(), HIERARCHY_SEARCH, HIERARCHY_SEARCH },
                args.getValue());
    }

    @Test
    void retrieveOneWithChartSlabsOutsideHierarchyIsNotFound() {
        when(context.officeHierarchy()).thenReturn(HIERARCHY);
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.queryForObject(sql.capture(), any(RowMapper.class), eq(5L), eq(DepositAccountType.RECURRING_DEPOSIT.getValue()),
                eq(HIERARCHY_SEARCH), eq(HIERARCHY_SEARCH))).thenThrow(new EmptyResultDataAccessException(1));

        assertThrows(DepositAccountNotFoundException.class,
                () -> service.retrieveOneWithChartSlabs(DepositAccountType.RECURRING_DEPOSIT, 5L));
        assertScoped(sql.getValue());
    }

    @Test
    void retrieveOneWithClosureTemplateOutsideHierarchyIsNotFound() {
        when(context.officeHierarchy()).thenReturn(HIERARCHY);
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.queryForObject(sql.capture(), any(RowMapper.class), eq(5L), eq(DepositAccountType.RECURRING_DEPOSIT.getValue()),
                eq(HIERARCHY_SEARCH), eq(HIERARCHY_SEARCH))).thenThrow(new EmptyResultDataAccessException(1));

        assertThrows(DepositAccountNotFoundException.class,
                () -> service.retrieveOneWithClosureTemplate(DepositAccountType.RECURRING_DEPOSIT, 5L));
        assertScoped(sql.getValue());
    }

    @Test
    void retrieveAllTransactionsIsScopedToUserOfficeHierarchy() {
        when(context.officeHierarchy()).thenReturn(HIERARCHY);
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        when(jdbcTemplate.query(sql.capture(), any(RowMapper.class), args.capture())).thenReturn(Collections.emptyList());

        service.retrieveAllTransactions(DepositAccountType.RECURRING_DEPOSIT, 5L);

        assertScoped(sql.getValue());
        assertArrayEquals(new Object[] { 5L, DepositAccountType.RECURRING_DEPOSIT.getValue(), HIERARCHY_SEARCH, HIERARCHY_SEARCH },
                args.getValue());
    }

    @Test
    void internalRetrieveOneIsNotScoped() {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.queryForObject(sql.capture(), any(RowMapper.class), eq(5L), eq(DepositAccountType.RECURRING_DEPOSIT.getValue())))
                .thenReturn(null);

        service.retrieveOne(DepositAccountType.RECURRING_DEPOSIT, 5L);

        assertFalse(sql.getValue().contains("hierarchy like"));
    }

    private static void assertScoped(final String sql) {
        assertTrue(sql.contains("left join m_office scope_client_office on scope_client_office.id = scope_client.office_id"), sql);
        assertTrue(sql.contains("left join m_office scope_group_office on scope_group_office.id = scope_group.office_id"), sql);
        assertTrue(sql.contains(SCOPE_CONDITION), sql);
        assertTrue(sql.indexOf(SCOPE_CONDITION) > sql.indexOf(" where "), sql);
    }
}
