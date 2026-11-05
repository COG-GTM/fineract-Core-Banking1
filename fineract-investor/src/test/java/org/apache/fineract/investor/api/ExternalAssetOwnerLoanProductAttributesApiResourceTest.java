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
package org.apache.fineract.investor.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.PlatformUserRightsContext;
import org.apache.fineract.investor.data.ExternalTransferLoanProductAttributesData;
import org.apache.fineract.investor.service.ExternalAssetOwnerLoanProductAttributesReadService;
import org.apache.fineract.portfolio.loanproduct.exception.LoanProductNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExternalAssetOwnerLoanProductAttributesApiResourceTest {

    private static final String RESOURCE = "EXTERNAL_ASSET_OWNER_LOAN_PRODUCT_ATTRIBUTE";

    @Mock
    private PlatformUserRightsContext platformUserRightsContext;

    @Mock
    private PlatformSecurityContext platformSecurityContext;

    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Mock
    private ExternalAssetOwnerLoanProductAttributesReadService readService;

    @Mock
    private AppUser user;

    private ExternalAssetOwnerLoanProductAttributesApiResource underTest;

    @BeforeEach
    public void setUp() {
        underTest = new ExternalAssetOwnerLoanProductAttributesApiResource(platformUserRightsContext, platformSecurityContext,
                commandsSourceWritePlatformService, readService);
        when(platformSecurityContext.authenticatedUser()).thenReturn(user);
    }

    @Test
    public void getAttributesRequiresReadPermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ " + RESOURCE)).when(user).validateHasReadPermission(RESOURCE);

        assertThrows(NoAuthorizationException.class, () -> underTest.getExternalAssetOwnerLoanProductAttributes(null, 999L, null));
        verifyNoInteractions(readService);
    }

    @Test
    public void getAttributesWithReadPermissionDelegatesToReadService() {
        Page<ExternalTransferLoanProductAttributesData> page = new Page<>(List.of(), 0);
        when(readService.retrieveAllLoanProductAttributesByLoanProductId(1L, "key")).thenReturn(page);

        assertSame(page, underTest.getExternalAssetOwnerLoanProductAttributes(null, 1L, "key"));
        verify(user).validateHasReadPermission(RESOURCE);
    }

    @Test
    public void getAttributesWithReadPermissionPropagatesNotFound() {
        when(readService.retrieveAllLoanProductAttributesByLoanProductId(999L, null)).thenThrow(new LoanProductNotFoundException(999L));

        assertThrows(LoanProductNotFoundException.class, () -> underTest.getExternalAssetOwnerLoanProductAttributes(null, 999L, null));
        verify(user).validateHasReadPermission(RESOURCE);
    }
}
