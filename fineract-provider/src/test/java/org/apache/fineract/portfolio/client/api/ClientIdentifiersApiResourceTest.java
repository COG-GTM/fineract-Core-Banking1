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
package org.apache.fineract.portfolio.client.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.data.ClientIdentifierData;
import org.apache.fineract.portfolio.client.data.ClientIdentifierRequest;
import org.apache.fineract.portfolio.client.exception.DuplicateClientIdentifierException;
import org.apache.fineract.portfolio.client.service.ClientIdentifierReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientIdentifiersApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ClientReadPlatformService clientReadPlatformService;
    @Mock
    private ClientIdentifierReadPlatformService clientIdentifierReadPlatformService;
    @Mock
    private CodeValueReadPlatformService codeValueReadPlatformService;
    @Mock
    private DefaultToApiJsonSerializer<ClientIdentifierData> toApiJsonSerializer;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    private ClientIdentifiersApiResource resource;
    private final ClientIdentifierRequest request = new ClientIdentifierRequest();

    @BeforeEach
    void setUp() {
        resource = new ClientIdentifiersApiResource(context, clientReadPlatformService, clientIdentifierReadPlatformService,
                codeValueReadPlatformService, toApiJsonSerializer, apiRequestParameterHelper, commandsSourceWritePlatformService);
        when(toApiJsonSerializer.serialize(any())).thenReturn("{}");
    }

    @Test
    void createDuplicateHeldOutsideCallerHierarchyDoesNotRevealHolder() {
        DuplicateClientIdentifierException original = new DuplicateClientIdentifierException(7L, "Passport", "P123");
        when(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).thenThrow(original);
        when(clientReadPlatformService.retrieveClientByIdentifier(7L, "P123")).thenReturn(null);

        DuplicateClientIdentifierException thrown = assertThrows(DuplicateClientIdentifierException.class,
                () -> resource.createClientIdentifier(1L, request));

        assertSame(original, thrown);
        assertArrayEquals(new Object[] { "Passport", "P123" }, thrown.getDefaultUserMessageArgs());
    }

    @Test
    void updateDuplicateHeldOutsideCallerHierarchyDoesNotRevealHolder() {
        DuplicateClientIdentifierException original = new DuplicateClientIdentifierException(7L, "Passport", "P123");
        when(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).thenThrow(original);
        when(clientReadPlatformService.retrieveClientByIdentifier(7L, "P123")).thenReturn(null);

        DuplicateClientIdentifierException thrown = assertThrows(DuplicateClientIdentifierException.class,
                () -> resource.updateClientIdentifer(1L, 2L, request));

        assertSame(original, thrown);
    }

    @Test
    void createDuplicateHeldWithinCallerHierarchyIncludesHolder() {
        when(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class)))
                .thenThrow(new DuplicateClientIdentifierException(7L, "Passport", "P123"));
        when(clientReadPlatformService.retrieveClientByIdentifier(7L, "P123")).thenReturn(ClientData.lookup(5L, "Alice", 2L, "South"));

        DuplicateClientIdentifierException thrown = assertThrows(DuplicateClientIdentifierException.class,
                () -> resource.createClientIdentifier(1L, request));

        assertArrayEquals(new Object[] { "Alice", "South", "Passport", "P123" }, thrown.getDefaultUserMessageArgs());
    }

    @Test
    void activeTypeDuplicateSkipsHolderLookup() {
        DuplicateClientIdentifierException original = new DuplicateClientIdentifierException("Passport");
        when(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).thenThrow(original);

        assertSame(original, assertThrows(DuplicateClientIdentifierException.class, () -> resource.createClientIdentifier(1L, request)));
        verify(clientReadPlatformService, never()).retrieveClientByIdentifier(any(), any());
    }
}
