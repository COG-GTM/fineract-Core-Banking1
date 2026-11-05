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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.portfolio.account.mapper.AccountTransfersMapper;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountTransfersReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ClientReadPlatformService clientReadPlatformService;
    @Mock
    private OfficeReadPlatformService officeReadPlatformService;
    @Mock
    private PortfolioAccountReadPlatformService portfolioAccountReadPlatformService;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private SqlValidator sqlValidator;

    private AccountTransfersReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        when(sqlGenerator.limit(any(Integer.class))).thenAnswer(inv -> "limit " + inv.getArgument(0));
        service = new AccountTransfersReadPlatformServiceImpl(jdbcTemplate, clientReadPlatformService, officeReadPlatformService,
                portfolioAccountReadPlatformService, sqlGenerator, new AccountTransfersMapper(), paginationHelper, sqlValidator);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select 1 from pg_sleep(5))", "(select sleep(5))", "att.id, (select 1)", "id desc, 1", "1",
            "case when 1 then att.id end", "att.unknown_column" })
    void retrieveAllRejectsOrderByOutsideAllowlist(final String orderBy) {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy(orderBy).build();

        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveAll(searchParameters, null));

        assertEquals("orderBy", ex.getErrors().get(0).getParameterName());
        verify(paginationHelper, never()).fetchPage(any(), anyString(), any(Object[].class), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "desc, (select 1 from pg_sleep(5))", "asc;", "(select sleep(5))", "descending" })
    void retrieveAllRejectsSortOrderOtherThanAscOrDesc(final String sortOrder) {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy("id").sortOrder(sortOrder).build();

        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveAll(searchParameters, null));

        assertEquals("sortOrder", ex.getErrors().get(0).getParameterName());
        verify(paginationHelper, never()).fetchPage(any(), anyString(), any(Object[].class), any());
    }

    @Test
    void retrieveAllOrdersByAllowlistedAliasAndDirection() {
        service.retrieveAll(SearchParameters.builder().orderBy("id").sortOrder("DESC").limit(10).build(), null);
        assertTrue(capturedSql().endsWith(" order by att.id DESC limit 10"), capturedSql());
    }

    @Test
    void retrieveAllAcceptsCaseInsensitiveAliasOrQualifiedColumn() {
        service.retrieveAll(SearchParameters.builder().orderBy("transferDate").sortOrder("asc").build(), 5L);
        assertTrue(capturedSql().endsWith(" where att.account_transfer_details_id=? order by att.transaction_date ASC"), capturedSql());

        service.retrieveAll(SearchParameters.builder().orderBy("ATT.AMOUNT").build(), null);
        assertTrue(lastCapturedSql().endsWith(" order by att.amount"), lastCapturedSql());
    }

    @Test
    void retrieveByStandingInstructionRejectsInjectedOrderBy() {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy("(select 1 from pg_sleep(5))").build();

        assertThrows(PlatformApiDataValidationException.class, () -> service.retrieveByStandingInstruction(1L, searchParameters));
        verify(paginationHelper, never()).fetchPage(any(), anyString(), any(Object[].class), any());
    }

    @Test
    void retrieveByStandingInstructionRejectsInjectedSortOrder() {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy("id").sortOrder("desc, (select sleep(5))").build();

        assertThrows(PlatformApiDataValidationException.class, () -> service.retrieveByStandingInstruction(1L, searchParameters));
        verify(paginationHelper, never()).fetchPage(any(), anyString(), any(Object[].class), any());
    }

    @Test
    void retrieveByStandingInstructionOrdersByAllowlistedColumn() {
        service.retrieveByStandingInstruction(1L, SearchParameters.builder().orderBy("transferAmount").sortOrder("desc").limit(5).build());
        assertTrue(capturedSql().endsWith(" where atsi.id = ? order by att.amount DESC limit 5"), capturedSql());
    }

    private String capturedSql() {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(any(), sql.capture(), any(Object[].class), any());
        return sql.getValue();
    }

    private String lastCapturedSql() {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper, org.mockito.Mockito.atLeastOnce()).fetchPage(any(), sql.capture(), any(Object[].class), any());
        return sql.getValue();
    }
}
