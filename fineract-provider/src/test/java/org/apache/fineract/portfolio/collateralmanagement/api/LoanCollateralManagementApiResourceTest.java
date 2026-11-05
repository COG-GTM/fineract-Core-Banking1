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
package org.apache.fineract.portfolio.collateralmanagement.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.collateralmanagement.data.LoanCollateralResponseData;
import org.apache.fineract.portfolio.collateralmanagement.service.LoanCollateralManagementReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanCollateralManagementApiResourceTest {

    private static final Long COLLATERAL_ID = 57L;

    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private LoanCollateralManagementReadPlatformService loanCollateralManagementReadPlatformService;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;

    @InjectMocks
    private LoanCollateralManagementApiResource resource;

    @Test
    void getLoanCollateralRejectsUserWithoutReadPermission() {
        when(context.authenticatedUser()).thenReturn(appUser);
        doThrow(new NoAuthorizationException("User has no authority to READ loan_collateral_products")).when(appUser)
                .validateHasReadPermission("LOAN_COLLATERAL_PRODUCT");

        assertThrows(NoAuthorizationException.class, () -> resource.getLoanCollateral(COLLATERAL_ID));

        verifyNoInteractions(loanCollateralManagementReadPlatformService);
    }

    @Test
    void getLoanCollateralReturnsDataForUserWithReadPermission() {
        LoanCollateralResponseData data = new LoanCollateralResponseData(COLLATERAL_ID, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN,
                3L);
        when(context.authenticatedUser()).thenReturn(appUser);
        when(loanCollateralManagementReadPlatformService.getLoanCollateralResponseData(COLLATERAL_ID)).thenReturn(data);

        assertSame(data, resource.getLoanCollateral(COLLATERAL_ID));

        verify(appUser).validateHasReadPermission("LOAN_COLLATERAL_PRODUCT");
    }
}
