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
package org.apache.fineract.portfolio.loanaccount.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.data.LoanApprovedAmountHistoryData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanApprovedAmountHistoryRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LoansApiResourceApprovedAmountHistoryTest {

    private static final Long LOAN_ID = 42L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private LoanReadPlatformService loanReadPlatformService;
    @Mock
    private LoanApprovedAmountHistoryRepository loanApprovedAmountHistoryRepository;
    @Mock
    private AppUser appUser;

    @InjectMocks
    private LoansApiResource loansApiResource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
    }

    @Test
    void approvedAmountHistoryIsNotReturnedForLoanOutsideOfficeHierarchy() {
        when(loanReadPlatformService.retrieveOne(LOAN_ID)).thenThrow(new LoanNotFoundException(LOAN_ID));

        assertThrows(LoanNotFoundException.class, () -> loansApiResource.getLoanApprovedAmountHistory(LOAN_ID, null));

        verify(loanApprovedAmountHistoryRepository, never()).findAllByLoanId(anyLong(), any(Pageable.class));
    }

    @Test
    void approvedAmountHistoryByExternalIdIsNotReturnedForLoanOutsideOfficeHierarchy() {
        String externalId = "ext-loan-42";
        when(loanReadPlatformService.getResolvedLoanId(new ExternalId(externalId))).thenReturn(LOAN_ID);
        when(loanReadPlatformService.retrieveOne(LOAN_ID)).thenThrow(new LoanNotFoundException(LOAN_ID));

        assertThrows(LoanNotFoundException.class, () -> loansApiResource.getLoanApprovedAmountHistory(externalId, null));

        verify(loanApprovedAmountHistoryRepository, never()).findAllByLoanId(anyLong(), any(Pageable.class));
    }

    @Test
    void approvedAmountHistoryIsReturnedForLoanInsideOfficeHierarchy() {
        List<LoanApprovedAmountHistoryData> history = List.of(mock(LoanApprovedAmountHistoryData.class));
        when(loanReadPlatformService.retrieveOne(LOAN_ID)).thenReturn(mock(LoanAccountData.class));
        when(loanApprovedAmountHistoryRepository.findAllByLoanId(any(), any(Pageable.class))).thenReturn(history);

        assertSame(history, loansApiResource.getLoanApprovedAmountHistory(LOAN_ID, null));

        InOrder inOrder = inOrder(appUser, loanReadPlatformService, loanApprovedAmountHistoryRepository);
        inOrder.verify(appUser).validateHasReadPermission("LOAN");
        inOrder.verify(loanReadPlatformService).retrieveOne(LOAN_ID);
        inOrder.verify(loanApprovedAmountHistoryRepository).findAllByLoanId(any(), any(Pageable.class));
    }
}
