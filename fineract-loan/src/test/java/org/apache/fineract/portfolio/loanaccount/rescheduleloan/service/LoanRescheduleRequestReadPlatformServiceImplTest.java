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
package org.apache.fineract.portfolio.loanaccount.rescheduleloan.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestData;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.exception.LoanRescheduleRequestNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanRescheduleRequestReadPlatformServiceImplTest {

    private static final String BRANCH_HIERARCHY = ".1.2.";
    private static final String BRANCH_HIERARCHY_SEARCH = BRANCH_HIERARCHY + "%";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private CodeValueReadPlatformService codeValueReadPlatformService;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;
    @Mock
    private Office office;

    private LoanRescheduleRequestReadPlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        when(context.getAuthenticatedUserIfPresent()).thenReturn(appUser);
        when(appUser.getOffice()).thenReturn(office);
        when(office.getHierarchy()).thenReturn(BRANCH_HIERARCHY);
        underTest = new LoanRescheduleRequestReadPlatformServiceImpl(jdbcTemplate, loanRepositoryWrapper, codeValueReadPlatformService,
                context);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readLoanRescheduleRequestIsScopedToUserOfficeHierarchy() {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);

        underTest.readLoanRescheduleRequest(7L);

        verify(jdbcTemplate).queryForObject(sql.capture(), any(RowMapper.class), params.capture());
        assertTrue(sql.getValue().contains("where lr.id = ? and (co.hierarchy like ? or gro.hierarchy like ? or tto.hierarchy like ?)"));
        assertArrayEquals(new Object[] { 7L, BRANCH_HIERARCHY_SEARCH, BRANCH_HIERARCHY_SEARCH, BRANCH_HIERARCHY_SEARCH },
                params.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readLoanRescheduleRequestOutsideHierarchyThrowsNotFound() {
        final EmptyResultDataAccessException emptyResult = new EmptyResultDataAccessException(1);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class))).thenThrow(emptyResult);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        final LoanRescheduleRequestNotFoundException thrown = assertThrows(LoanRescheduleRequestNotFoundException.class,
                () -> underTest.readLoanRescheduleRequest(7L));
        assertSame(emptyResult, thrown.getCause());
    }

    @Test
    @SuppressWarnings("unchecked")
    void readLoanRescheduleRequestWithoutTermVariationsInsideHierarchyReturnsNull() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertNull(underTest.readLoanRescheduleRequest(7L));
    }

    @Test
    void validateLoanRescheduleRequestAccessIsScopedToUserOfficeHierarchy() {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertDoesNotThrow(() -> underTest.validateLoanRescheduleRequestAccess(7L));

        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Integer.class), params.capture());
        assertTrue(sql.getValue().endsWith("where lr.id = ? and (co.hierarchy like ? or gro.hierarchy like ? or tto.hierarchy like ?)"));
        assertFalse(sql.getValue().contains("term_variations"));
        assertArrayEquals(new Object[] { 7L, BRANCH_HIERARCHY_SEARCH, BRANCH_HIERARCHY_SEARCH, BRANCH_HIERARCHY_SEARCH },
                params.getValue());
    }

    @Test
    void validateLoanRescheduleRequestAccessOutsideHierarchyThrowsNotFound() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        assertThrows(LoanRescheduleRequestNotFoundException.class, () -> underTest.validateLoanRescheduleRequestAccess(7L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveAllRescheduleRequestsIsScopedToUserOfficeHierarchy() {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);

        underTest.retrieveAllRescheduleRequests("approved", 9L);

        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), params.capture());
        assertTrue(sql.getValue().endsWith(" where (co.hierarchy like ? or gro.hierarchy like ? or tto.hierarchy like ?)"
                + " AND lrr.status_enum = ? AND loan.id = ?"));
        assertArrayEquals(new Object[] { BRANCH_HIERARCHY_SEARCH, BRANCH_HIERARCHY_SEARCH, BRANCH_HIERARCHY_SEARCH, 200, 9L },
                params.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveAllRescheduleRequestsWithoutFiltersIsStillScoped() {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);

        final List<LoanRescheduleRequestData> result = underTest.retrieveAllRescheduleRequests(null, null);

        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), params.capture());
        assertTrue(result.isEmpty());
        assertTrue(sql.getValue().endsWith(" where (co.hierarchy like ? or gro.hierarchy like ? or tto.hierarchy like ?)"));
        assertArrayEquals(new Object[] { BRANCH_HIERARCHY_SEARCH, BRANCH_HIERARCHY_SEARCH, BRANCH_HIERARCHY_SEARCH }, params.getValue());
    }
}
