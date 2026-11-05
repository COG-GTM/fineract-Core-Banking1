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
package org.apache.fineract.infrastructure.businessdate.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.businessdate.data.BusinessDateDTO;
import org.apache.fineract.infrastructure.businessdate.data.api.BusinessDateResponse;
import org.apache.fineract.infrastructure.businessdate.data.api.BusinessDateUpdateRequest;
import org.apache.fineract.infrastructure.businessdate.data.api.BusinessDateUpdateResponse;
import org.apache.fineract.infrastructure.businessdate.mapper.BusinessDateMapper;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessDateApiResourceTest {

    @Mock
    private BusinessDateReadPlatformService readPlatformService;
    @Mock
    private CommandPipeline commandPipeline;
    @Mock
    private BusinessDateMapper businessDateMapper;
    @Mock
    private PlatformSecurityContext context;

    @InjectMocks
    private BusinessDateApiResource underTest;

    private final BusinessDateUpdateResponse updateResponse = BusinessDateUpdateResponse.builder().build();

    @BeforeEach
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void setUp() {
        Supplier<BusinessDateUpdateResponse> supplier = () -> updateResponse;
        given(commandPipeline.send(any())).willReturn((Supplier) supplier);
        given(businessDateMapper.mapFetchResponse(any(BusinessDateDTO.class))).willReturn(BusinessDateResponse.builder().build());
    }

    @Test
    void userWithoutPermissionsCannotReadOrUpdateBusinessDate() {
        authenticateWith();

        assertThrows(NoAuthorizationException.class, () -> underTest.getBusinessDates());
        assertThrows(NoAuthorizationException.class, () -> underTest.getBusinessDate("BUSINESS_DATE"));
        assertThrows(NoAuthorizationException.class, () -> underTest.updateBusinessDate(new BusinessDateUpdateRequest()));

        verifyNoInteractions(readPlatformService, commandPipeline);
    }

    @Test
    void readPermissionAllowsReadButNotUpdate() {
        authenticateWith("READ_BUSINESS_DATE");

        underTest.getBusinessDates();
        underTest.getBusinessDate("BUSINESS_DATE");
        assertThrows(NoAuthorizationException.class, () -> underTest.updateBusinessDate(new BusinessDateUpdateRequest()));

        verify(readPlatformService).findAll();
        verify(readPlatformService).findByType("BUSINESS_DATE");
        verifyNoInteractions(commandPipeline);
    }

    @Test
    void allFunctionsReadDoesNotAllowUpdate() {
        authenticateWith("ALL_FUNCTIONS_READ");

        underTest.getBusinessDates();
        assertThrows(NoAuthorizationException.class, () -> underTest.updateBusinessDate(new BusinessDateUpdateRequest()));

        verifyNoInteractions(commandPipeline);
    }

    @Test
    void updatePermissionAllowsUpdate() {
        authenticateWith("UPDATE_BUSINESS_DATE");

        assertSame(updateResponse, underTest.updateBusinessDate(new BusinessDateUpdateRequest()));
        assertThrows(NoAuthorizationException.class, () -> underTest.getBusinessDates());
    }

    @Test
    void allFunctionsWriteAllowsUpdate() {
        authenticateWith("ALL_FUNCTIONS_WRITE");

        assertSame(updateResponse, underTest.updateBusinessDate(new BusinessDateUpdateRequest()));
    }

    @Test
    void allFunctionsAllowsReadAndUpdate() {
        authenticateWith("ALL_FUNCTIONS");

        underTest.getBusinessDates();
        underTest.getBusinessDate("COB_DATE");
        assertSame(updateResponse, underTest.updateBusinessDate(new BusinessDateUpdateRequest()));
    }

    @Test
    void securityRulesCoverTrailingSlashAndBasePaths() {
        PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
        PathPatternRequestMatcher post = builder.matcher(HttpMethod.POST, "/api/*/businessdate/**");
        PathPatternRequestMatcher get = builder.matcher(HttpMethod.GET, "/api/*/businessdate/**");

        assertTrue(post.matches(request("POST", "/api/v1/businessdate")));
        assertTrue(post.matches(request("POST", "/api/v1/businessdate/")));
        assertTrue(get.matches(request("GET", "/api/v1/businessdate")));
        assertTrue(get.matches(request("GET", "/api/v1/businessdate/")));
        assertTrue(get.matches(request("GET", "/api/v1/businessdate/BUSINESS_DATE")));
        assertTrue(get.matches(request("GET", "/api/v1/businessdate/BUSINESS_DATE/")));
        assertFalse(post.matches(request("POST", "/api/v1/businessdates")));

        assertFalse(builder.matcher(HttpMethod.POST, "/api/*/businessdate").matches(request("POST", "/api/v1/businessdate/")));
    }

    private void authenticateWith(String... permissionCodes) {
        Role role = new Role("role", "role");
        Arrays.stream(permissionCodes).map(BusinessDateApiResourceTest::permission).forEach(p -> role.updatePermission(p, true));
        AppUser user = mock(AppUser.class, Answers.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(user, "roles", new HashSet<>(List.of(role)));
        given(context.authenticatedUser()).willReturn(user);
    }

    private static Permission permission(String code) {
        int separator = code.indexOf('_');
        if (code.startsWith("ALL_FUNCTIONS")) {
            Permission permission = new Permission("special", "ALL_FUNCTIONS", "ALL");
            ReflectionTestUtils.setField(permission, "code", code);
            return permission;
        }
        return new Permission("organisation", code.substring(separator + 1), code.substring(0, separator));
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/fineract-provider" + path);
        request.setContextPath("/fineract-provider");
        return request;
    }
}
