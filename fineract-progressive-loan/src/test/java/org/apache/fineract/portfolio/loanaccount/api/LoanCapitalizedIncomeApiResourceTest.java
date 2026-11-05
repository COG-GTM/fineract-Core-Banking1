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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.CapitalizedIncomeDetails;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.data.LoanAmortizationAllocationData;
import org.apache.fineract.portfolio.loanaccount.data.LoanCapitalizedIncomeData;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.service.CapitalizedIncomeBalanceReadService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAmortizationAllocationService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanCapitalizedIncomeApiResourceTest {

    private static final Long OWN_LOAN_ID = 100L;
    private static final Long OTHER_OFFICE_LOAN_ID = 200L;
    private static final Long LOAN_TRANSACTION_ID = 9200L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;
    @Mock
    private CapitalizedIncomeBalanceReadService capitalizedIncomeBalanceReadService;
    @Mock
    private LoanAmortizationAllocationService loanAmortizationAllocationService;
    @Mock
    private DefaultToApiJsonSerializer<LoanAmortizationAllocationData> toApiJsonSerializer;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private LoanReadPlatformService loanReadPlatformService;
    @Mock
    private UriInfo uriInfo;

    private LoanCapitalizedIncomeApiResource underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(loanReadPlatformService.retrieveOne(OWN_LOAN_ID)).thenReturn(mock(LoanAccountData.class));
        when(loanReadPlatformService.retrieveOne(OTHER_OFFICE_LOAN_ID)).thenThrow(new LoanNotFoundException(OTHER_OFFICE_LOAN_ID));
        when(loanReadPlatformService.getResolvedLoanId(any(ExternalId.class))).thenReturn(OTHER_OFFICE_LOAN_ID);
        when(loanReadPlatformService.getResolvedLoanTransactionId(any(), any())).thenReturn(LOAN_TRANSACTION_ID);
        underTest = new LoanCapitalizedIncomeApiResource(context, capitalizedIncomeBalanceReadService, loanAmortizationAllocationService,
                toApiJsonSerializer, apiRequestParameterHelper, loanReadPlatformService);
    }

    @Test
    void capitalizedIncomeDetailsOfLoanOutsideOfficeHierarchyAreNotReturned() {
        assertThrows(LoanNotFoundException.class, () -> underTest.fetchCapitalizedIncomeDetails(OTHER_OFFICE_LOAN_ID));
        assertThrows(LoanNotFoundException.class, () -> underTest.fetchCapitalizedIncomeDetailsByExternalId("other-office-loan"));
        assertThrows(LoanNotFoundException.class, () -> underTest.fetchLoanCapitalizedIncomeData(OTHER_OFFICE_LOAN_ID));
        assertThrows(LoanNotFoundException.class, () -> underTest.fetchLoanCapitalizedIncomeDataByExternalId("other-office-loan"));

        verifyNoInteractions(capitalizedIncomeBalanceReadService);
    }

    @Test
    void capitalizedIncomeAllocationOfLoanOutsideOfficeHierarchyIsNotReturned() {
        assertThrows(LoanNotFoundException.class,
                () -> underTest.retrieveCapitalizedIncomeAllocationData(OTHER_OFFICE_LOAN_ID, LOAN_TRANSACTION_ID, uriInfo));
        assertThrows(LoanNotFoundException.class,
                () -> underTest.getCapitalizedIncomeAllocationDataByLoanExternalId("other-office-loan", LOAN_TRANSACTION_ID, uriInfo));
        assertThrows(LoanNotFoundException.class,
                () -> underTest.getCapitalizedIncomeAllocationDataByTransactionExternalId(OTHER_OFFICE_LOAN_ID, "txn", uriInfo));
        assertThrows(LoanNotFoundException.class,
                () -> underTest.getCapitalizedIncomeAllocationDataByExternalIds("other-office-loan", "txn", uriInfo));

        verifyNoInteractions(loanAmortizationAllocationService);
    }

    @Test
    void capitalizedIncomeOfLoanWithinOfficeHierarchyIsReturned() {
        final List<CapitalizedIncomeDetails> details = List.of(mock(CapitalizedIncomeDetails.class));
        final LoanCapitalizedIncomeData data = mock(LoanCapitalizedIncomeData.class);
        when(capitalizedIncomeBalanceReadService.fetchLoanCapitalizedIncomeDetails(OWN_LOAN_ID)).thenReturn(details);
        when(capitalizedIncomeBalanceReadService.fetchLoanCapitalizedIncomeData(OWN_LOAN_ID)).thenReturn(data);
        when(loanAmortizationAllocationService.retrieveLoanAmortizationAllocationsForCapitalizedIncomeTransaction(anyLong(), anyLong()))
                .thenReturn(mock(LoanAmortizationAllocationData.class));
        when(toApiJsonSerializer.serialize(any(), any(), any())).thenReturn("{}");

        assertSame(details, underTest.fetchCapitalizedIncomeDetails(OWN_LOAN_ID));
        assertSame(data, underTest.fetchLoanCapitalizedIncomeData(OWN_LOAN_ID));
        assertSame("{}", underTest.retrieveCapitalizedIncomeAllocationData(OWN_LOAN_ID, LOAN_TRANSACTION_ID, uriInfo));

        verify(loanAmortizationAllocationService).retrieveLoanAmortizationAllocationsForCapitalizedIncomeTransaction(LOAN_TRANSACTION_ID,
                OWN_LOAN_ID);
    }
}
