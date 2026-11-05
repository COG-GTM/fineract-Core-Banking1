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
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.useradministration.domain.AppUser;
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
class SavingsAccountReadPlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SavingsAccountAssembler savingsAccountAssembler;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;
    @Mock
    private AppUser appUser;
    @Mock
    private Office office;

    private SavingsAccountReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(appUser.getOffice()).thenReturn(office);
        when(office.getHierarchy()).thenReturn(".");
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        service = new SavingsAccountReadPlatformServiceImpl(context, jdbcTemplate, savingsAccountAssembler, paginationHelper, sqlGenerator,
                savingsAccountRepositoryWrapper);
    }

    @Test
    void retrieveAllSortsByAllowlistedColumn() {
        service.retrieveAll(SearchParameters.builder().orderBy("accountNo").sortOrder("desc").build());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(any(JdbcTemplate.class), sql.capture(), any(Object[].class), any(RowMapper.class));
        assertTrue(sql.getValue().endsWith(" where o.hierarchy like ? order by sa.account_no DESC"), sql.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = { "(case when sbu.password like 'a%' then sa.id else -sa.id end)",
            "(select case when u.password like '$2a%' then sa.id else -sa.id end from m_appuser u where u.id < 2)",
            "(select 1 from m_appuser u where u.id < 2)", "sbu.password" })
    void retrieveAllRejectsOrderByOutsideAllowlistWithoutQuerying(String orderBy) {
        assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveAll(SearchParameters.builder().orderBy(orderBy).sortOrder("asc").build()));

        verify(paginationHelper, never()).fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any(RowMapper.class));
    }

    @Test
    void retrieveAllRejectsInvalidSortOrderWithoutQuerying() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveAll(SearchParameters.builder().orderBy("id").sortOrder("asc, (select 1)").build()));

        verify(paginationHelper, never()).fetchPage(any(JdbcTemplate.class), anyString(), any(Object[].class), any(RowMapper.class));
    }
}
