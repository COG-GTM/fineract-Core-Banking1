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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LoanTransactionsApiResourceTest {

    private static final Long LOAN_ID = 42L;
    private static final Long TRANSACTION_ID = 7L;
    private static final String LOAN_EXTERNAL_ID = "foreign-branch-loan";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private LoanReadPlatformService loanReadPlatformService;
    @Mock
    private AppUser appUser;

    @InjectMocks
    private LoanTransactionsApiResource resource;

    private final Pageable pageable = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
    }

    @Test
    void retrieveTransactionsRejectsLoanOutsideUserOfficeHierarchy() {
        when(loanReadPlatformService.existsByLoanIdWithinUserOfficeHierarchy(LOAN_ID)).thenReturn(false);

        assertThrows(LoanNotFoundException.class,
                () -> resource.retrieveTransactionsByLoanId(LOAN_ID, null, null, null, null, pageable, null));

        verify(loanReadPlatformService, never()).retrieveLoanTransactions(anyLong(), any(), any());
    }

    @Test
    void retrieveTransactionsByExternalIdRejectsLoanOutsideUserOfficeHierarchy() {
        when(loanReadPlatformService.retrieveLoanIdByExternalId(new ExternalId(LOAN_EXTERNAL_ID))).thenReturn(LOAN_ID);
        when(loanReadPlatformService.existsByLoanIdWithinUserOfficeHierarchy(LOAN_ID)).thenReturn(false);

        assertThrows(LoanNotFoundException.class,
                () -> resource.retrieveTransactionsByExternalLoanId(LOAN_EXTERNAL_ID, null, null, null, null, pageable, null));

        verify(loanReadPlatformService, never()).retrieveLoanTransactions(anyLong(), any(), any());
    }

    @Test
    void retrieveTransactionsReturnsPageForLoanInsideUserOfficeHierarchy() {
        final Page<LoanTransactionData> expected = Page.empty();
        when(loanReadPlatformService.existsByLoanIdWithinUserOfficeHierarchy(LOAN_ID)).thenReturn(true);
        when(loanReadPlatformService.retrieveLoanTransactions(eq(LOAN_ID), any(), eq(pageable))).thenReturn(expected);

        assertSame(expected, resource.retrieveTransactionsByLoanId(LOAN_ID, null, null, null, null, pageable, null));
    }

    @Test
    void retrieveTransactionRejectsLoanOutsideUserOfficeHierarchy() {
        when(loanReadPlatformService.existsByLoanIdWithinUserOfficeHierarchy(LOAN_ID)).thenReturn(false);

        assertThrows(LoanNotFoundException.class, () -> resource.retrieveTransaction(LOAN_ID, TRANSACTION_ID, null, null));

        verify(loanReadPlatformService, never()).retrieveLoanTransaction(anyLong(), anyLong());
    }

    @Test
    void retrieveTransactionByLoanExternalIdRejectsLoanOutsideUserOfficeHierarchy() {
        when(loanReadPlatformService.retrieveLoanIdByExternalId(new ExternalId(LOAN_EXTERNAL_ID))).thenReturn(LOAN_ID);
        when(loanReadPlatformService.existsByLoanIdWithinUserOfficeHierarchy(LOAN_ID)).thenReturn(false);

        assertThrows(LoanNotFoundException.class,
                () -> resource.retrieveTransactionByLoanExternalIdAndTransactionId(LOAN_EXTERNAL_ID, TRANSACTION_ID, null, null));

        verify(loanReadPlatformService, never()).retrieveLoanTransaction(anyLong(), anyLong());
    }
}
