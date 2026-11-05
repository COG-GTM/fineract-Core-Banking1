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
package org.apache.fineract.infrastructure.codes.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.codes.data.CodeData;
import org.apache.fineract.infrastructure.codes.service.CodeReadPlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodesApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private CodeReadPlatformService readPlatformService;
    @Mock
    private DefaultToApiJsonSerializer<CodeData> toApiJsonSerializer;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private AppUser user;
    @Mock
    private UriInfo uriInfo;

    private CodesApiResource resource;

    @BeforeEach
    void setUp() {
        given(context.authenticatedUser()).willReturn(user);
        given(uriInfo.getQueryParameters()).willReturn(new MultivaluedHashMap<>());
        resource = new CodesApiResource(context, readPlatformService, toApiJsonSerializer, apiRequestParameterHelper,
                commandsSourceWritePlatformService);
    }

    @Test
    void retrieveCodeRequiresReadCodePermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ codes")).when(user).validateHasReadPermission("CODE");

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveCode(1L, uriInfo));
        verify(readPlatformService, never()).retrieveCode(anyLong());
    }

    @Test
    void retrieveCodeByNameRequiresReadCodePermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ codes")).when(user).validateHasReadPermission("CODE");

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveCodeByName("Gender", uriInfo));
        verify(readPlatformService, never()).retrieveCode(anyString());
    }

    @Test
    void retrieveCodeWithReadCodePermission() {
        resource.retrieveCode(1L, uriInfo);

        verify(user).validateHasReadPermission("CODE");
        verify(readPlatformService).retrieveCode(1L);
    }

    @Test
    void retrieveCodeByNameWithReadCodePermission() {
        resource.retrieveCodeByName("Gender", uriInfo);

        verify(user).validateHasReadPermission("CODE");
        verify(readPlatformService).retrieveCode("Gender");
    }
}
