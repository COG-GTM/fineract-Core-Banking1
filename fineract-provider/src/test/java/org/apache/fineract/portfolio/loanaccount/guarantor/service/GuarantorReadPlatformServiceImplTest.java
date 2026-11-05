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
package org.apache.fineract.portfolio.loanaccount.guarantor.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.guarantor.exception.GuarantorNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuarantorReadPlatformServiceImplTest {

    private static final Long LOAN_ID = 4711L;
    private static final Long GUARANTOR_ID = 2L;
    private static final String HIERARCHY_SEARCH = ".2.%";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ClientReadPlatformService clientReadPlatformService;
    @Mock
    private StaffReadPlatformService staffReadPlatformService;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;
    @Mock
    private Office office;

    @InjectMocks
    private GuarantorReadPlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(appUser.getOffice()).thenReturn(office);
        when(office.getHierarchy()).thenReturn(".2.");
    }

    @Test
    void retrieveGuarantorsForValidLoanRejectsLoanOutsideUserOfficeHierarchy() {
        whenLoanInHierarchyCount(0L);

        assertThrows(LoanNotFoundException.class, () -> underTest.retrieveGuarantorsForValidLoan(LOAN_ID));

        verify(jdbcTemplate, never()).query(any(PreparedStatementCreator.class), any(RowMapper.class));
    }

    @Test
    void retrieveGuarantorRejectsLoanOutsideUserOfficeHierarchy() {
        whenLoanInHierarchyCount(0L);

        assertThrows(LoanNotFoundException.class, () -> underTest.retrieveGuarantor(LOAN_ID, GUARANTOR_ID));

        verify(jdbcTemplate, never()).query(any(PreparedStatementCreator.class), any(RowMapper.class));
    }

    @Test
    void retrieveGuarantorsForValidLoanReturnsGuarantorsForLoanInsideUserOfficeHierarchy() {
        whenLoanInHierarchyCount(1L);
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenReturn(List.of());

        underTest.retrieveGuarantorsForValidLoan(LOAN_ID);

        verify(jdbcTemplate).query(any(PreparedStatementCreator.class), any(RowMapper.class));
    }

    @Test
    void retrieveGuarantorThrowsNotFoundWhenGuarantorDoesNotBelongToLoan() {
        whenLoanInHierarchyCount(1L);
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenReturn(List.of());

        assertThrows(GuarantorNotFoundException.class, () -> underTest.retrieveGuarantor(LOAN_ID, GUARANTOR_ID));
    }

    private void whenLoanInHierarchyCount(final Long count) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(LOAN_ID), eq(HIERARCHY_SEARCH), eq(HIERARCHY_SEARCH)))
                .thenReturn(count);
    }
}
