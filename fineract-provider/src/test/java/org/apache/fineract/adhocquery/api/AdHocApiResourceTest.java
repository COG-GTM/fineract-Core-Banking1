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
package org.apache.fineract.adhocquery.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.adhocquery.data.AdHocData;
import org.apache.fineract.adhocquery.service.AdHocReadPlatformService;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdHocApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private AdHocReadPlatformService adHocReadPlatformService;
    @Mock
    private DefaultToApiJsonSerializer<AdHocData> toApiJsonSerializer;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    private AdHocApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
        resource = new AdHocApiResource(context, adHocReadPlatformService, toApiJsonSerializer, commandsSourceWritePlatformService);
    }

    @Test
    void retrieveAllRequiresAdHocReadPermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ adhocs")).when(user).validateHasReadPermission("ADHOC");

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveAll());
        verifyNoInteractions(adHocReadPlatformService);
    }

    @Test
    void retrieveOneRequiresAdHocReadPermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ adhocs")).when(user).validateHasReadPermission("ADHOC");

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveAdHocQuery(1L));
        verifyNoInteractions(adHocReadPlatformService);
    }

    @Test
    void retrieveAllReturnsDefinitionsWhenPermitted() {
        final List<AdHocData> definitions = List.of();
        when(adHocReadPlatformService.retrieveAllAdHocQuery()).thenReturn(definitions);

        assertSame(definitions, resource.retrieveAll());
        verify(user).validateHasReadPermission("ADHOC");
    }

    @Test
    void retrieveOneReturnsDefinitionWhenPermitted() {
        final AdHocData definition = AdHocData.template();
        when(adHocReadPlatformService.retrieveOne(1L)).thenReturn(definition);

        assertSame(definition, resource.retrieveAdHocQuery(1L));
        verify(user).validateHasReadPermission("ADHOC");
    }
}
