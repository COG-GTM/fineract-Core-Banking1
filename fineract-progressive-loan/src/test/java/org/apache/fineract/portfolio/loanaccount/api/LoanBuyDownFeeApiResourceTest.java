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
import static org.mockito.Mockito.inOrder;
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
import org.apache.fineract.portfolio.loanaccount.data.BuyDownFeeAmortizationDetails;
import org.apache.fineract.portfolio.loanaccount.data.LoanAmortizationAllocationData;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.service.BuyDownFeeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAmortizationAllocationService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanBuyDownFeeApiResourceTest {

    private static final Long OWN_LOAN_ID = 1L;
    private static final Long OTHER_OFFICE_LOAN_ID = 4711L;
    private static final Long TRANSACTION_ID = 9001L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private BuyDownFeeReadPlatformService buyDownFeeReadPlatformService;
    @Mock
    private LoanAmortizationAllocationService loanAmortizationAllocationService;
    @Mock
    private DefaultToApiJsonSerializer<LoanAmortizationAllocationData> toApiJsonSerializer;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private LoanReadPlatformService loanReadPlatformService;
    @Mock
    private AppUser appUser;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private LoanBuyDownFeeApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(loanReadPlatformService.retrieveOne(OTHER_OFFICE_LOAN_ID)).thenThrow(new LoanNotFoundException(OTHER_OFFICE_LOAN_ID));
        when(loanReadPlatformService.getResolvedLoanTransactionId(TRANSACTION_ID, ExternalId.empty())).thenReturn(TRANSACTION_ID);
    }

    @Test
    void buyDownFeesOfLoanOutsideUserHierarchyAreNotReturned() {
        assertThrows(LoanNotFoundException.class, () -> resource.retrieveLoanBuyDownFeeAmortizationDetails(OTHER_OFFICE_LOAN_ID));

        verify(appUser).validateHasReadPermission("LOAN");
        verifyNoInteractions(buyDownFeeReadPlatformService);
    }

    @Test
    void buyDownFeesOfLoanOutsideUserHierarchyAreNotReturnedByExternalId() {
        final ExternalId externalId = new ExternalId("south-branch-loan");
        when(loanReadPlatformService.getResolvedLoanId(externalId)).thenReturn(OTHER_OFFICE_LOAN_ID);

        assertThrows(LoanNotFoundException.class,
                () -> resource.retrieveLoanBuyDownFeeAmortizationDetailsByExternalId("south-branch-loan"));

        verifyNoInteractions(buyDownFeeReadPlatformService);
    }

    @Test
    void buyDownFeesOfLoanInsideUserHierarchyAreReturned() {
        final List<BuyDownFeeAmortizationDetails> details = List.of(mock(BuyDownFeeAmortizationDetails.class));
        when(buyDownFeeReadPlatformService.retrieveLoanBuyDownFeeAmortizationDetails(OWN_LOAN_ID)).thenReturn(details);

        assertSame(details, resource.retrieveLoanBuyDownFeeAmortizationDetails(OWN_LOAN_ID));

        final InOrder inOrder = inOrder(loanReadPlatformService, buyDownFeeReadPlatformService);
        inOrder.verify(loanReadPlatformService).retrieveOne(OWN_LOAN_ID);
        inOrder.verify(buyDownFeeReadPlatformService).retrieveLoanBuyDownFeeAmortizationDetails(OWN_LOAN_ID);
    }

    @Test
    void buyDownFeeAllocationOfLoanOutsideUserHierarchyIsNotReturned() {
        assertThrows(LoanNotFoundException.class,
                () -> resource.retrieveBuyDownFeesAllocationData(OTHER_OFFICE_LOAN_ID, TRANSACTION_ID, uriInfo));

        verify(appUser).validateHasReadPermission("LOAN");
        verifyNoInteractions(loanAmortizationAllocationService, toApiJsonSerializer);
    }

    @Test
    void buyDownFeeAllocationOfLoanOutsideUserHierarchyIsNotReturnedByExternalIds() {
        final ExternalId loanExternalId = new ExternalId("south-branch-loan");
        when(loanReadPlatformService.getResolvedLoanId(loanExternalId)).thenReturn(OTHER_OFFICE_LOAN_ID);
        when(loanReadPlatformService.getResolvedLoanTransactionId(null, new ExternalId("south-branch-tx"))).thenReturn(TRANSACTION_ID);

        assertThrows(LoanNotFoundException.class,
                () -> resource.getBuyDownFeesAllocationDataByExternalIds("south-branch-loan", "south-branch-tx", uriInfo));

        verifyNoInteractions(loanAmortizationAllocationService, toApiJsonSerializer);
    }

    @Test
    void buyDownFeeAllocationOfLoanInsideUserHierarchyIsReturned() {
        final LoanAmortizationAllocationData data = mock(LoanAmortizationAllocationData.class);
        when(loanAmortizationAllocationService.retrieveLoanAmortizationAllocationsForBuyDownFeeTransaction(TRANSACTION_ID, OWN_LOAN_ID))
                .thenReturn(data);
        when(toApiJsonSerializer.serialize(any(), any(LoanAmortizationAllocationData.class), any())).thenReturn("{}");

        assertSame("{}", resource.retrieveBuyDownFeesAllocationData(OWN_LOAN_ID, TRANSACTION_ID, uriInfo));

        final InOrder inOrder = inOrder(loanReadPlatformService, loanAmortizationAllocationService);
        inOrder.verify(loanReadPlatformService).retrieveOne(OWN_LOAN_ID);
        inOrder.verify(loanAmortizationAllocationService).retrieveLoanAmortizationAllocationsForBuyDownFeeTransaction(TRANSACTION_ID,
                OWN_LOAN_ID);
    }
}
