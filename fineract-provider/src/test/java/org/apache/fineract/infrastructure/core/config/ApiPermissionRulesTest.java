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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.RequestMatcher;

class ApiPermissionRulesTest {

    @ParameterizedTest
    @CsvSource({ "GET, /api/v1/externalevents/configuration, READ_EXTERNAL_EVENT_CONFIGURATION",
            "GET, /api/v1/externalevents/configuration/, READ_EXTERNAL_EVENT_CONFIGURATION",
            "PUT, /api/v1/externalevents/configuration, UPDATE_EXTERNAL_EVENT_CONFIGURATION",
            "PUT, /api/v1/externalevents/configuration/, UPDATE_EXTERNAL_EVENT_CONFIGURATION",
            "GET, /api/v1/businessdate, READ_BUSINESS_DATE", "GET, /api/v1/businessdate/BUSINESS_DATE/, READ_BUSINESS_DATE",
            "POST, /api/v1/businessdate/, UPDATE_BUSINESS_DATE", "GET, /api/v1/caches/, READ_CACHE", "PUT, /api/v1/caches/, UPDATE_CACHE",
            "GET, /api/v1/currencies/, READ_CURRENCY", "PUT, /api/v1/currencies, UPDATE_CURRENCY",
            "PUT, /api/v1/currencies/, UPDATE_CURRENCY", "GET, /api/v1/workingdays/, READ_WORKINGDAYS",
            "GET, /api/v1/workingdays/template/, READ_WORKINGDAYS", "PUT, /api/v1/workingdays/, UPDATE_WORKINGDAYS",
            "GET, /api/v1/paymenttypes, READ_PAYMENTTYPE", "GET, /api/v1/paymenttypes/1/, READ_PAYMENTTYPE",
            "POST, /api/v1/paymenttypes/, CREATE_PAYMENTTYPE", "PUT, /api/v1/paymenttypes/1, UPDATE_PAYMENTTYPE",
            "DELETE, /api/v1/paymenttypes/1/, DELETE_PAYMENTTYPE", "GET, /api/v1/mixmapping/, READ_MIX_MAPPING",
            "PUT, /api/v1/mixmapping, UPDATE_XBRLMAPPING", "PUT, /api/v1/mixmapping/, UPDATE_MIX_MAPPING" })
    void requiresPermissionWithAndWithoutTrailingSlash(String method, String path, String permission) {
        assertFalse(isGranted(method, path, basicAuth("READ_CLIENT")), "user without permission must be denied");
        assertTrue(isGranted(method, path, basicAuth(permission)));
        assertTrue(isGranted(method, path, basicAuth("ALL_FUNCTIONS")));
    }

    @Test
    void allFunctionsReadDoesNotGrantWrites() {
        assertTrue(isGranted("GET", "/api/v1/externalevents/configuration/", basicAuth("ALL_FUNCTIONS_READ")));
        assertFalse(isGranted("PUT", "/api/v1/externalevents/configuration/", basicAuth("ALL_FUNCTIONS_READ")));
        assertTrue(isGranted("PUT", "/api/v1/externalevents/configuration/", basicAuth("ALL_FUNCTIONS_WRITE")));
    }

    @Test
    void oauth2AuthenticationIsCheckedAgainstUserPermissionsNotTokenScopes() {
        Authentication withoutPermission = jwtLike(user("READ_CLIENT"), "SCOPE_UPDATE_EXTERNAL_EVENT_CONFIGURATION");
        Authentication withPermission = jwtLike(user("UPDATE_EXTERNAL_EVENT_CONFIGURATION"), "SCOPE_UPDATE_EXTERNAL_EVENT_CONFIGURATION");

        assertFalse(isGranted("PUT", "/api/v1/externalevents/configuration", withoutPermission));
        assertTrue(isGranted("PUT", "/api/v1/externalevents/configuration", withPermission));
    }

    @Test
    void unauthenticatedRequestIsDenied() {
        Authentication anonymous = new TestingAuthenticationToken("anonymous", null);
        anonymous.setAuthenticated(false);
        assertFalse(isGranted("GET", "/api/v1/externalevents/configuration", anonymous));
    }

    @Test
    void apiMatcherKeepsCatchAllPatternsUnchanged() {
        assertTrue(ApiPermissionRules.apiMatcher(HttpMethod.OPTIONS, "/api/**").matches(request("OPTIONS", "/api/v1/clients/")));
        RequestMatcher authentication = ApiPermissionRules.apiMatcher(HttpMethod.POST, "/api/*/authentication");
        assertTrue(authentication.matches(request("POST", "/api/v1/authentication/")));
        assertFalse(authentication.matches(request("POST", "/api/v1/authenticationx")));
    }

    @Test
    void unrelatedPathsAreNotCoveredByRules() {
        assertEquals(Optional.empty(), findRule(request("GET", "/api/v1/clients")));
    }

    @Test
    void twoFactorAuthorityIsRequiredWhenEnabled() {
        String path = "/api/v1/externalevents/configuration/";
        Authentication withoutTwoFactor = basicAuth("UPDATE_EXTERNAL_EVENT_CONFIGURATION");
        Authentication withTwoFactor = jwtLike(user("UPDATE_EXTERNAL_EVENT_CONFIGURATION"), ApiPermissionRules.TWOFACTOR_AUTHENTICATED);

        assertTrue(isGranted("PUT", path, withoutTwoFactor, false));
        assertFalse(isGranted("PUT", path, withoutTwoFactor, true));
        assertTrue(isGranted("PUT", path, withTwoFactor, true));
    }

    @Test
    void legacyPermissionsKeepAccess() {
        assertTrue(isGranted("GET", "/api/v1/mixmapping", basicAuth("UPDATE_XBRLMAPPING")));
        assertTrue(isGranted("GET", "/api/v1/clients/1/images", basicAuth("READ_CLIENTIMAGE")));
        assertTrue(isGranted("GET", "/api/v1/staff/1/images/", basicAuth("READ_STAFFIMAGE")));
        assertTrue(isGranted("GET", "/api/v1/workingdays", basicAuth("READ_WORKING_DAYS")));
    }

    private static boolean isGranted(String method, String path, Authentication authentication) {
        return isGranted(method, path, authentication, false);
    }

    private static boolean isGranted(String method, String path, Authentication authentication, boolean twoFactorEnabled) {
        MockHttpServletRequest request = request(method, path);
        ApiPermissionRules.Rule rule = findRule(request)
                .orElseThrow(() -> new AssertionError("No permission rule matches " + method + " " + path));
        return rule.authorizationManager(twoFactorEnabled).authorize(() -> authentication, new RequestAuthorizationContext(request))
                .isGranted();
    }

    private static Optional<ApiPermissionRules.Rule> findRule(MockHttpServletRequest request) {
        return ApiPermissionRules.RULES.stream().filter(rule -> rule.matcher().matches(request)).findFirst();
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private static UserDetails user(String... permissions) {
        return User.withUsername("mallory").password("secret").authorities(permissions).build();
    }

    private static Authentication basicAuth(String... permissions) {
        UserDetails user = user(permissions);
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }

    private static Authentication jwtLike(UserDetails principal, String... scopes) {
        return new TestingAuthenticationToken(principal, null, AuthorityUtils.createAuthorityList(scopes));
    }
}
