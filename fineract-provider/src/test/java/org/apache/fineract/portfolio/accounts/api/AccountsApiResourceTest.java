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
package org.apache.fineract.portfolio.accounts.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Set;
import org.apache.fineract.infrastructure.bulkimport.service.BulkImportWorkbookPopulatorService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.shareaccounts.service.ShareAccountReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountsApiResourceTest {

    private static final String SHARE_ACCOUNT_RESOURCE = "SHAREACCOUNT";

    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private PlatformSecurityContext platformSecurityContext;
    @Mock
    private BulkImportWorkbookPopulatorService bulkImportWorkbookPopulatorService;
    @Mock
    private ShareAccountReadPlatformService shareAccountReadPlatformService;
    @Mock
    private AppUser appUser;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private AccountsApiResource underTest;

    @BeforeEach
    void setUp() {
        when(platformSecurityContext.authenticatedUser()).thenReturn(appUser);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        when(apiRequestParameterHelper.process(any())).thenReturn(ApiRequestJsonSerializationSettings.from(Set.of(), false, false, false));
    }

    @Test
    void typePathSegmentOnlyMatchesShare() {
        UriTemplate template = new UriTemplate(AccountsApiResource.class.getAnnotation(Path.class).value());

        assertTrue(template.match("/v1/accounts/share", new HashMap<>()));
        assertFalse(template.match("/v1/accounts/self", new HashMap<>()));
        assertFalse(template.match("/v1/accounts/anything", new HashMap<>()));
    }

    @Test
    void readEndpointsRejectUserWithoutShareAccountReadPermission() {
        doThrow(new NoAuthorizationException("no read permission")).when(appUser).validateHasReadPermission(SHARE_ACCOUNT_RESOURCE);

        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveAccount(1L, "share", uriInfo));
        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveAllAccounts("share", 0, 100));
        assertThrows(NoAuthorizationException.class, () -> underTest.template("share", 1L, 1L));
        assertThrows(NoAuthorizationException.class, () -> underTest.getSharedAccountsTemplate(1L, "dd MMMM yyyy", "share"));

        verifyNoInteractions(shareAccountReadPlatformService, bulkImportWorkbookPopulatorService);
    }

    @Test
    void readEndpointsDelegateWhenUserHasShareAccountReadPermission() {
        underTest.retrieveAccount(1L, "share", uriInfo);
        underTest.retrieveAllAccounts("share", 0, 100);
        underTest.template("share", 1L, 2L);

        verify(appUser, times(3)).validateHasReadPermission(SHARE_ACCOUNT_RESOURCE);
        verify(shareAccountReadPlatformService).retrieveOne(1L, false);
        verify(shareAccountReadPlatformService).retrieveAll(0, 100);
        verify(shareAccountReadPlatformService).retrieveTemplate(1L, 2L);
    }
}
