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
package org.apache.fineract.portfolio.collateral.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.collateral.data.CollateralData;
import org.apache.fineract.portfolio.collateral.exception.CollateralNotFoundException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class CollateralReadPlatformServiceImplTest {

    private static final Long LOAN_ID = 4521L;
    private static final Long COLLATERAL_ID = 1L;
    private static final String USER_HIERARCHY_SEARCH = ".3.%";
    private static final String SCOPED_COLLATERAL_SQL = "and exists (select 1 from m_loan l";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private AppUser appUser;
    @Mock
    private Office office;

    @InjectMocks
    private CollateralReadPlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(appUser.getOffice()).thenReturn(office);
        when(office.getHierarchy()).thenReturn(".3.");
    }

    private void loanInUserHierarchy(boolean inHierarchy) {
        when(jdbcTemplate.queryForObject(contains("hierarchy like ?"), eq(Integer.class), eq(LOAN_ID), eq(USER_HIERARCHY_SEARCH),
                eq(USER_HIERARCHY_SEARCH))).thenReturn(inHierarchy ? 1 : 0);
    }

    @Test
    void retrieveCollateralsForValidLoanThrowsNotFoundWhenLoanIsOutsideUserOfficeHierarchy() {
        loanInUserHierarchy(false);

        assertThrows(LoanNotFoundException.class, () -> underTest.retrieveCollateralsForValidLoan(LOAN_ID));

        verify(jdbcTemplate).queryForObject(contains("hierarchy like ?"), eq(Integer.class), eq(LOAN_ID), eq(USER_HIERARCHY_SEARCH),
                eq(USER_HIERARCHY_SEARCH));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void retrieveCollateralThrowsNotFoundWhenLoanIsOutsideUserOfficeHierarchy() {
        when(jdbcTemplate.queryForObject(contains(SCOPED_COLLATERAL_SQL), ArgumentMatchers.<RowMapper<CollateralData>>any(), eq(LOAN_ID),
                eq(COLLATERAL_ID), eq(USER_HIERARCHY_SEARCH), eq(USER_HIERARCHY_SEARCH)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThrows(CollateralNotFoundException.class, () -> underTest.retrieveCollateral(LOAN_ID, COLLATERAL_ID));
    }

    @Test
    void retrieveCollateralsScopesQueryToUserOfficeHierarchy() {
        when(jdbcTemplate.query(contains(SCOPED_COLLATERAL_SQL), ArgumentMatchers.<RowMapper<CollateralData>>any(), eq(LOAN_ID),
                eq(USER_HIERARCHY_SEARCH), eq(USER_HIERARCHY_SEARCH))).thenReturn(List.of());

        assertTrue(underTest.retrieveCollaterals(LOAN_ID).isEmpty());
    }

    @Test
    void retrieveCollateralsForValidLoanReturnsCollateralsWhenLoanIsInUserOfficeHierarchy() {
        loanInUserHierarchy(true);
        final List<CollateralData> collaterals = List.of(CollateralData.instance(COLLATERAL_ID, null, null, "car", null));
        when(jdbcTemplate.query(contains(SCOPED_COLLATERAL_SQL), ArgumentMatchers.<RowMapper<CollateralData>>any(), eq(LOAN_ID),
                eq(USER_HIERARCHY_SEARCH), eq(USER_HIERARCHY_SEARCH))).thenReturn(collaterals);

        assertEquals(collaterals, underTest.retrieveCollateralsForValidLoan(LOAN_ID));

        verify(loanRepositoryWrapper).findOneWithNotFoundDetection(LOAN_ID);
    }
}
