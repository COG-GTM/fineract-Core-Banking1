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
package org.apache.fineract.interoperation.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.interoperation.domain.InteropIdentifierType;
import org.apache.fineract.interoperation.service.InteropService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InteropApiResourceTest {

    private static final String ACCOUNT_ID = "acc-ext-id";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private InteropService interopService;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private InteropApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void getAccountDetailsRequiresSavingsReadPermission() {
        assertDenied("savingsaccount", () -> resource.getAccountDetails(ACCOUNT_ID, uriInfo));
    }

    @Test
    void getAccountTransactionsRequiresSavingsReadPermission() {
        assertDenied("savingsaccount", () -> resource.getAccountTransactions(ACCOUNT_ID, true, false, null, null, uriInfo));
    }

    @Test
    void getAccountIdentifiersRequiresIdentifierReadPermission() {
        assertDenied("INTERID", () -> resource.getAccountIdentifiers(ACCOUNT_ID, uriInfo));
    }

    @Test
    void getAccountByIdentifierRequiresIdentifierReadPermission() {
        assertDenied("INTERID", () -> resource.getAccountByIdentifier(InteropIdentifierType.MSISDN, "123", uriInfo));
    }

    @Test
    void getAccountByIdentifierWithSubTypeRequiresIdentifierReadPermission() {
        assertDenied("INTERID", () -> resource.getAccountByIdentifier(InteropIdentifierType.MSISDN, "123", "sub", uriInfo));
    }

    @Test
    void getClientKycRequiresSavingsReadPermission() {
        assertDenied("savingsaccount", () -> resource.getClientKyc(ACCOUNT_ID, uriInfo));
    }

    @Test
    void getClientKycRequiresClientReadPermission() {
        assertDenied("client", () -> resource.getClientKyc(ACCOUNT_ID, uriInfo));
        verify(user).validateHasReadPermission("savingsaccount");
    }

    private void assertDenied(String resourceType, Executable call) {
        doThrow(new NoAuthorizationException("User has no authority to view " + resourceType)).when(user)
                .validateHasReadPermission(resourceType);

        assertThrows(NoAuthorizationException.class, call);

        verify(user).validateHasReadPermission(resourceType);
        verifyNoInteractions(interopService);
    }
}
