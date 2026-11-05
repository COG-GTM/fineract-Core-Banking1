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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * URL level permission rules for the Fineract API, shared by the Basic Auth ({@link SecurityConfig}) and the OAuth2
 * ({@code AuthorizationServerConfig}) security filter chains.
 */
public final class ApiPermissionRules {

    private static final PathPatternRequestMatcher.Builder API_MATCHER = PathPatternRequestMatcher.withDefaults();
    private static final String ALL_FUNCTIONS = "ALL_FUNCTIONS";
    private static final String ALL_FUNCTIONS_READ = "ALL_FUNCTIONS_READ";
    private static final String ALL_FUNCTIONS_WRITE = "ALL_FUNCTIONS_WRITE";
    static final String TWOFACTOR_AUTHENTICATED = "TWOFACTOR_AUTHENTICATED";

    static final List<Rule> RULES = List.of(
            // businessdate
            rule(HttpMethod.GET, "/api/*/businessdate", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_BUSINESS_DATE"),
            rule(HttpMethod.GET, "/api/*/businessdate/*", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_BUSINESS_DATE"),
            rule(HttpMethod.POST, "/api/*/businessdate", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_BUSINESS_DATE"),
            // external
            rule(HttpMethod.GET, "/api/*/externalevents/configuration", ALL_FUNCTIONS, ALL_FUNCTIONS_READ,
                    "READ_EXTERNAL_EVENT_CONFIGURATION"),
            rule(HttpMethod.PUT, "/api/*/externalevents/configuration", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE,
                    "UPDATE_EXTERNAL_EVENT_CONFIGURATION"),
            // cache
            rule(HttpMethod.GET, "/api/*/caches", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_CACHE"),
            rule(HttpMethod.PUT, "/api/*/caches", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_CACHE"),
            // currency
            rule(HttpMethod.GET, "/api/*/currencies", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_CURRENCY"),
            rule(HttpMethod.PUT, "/api/*/currencies", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_CURRENCY"),
            // notes: read
            rule(HttpMethod.GET, "/api/*/clients/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_CLIENTNOTE"),
            rule(HttpMethod.GET, "/api/*/loans/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_LOANNOTE"),
            rule(HttpMethod.GET, "/api/*/loanTransactions/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_LOANTRANSACTIONNOTE"),
            rule(HttpMethod.GET, "/api/*/savings/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_SAVINGNOTE"),
            rule(HttpMethod.GET, "/api/*/groups/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_GROUPNOTE"),
            // notes: create
            rule(HttpMethod.POST, "/api/*/clients/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_CLIENTNOTE"),
            rule(HttpMethod.POST, "/api/*/loans/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_LOANNOTE"),
            rule(HttpMethod.POST, "/api/*/loanTransactions/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_LOANTRANSACTIONNOTE"),
            rule(HttpMethod.POST, "/api/*/savings/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_SAVINGNOTE"),
            rule(HttpMethod.POST, "/api/*/groups/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_GROUPNOTE"),
            // notes: update
            rule(HttpMethod.PUT, "/api/*/clients/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_CLIENTNOTE"),
            rule(HttpMethod.PUT, "/api/*/loans/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_LOANNOTE"),
            rule(HttpMethod.PUT, "/api/*/loanTransactions/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_LOANTRANSACTIONNOTE"),
            rule(HttpMethod.PUT, "/api/*/savings/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_SAVINGNOTE"),
            rule(HttpMethod.PUT, "/api/*/groups/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_GROUPNOTE"),
            // notes: delete
            rule(HttpMethod.DELETE, "/api/*/clients/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_CLIENTNOTE"),
            rule(HttpMethod.DELETE, "/api/*/loans/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_LOANNOTE"),
            rule(HttpMethod.DELETE, "/api/*/loanTransactions/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_LOANTRANSACTIONNOTE"),
            rule(HttpMethod.DELETE, "/api/*/savings/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_SAVINGNOTE"),
            rule(HttpMethod.DELETE, "/api/*/groups/*/notes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_GROUPNOTE"),
            // document: clients
            rule(HttpMethod.GET, "/api/*/clients/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT"),
            rule(HttpMethod.POST, "/api/*/clients/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_DOCUMENT"),
            rule(HttpMethod.PUT, "/api/*/clients/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_DOCUMENT"),
            rule(HttpMethod.DELETE, "/api/*/clients/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_DOCUMENT"),
            // document: client_identifiers
            rule(HttpMethod.GET, "/api/*/client_identifiers/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT"),
            rule(HttpMethod.POST, "/api/*/client_identifiers/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_DOCUMENT"),
            rule(HttpMethod.PUT, "/api/*/client_identifiers/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_DOCUMENT"),
            rule(HttpMethod.DELETE, "/api/*/client_identifiers/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_DOCUMENT"),
            // document: staff
            rule(HttpMethod.GET, "/api/*/staff/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT"),
            rule(HttpMethod.POST, "/api/*/staff/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_DOCUMENT"),
            rule(HttpMethod.PUT, "/api/*/staff/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_DOCUMENT"),
            rule(HttpMethod.DELETE, "/api/*/staff/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_DOCUMENT"),
            // document: loans
            rule(HttpMethod.GET, "/api/*/loans/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT"),
            rule(HttpMethod.POST, "/api/*/loans/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_DOCUMENT"),
            rule(HttpMethod.PUT, "/api/*/loans/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_DOCUMENT"),
            rule(HttpMethod.DELETE, "/api/*/loans/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_DOCUMENT"),
            // document: savings
            rule(HttpMethod.GET, "/api/*/savings/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT"),
            rule(HttpMethod.POST, "/api/*/savings/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_DOCUMENT"),
            rule(HttpMethod.PUT, "/api/*/savings/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_DOCUMENT"),
            rule(HttpMethod.DELETE, "/api/*/savings/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_DOCUMENT"),
            // document: groups
            rule(HttpMethod.GET, "/api/*/groups/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT"),
            rule(HttpMethod.POST, "/api/*/groups/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_DOCUMENT"),
            rule(HttpMethod.PUT, "/api/*/groups/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_DOCUMENT"),
            rule(HttpMethod.DELETE, "/api/*/groups/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_DOCUMENT"),
            // document: import
            rule(HttpMethod.GET, "/api/*/import/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT"),
            rule(HttpMethod.POST, "/api/*/import/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_DOCUMENT"),
            rule(HttpMethod.PUT, "/api/*/import/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_DOCUMENT"),
            rule(HttpMethod.DELETE, "/api/*/import/*/documents", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_DOCUMENT"),
            // image: clients
            rule(HttpMethod.GET, "/api/*/clients/*/images", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT", "READ_CLIENTIMAGE"),
            rule(HttpMethod.POST, "/api/*/clients/*/images", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_CLIENTIMAGE"),
            rule(HttpMethod.PUT, "/api/*/clients/*/images", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_CLIENTIMAGE"),
            rule(HttpMethod.DELETE, "/api/*/clients/*/images", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_CLIENTIMAGE"),
            // image: staff
            rule(HttpMethod.GET, "/api/*/staff/*/images", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_DOCUMENT", "READ_STAFFIMAGE"),
            rule(HttpMethod.POST, "/api/*/staff/*/images", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_STAFFIMAGE"),
            rule(HttpMethod.PUT, "/api/*/staff/*/images", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_STAFFIMAGE"),
            rule(HttpMethod.DELETE, "/api/*/staff/*/images", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_STAFFIMAGE"),
            // bulk import
            rule(HttpMethod.GET, "/api/*/import", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_IMPORT"),
            rule(HttpMethod.GET, "/api/*/import/*", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_IMPORT"),
            // payment type
            rule(HttpMethod.GET, "/api/*/paymenttypes", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_PAYMENTTYPE"),
            rule(HttpMethod.GET, "/api/*/paymenttypes/*", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_PAYMENTTYPE"),
            rule(HttpMethod.POST, "/api/*/paymenttypes", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_PAYMENTTYPE"),
            rule(HttpMethod.PUT, "/api/*/paymenttypes/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_PAYMENTTYPE"),
            rule(HttpMethod.DELETE, "/api/*/paymenttypes/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_PAYMENTTYPE"),
            // mix: taxonomy
            rule(HttpMethod.GET, "/api/*/mixtaxonomy/*", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_MIX_TAXONOMY"),
            rule(HttpMethod.POST, "/api/*/mixtaxonomy/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_MIX_TAXONOMY"),
            rule(HttpMethod.PUT, "/api/*/mixtaxonomy/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_MIX_TAXONOMY"),
            rule(HttpMethod.DELETE, "/api/*/mixtaxonomy/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_MIX_TAXONOMY"),
            // mix: mapping
            rule(HttpMethod.GET, "/api/*/mixmapping", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_MIX_MAPPING", "UPDATE_MIX_MAPPING",
                    "UPDATE_XBRLMAPPING"),
            rule(HttpMethod.GET, "/api/*/mixmapping/*", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_MIX_MAPPING", "UPDATE_MIX_MAPPING",
                    "UPDATE_XBRLMAPPING"),
            rule(HttpMethod.POST, "/api/*/mixmapping/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_MIX_MAPPING"),
            // TODO: "UPDATE_XBRLMAPPING" is the legacy permission name; we should rename for consistency
            rule(HttpMethod.PUT, "/api/*/mixmapping", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_MIX_MAPPING", "UPDATE_XBRLMAPPING"),
            rule(HttpMethod.PUT, "/api/*/mixmapping/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_MIX_MAPPING", "UPDATE_XBRLMAPPING"),
            rule(HttpMethod.DELETE, "/api/*/mixmapping/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_MIX_MAPPING"),
            // mix: report
            rule(HttpMethod.GET, "/api/*/mixreport/*", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_MIX_REPORT"),
            rule(HttpMethod.POST, "/api/*/mixreport/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "CREATE_MIX_REPORT"),
            rule(HttpMethod.PUT, "/api/*/mixreport/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_MIX_REPORT"),
            rule(HttpMethod.DELETE, "/api/*/mixreport/*", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "DELETE_MIX_REPORT"),
            // working days
            rule(HttpMethod.GET, "/api/*/workingdays", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_WORKING_DAYS", "READ_WORKINGDAYS"),
            rule(HttpMethod.GET, "/api/*/workingdays/template", ALL_FUNCTIONS, ALL_FUNCTIONS_READ, "READ_WORKING_DAYS", "READ_WORKINGDAYS"),
            rule(HttpMethod.PUT, "/api/*/workingdays", ALL_FUNCTIONS, ALL_FUNCTIONS_WRITE, "UPDATE_WORKING_DAYS", "UPDATE_WORKINGDAYS"));

    private ApiPermissionRules() {}

    /**
     * Registers the permission rules. When two-factor authentication is enabled, the rules also require the
     * {@value #TWOFACTOR_AUTHENTICATED} authority, like the generic {@code /api/**} rule.
     */
    public static void apply(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
            boolean twoFactorEnabled) {
        for (Rule rule : RULES) {
            auth.requestMatchers(rule.matcher()).access(rule.authorizationManager(twoFactorEnabled));
        }
    }

    /**
     * Matches the given pattern with and without a trailing slash, because Jersey routes both to the same resource.
     */
    public static RequestMatcher apiMatcher(HttpMethod method, String pattern) {
        RequestMatcher matcher = API_MATCHER.matcher(method, pattern);
        if (pattern.endsWith("**")) {
            return matcher;
        }
        return new OrRequestMatcher(matcher, API_MATCHER.matcher(method, pattern + "/"));
    }

    /**
     * Grants access when the authenticated user holds any of the given permissions. Permissions are read from the
     * {@link UserDetails} principal when present, so OAuth2 (JWT) authentications are checked against the user's
     * permission codes rather than the token scopes.
     */
    public static AuthorizationManager<RequestAuthorizationContext> hasAnyPermission(String... permissions) {
        return hasAnyPermission(false, permissions);
    }

    private static AuthorizationManager<RequestAuthorizationContext> hasAnyPermission(boolean requireTwoFactor, String... permissions) {
        Set<String> allowed = Set.of(permissions);
        return (authentication, context) -> {
            Authentication current = authentication.get();
            boolean granted = hasAnyPermission(current, allowed) && (!requireTwoFactor || isTwoFactorAuthenticated(current));
            return new AuthorizationDecision(granted);
        };
    }

    private static boolean isTwoFactorAuthenticated(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(TWOFACTOR_AUTHENTICATED::equals);
    }

    private static boolean hasAnyPermission(Authentication authentication, Set<String> allowed) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getPrincipal() instanceof UserDetails user
                ? user.getAuthorities()
                : authentication.getAuthorities();
        return authorities.stream().map(GrantedAuthority::getAuthority).anyMatch(allowed::contains);
    }

    private static Rule rule(HttpMethod method, String pattern, String... permissions) {
        return new Rule(method, pattern, permissions);
    }

    record Rule(HttpMethod method, String pattern, String... permissions) {

        RequestMatcher matcher() {
            return apiMatcher(method, pattern);
        }

        AuthorizationManager<RequestAuthorizationContext> authorizationManager(boolean requireTwoFactor) {
            return hasAnyPermission(requireTwoFactor, permissions);
        }
    }
}
