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
package org.apache.fineract.infrastructure.event.external.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.apache.fineract.command.core.Command;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.event.external.data.ExternalEventConfigurationResponse;
import org.apache.fineract.infrastructure.event.external.data.ExternalEventConfigurationUpdateRequest;
import org.apache.fineract.infrastructure.event.external.data.ExternalEventConfigurationUpdateResponse;
import org.apache.fineract.infrastructure.event.external.service.ExternalEventConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalEventConfigurationApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ExternalEventConfigurationReadPlatformService readPlatformService;
    @Mock
    private CommandPipeline commandPipeline;
    @Mock
    private AppUser user;

    @InjectMocks
    private ExternalEventConfigurationApiResource underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void getRequiresReadPermission() {
        doThrow(new NoAuthorizationException("denied")).when(user).validateHasReadPermission("EXTERNAL_EVENT_CONFIGURATION");

        assertThrows(NoAuthorizationException.class, () -> underTest.getExternalEventConfigurations());

        verifyNoInteractions(readPlatformService);
    }

    @Test
    void getReturnsConfigurationWhenPermitted() {
        ExternalEventConfigurationResponse response = mock(ExternalEventConfigurationResponse.class);
        when(readPlatformService.findAllExternalEventConfigurations()).thenReturn(response);

        assertSame(response, underTest.getExternalEventConfigurations());

        verify(user).validateHasReadPermission("EXTERNAL_EVENT_CONFIGURATION");
    }

    @Test
    void updateRequiresUpdatePermission() {
        doThrow(new NoAuthorizationException("denied")).when(user).validateHasPermissionTo(eq("UPDATE_EXTERNAL_EVENT_CONFIGURATION"),
                eq(List.of("ALL_FUNCTIONS_WRITE", "UPDATE_EXTERNAL_EVENT_CONFIGURATION")));

        assertThrows(NoAuthorizationException.class, () -> underTest.updateExternalEventConfigurations(updateRequest()));

        verifyNoInteractions(commandPipeline);
    }

    @Test
    void updateSendsCommandWhenPermitted() {
        ExternalEventConfigurationUpdateResponse response = mock(ExternalEventConfigurationUpdateResponse.class);
        when(commandPipeline.<ExternalEventConfigurationUpdateRequest, ExternalEventConfigurationUpdateResponse>send(any(Command.class)))
                .thenReturn(() -> response);

        assertSame(response, underTest.updateExternalEventConfigurations(updateRequest()));

        verify(user).validateHasPermissionTo("UPDATE_EXTERNAL_EVENT_CONFIGURATION",
                List.of("ALL_FUNCTIONS_WRITE", "UPDATE_EXTERNAL_EVENT_CONFIGURATION"));
    }

    private static ExternalEventConfigurationUpdateRequest updateRequest() {
        ExternalEventConfigurationUpdateRequest request = new ExternalEventConfigurationUpdateRequest();
        request.setExternalEventConfigurations(Map.of("LoanApprovedBusinessEvent", false));
        return request;
    }
}
