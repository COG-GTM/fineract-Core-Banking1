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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

class SecurityConfigApiMatcherTest {

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        return request;
    }

    @Test
    void matchesPathWithAndWithoutTrailingSlash() {
        RequestMatcher matcher = SecurityConfig.apiMatcher(HttpMethod.PUT, "/api/*/workingdays");

        assertTrue(matcher.matches(request("PUT", "/api/v1/workingdays")));
        assertTrue(matcher.matches(request("PUT", "/api/v1/workingdays/")));
    }

    @Test
    void doesNotMatchOtherMethodsOrSubPaths() {
        RequestMatcher matcher = SecurityConfig.apiMatcher(HttpMethod.PUT, "/api/*/workingdays");

        assertFalse(matcher.matches(request("GET", "/api/v1/workingdays/")));
        assertFalse(matcher.matches(request("PUT", "/api/v1/workingdays/template")));
        assertFalse(matcher.matches(request("PUT", "/api/v1/workingdaysx")));
    }

    @Test
    void matchesWildcardSegmentWithTrailingSlash() {
        RequestMatcher matcher = SecurityConfig.apiMatcher(HttpMethod.GET, "/api/*/workingdays/template");

        assertTrue(matcher.matches(request("GET", "/api/v1/workingdays/template")));
        assertTrue(matcher.matches(request("GET", "/api/v1/workingdays/template/")));
    }

    @Test
    void catchAllPatternIsUnchanged() {
        RequestMatcher matcher = SecurityConfig.apiMatcher("/api/**");

        assertTrue(matcher.matches(request("DELETE", "/api/v1/anything")));
        assertTrue(matcher.matches(request("DELETE", "/api/v1/anything/")));
    }
}
