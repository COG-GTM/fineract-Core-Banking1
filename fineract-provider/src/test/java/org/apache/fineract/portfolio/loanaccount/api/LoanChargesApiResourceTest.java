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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeReadPlatformService;
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
class LoanChargesApiResourceTest {

    private static final Long LOAN_ID = 1234L;
    private static final Long LOAN_CHARGE_ID = 5678L;
    private static final String LOAN_EXTERNAL_ID = "northside-loan";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;
    @Mock
    private ChargeReadPlatformService chargeReadPlatformService;
    @Mock
    private LoanChargeReadPlatformService loanChargeReadPlatformService;
    @Mock
    private DefaultToApiJsonSerializer<LoanChargeData> toApiJsonSerializer;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private LoanReadPlatformService loanReadPlatformService;
    @Mock
    private UriInfo uriInfo;

    private LoanChargesApiResource resource;

    @BeforeEach
    void setUp() {
        given(context.authenticatedUser()).willReturn(appUser);
        resource = new LoanChargesApiResource(context, chargeReadPlatformService, loanChargeReadPlatformService, toApiJsonSerializer,
                apiRequestParameterHelper, commandsSourceWritePlatformService, loanReadPlatformService);
    }

    private void loanOutsideOfficeHierarchy(final Long loanId) {
        willThrow(new LoanNotFoundException(loanId)).given(loanReadPlatformService).validateLoanIsInOfficeHierarchy(loanId);
    }

    @Test
    void retrieveAllLoanChargesRejectsLoanOutsideOfficeHierarchy() {
        loanOutsideOfficeHierarchy(LOAN_ID);

        assertThrows(LoanNotFoundException.class, () -> resource.retrieveAllLoanCharges(LOAN_ID, uriInfo));

        verifyNoInteractions(loanChargeReadPlatformService);
    }

    @Test
    void retrieveAllLoanChargesByExternalIdRejectsLoanOutsideOfficeHierarchy() {
        given(loanReadPlatformService.getResolvedLoanId(any(ExternalId.class))).willReturn(LOAN_ID);
        loanOutsideOfficeHierarchy(LOAN_ID);

        assertThrows(LoanNotFoundException.class, () -> resource.retrieveAllLoanCharges(LOAN_EXTERNAL_ID, uriInfo));

        verifyNoInteractions(loanChargeReadPlatformService);
    }

    @Test
    void retrieveLoanChargeRejectsLoanOutsideOfficeHierarchy() {
        loanOutsideOfficeHierarchy(LOAN_ID);

        assertThrows(LoanNotFoundException.class, () -> resource.retrieveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, uriInfo));

        verifyNoInteractions(loanChargeReadPlatformService);
    }

    @Test
    void retrieveTemplateRejectsLoanOutsideOfficeHierarchy() {
        loanOutsideOfficeHierarchy(LOAN_ID);

        assertThrows(LoanNotFoundException.class, () -> resource.retrieveTemplate(LOAN_ID, uriInfo));

        verifyNoInteractions(chargeReadPlatformService);
    }

    @Test
    void writeCommandsRejectLoanOutsideOfficeHierarchy() {
        loanOutsideOfficeHierarchy(LOAN_ID);

        assertThrows(LoanNotFoundException.class, () -> resource.executeLoanCharge(LOAN_ID, (String) null, "{}"));
        assertThrows(LoanNotFoundException.class,
                () -> resource.executeLoanCharge(LOAN_ID, LOAN_CHARGE_ID, LoanChargesApiResource.COMMAND_WAIVE, "{}"));
        assertThrows(LoanNotFoundException.class, () -> resource.updateLoanCharge(LOAN_ID, LOAN_CHARGE_ID, "{}"));
        assertThrows(LoanNotFoundException.class, () -> resource.deleteLoanCharge(LOAN_ID, LOAN_CHARGE_ID));

        verifyNoInteractions(commandsSourceWritePlatformService);
    }

    @Test
    void retrieveAllLoanChargesReturnsChargesForLoanInsideOfficeHierarchy() {
        resource.retrieveAllLoanCharges(LOAN_ID, uriInfo);

        verify(loanReadPlatformService).validateLoanIsInOfficeHierarchy(LOAN_ID);
        verify(loanChargeReadPlatformService).retrieveLoanCharges(LOAN_ID);
    }
}
