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
package org.apache.fineract.cob.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.cob.loan.RetrieveLoanIdService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalCOBApiResourceTest {

    private static final String ALL_FUNCTIONS = "ALL_FUNCTIONS";
    private static final long LOAN_ID = 1L;
    private static final String FAST_FORWARD_BODY = "{\"lastClosedBusinessDate\": \"01 January 2099\"}";

    @Mock
    private RetrieveLoanIdService retrieveLoanIdService;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private ToApiJsonSerializer<List> toApiJsonSerializerForList;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private LoanScheduleService loanScheduleService;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;
    @Mock
    private Loan loan;

    @InjectMocks
    private InternalCOBApiResource underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
    }

    private void denyAllFunctions() {
        doThrow(NoAuthorizationException.class).when(appUser).validateHasPermissionTo(ALL_FUNCTIONS);
    }

    @Test
    void fastForwardCobDateRejectsUserWithoutAllFunctions() {
        denyAllFunctions();

        assertThrows(NoAuthorizationException.class, () -> underTest.updateLoanCobLastDate(null, LOAN_ID, FAST_FORWARD_BODY));

        verifyNoInteractions(loanRepositoryWrapper);
    }

    @Test
    void loanReprocessRejectsUserWithoutAllFunctions() {
        denyAllFunctions();

        assertThrows(NoAuthorizationException.class, () -> underTest.loanReprocess(null, LOAN_ID));

        verifyNoInteractions(loanRepositoryWrapper, loanScheduleService);
    }

    @Test
    void cobPartitionsRejectsUserWithoutAllFunctions() {
        denyAllFunctions();

        assertThrows(NoAuthorizationException.class, () -> underTest.getCobPartitions(null, 10));

        verifyNoInteractions(retrieveLoanIdService);
    }

    @Test
    void fastForwardCobDateAllowedForAllFunctionsUser() {
        when(loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID)).thenReturn(loan);

        underTest.updateLoanCobLastDate(null, LOAN_ID, FAST_FORWARD_BODY);

        verify(appUser).validateHasPermissionTo(ALL_FUNCTIONS);
        verify(loan).setLastClosedBusinessDate(LocalDate.of(2099, 1, 1));
        verify(loanRepositoryWrapper).save(loan);
    }

    @Test
    void loanReprocessAllowedForAllFunctionsUser() {
        when(loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID)).thenReturn(loan);

        underTest.loanReprocess(null, LOAN_ID);

        verify(appUser).validateHasPermissionTo(ALL_FUNCTIONS);
        verify(loanScheduleService).regenerateScheduleWithReprocessingTransactions(loan);
    }
}
