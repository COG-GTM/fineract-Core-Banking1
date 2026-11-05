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
package org.apache.fineract.infrastructure.core.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.authorization.AuthenticatedAuthorizationManager.fullyAuthenticated;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class SecurityConfigTest {

    private static final List<AuthorizationManager<RequestAuthorizationContext>> TWO_FACTOR_BASE = List.of(fullyAuthenticated(),
            hasAuthority("TWOFACTOR_AUTHENTICATED"));

    private final RequestAuthorizationContext context = new RequestAuthorizationContext(Mockito.mock(HttpServletRequest.class));

    @Test
    void explicitRuleDeniesPermittedUserWithoutSecondFactor() {
        assertFalse(isGranted(SecurityConfig.withBase(TWO_FACTOR_BASE, "ALL_FUNCTIONS", "READ_DOCUMENT"), "READ_DOCUMENT"));
    }

    @Test
    void explicitRuleGrantsPermittedUserWithSecondFactor() {
        assertTrue(isGranted(SecurityConfig.withBase(TWO_FACTOR_BASE, "ALL_FUNCTIONS", "READ_DOCUMENT"), "READ_DOCUMENT",
                "TWOFACTOR_AUTHENTICATED"));
    }

    @Test
    void explicitRuleDeniesSecondFactorUserWithoutPermission() {
        assertFalse(isGranted(SecurityConfig.withBase(TWO_FACTOR_BASE, "ALL_FUNCTIONS", "READ_DOCUMENT"), "TWOFACTOR_AUTHENTICATED"));
    }

    @Test
    void explicitRuleKeepsPermissionOnlyCheckWhenTwoFactorDisabled() {
        assertTrue(isGranted(SecurityConfig.withBase(List.of(fullyAuthenticated()), "ALL_FUNCTIONS", "READ_DOCUMENT"), "READ_DOCUMENT"));
    }

    private boolean isGranted(AuthorizationManager<RequestAuthorizationContext> manager, String... authorities) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated("user", "password",
                AuthorityUtils.createAuthorityList(authorities));
        return manager.check(() -> authentication, context).isGranted();
    }
}
