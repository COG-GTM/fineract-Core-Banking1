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
package org.apache.fineract.infrastructure.security.vote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class SelfServiceUserAuthorizationManagerTest {

    private final SelfServiceUserAuthorizationManager manager = SelfServiceUserAuthorizationManager.selfServiceUserAuthManager();

    @Test
    void selfServiceUserIsDeniedOnStaffEndpoint() {
        assertFalse(isGranted(user(true), "GET", "/fineract-provider/api/v1/loans/1"));
        assertFalse(isGranted(user(true), "PUT", "/fineract-provider/api/v1/clients/7"));
    }

    @Test
    void selfServiceUserIsAllowedOnSelfEndpoint() {
        assertTrue(isGranted(user(true), "GET", "/fineract-provider/api/v1/self/loans/1"));
    }

    @Test
    void staffUserIsDeniedOnSelfEndpoint() {
        assertFalse(isGranted(user(false), "GET", "/fineract-provider/api/v1/self/loans/1"));
    }

    @Test
    void staffUserIsAllowedOnStaffEndpoint() {
        assertTrue(isGranted(user(false), "GET", "/fineract-provider/api/v1/loans/1"));
    }

    @Test
    void optionsRequestIsNotRestricted() {
        assertTrue(isGranted(user(true), "OPTIONS", "/fineract-provider/api/v1/loans/1"));
    }

    @Test
    void nonAppUserPrincipalIsDenied() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertFalse(isGranted(anonymous, "GET", "/fineract-provider/api/v1/loans/1"));
        assertFalse(isGranted(null, "GET", "/fineract-provider/api/v1/self/loans/1"));
    }

    private boolean isGranted(Authentication authentication, String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        return manager.check(() -> authentication, new RequestAuthorizationContext(request)).isGranted();
    }

    private static Authentication user(boolean selfService) {
        AppUser appUser = mock(AppUser.class);
        when(appUser.isSelfServiceUser()).thenReturn(selfService);
        return new UsernamePasswordAuthenticationToken(appUser, null, AuthorityUtils.NO_AUTHORITIES);
    }
}
