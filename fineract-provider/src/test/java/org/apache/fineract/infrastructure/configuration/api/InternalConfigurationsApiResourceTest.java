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
package org.apache.fineract.infrastructure.configuration.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.service.MoneyHelperInitializationService;
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
class InternalConfigurationsApiResourceTest {

    private static final String CONFIG_NAME = "force-withdrawal-on-savings-account-limit";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private GlobalConfigurationRepositoryWrapper repository;
    @Mock
    private MoneyHelperInitializationService moneyHelperInitializationService;
    @Mock
    private AppUser user;
    @Mock
    private GlobalConfigurationProperty property;

    @InjectMocks
    private InternalConfigurationsApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void updateRejectedWithoutUpdateConfigurationPermission() {
        doThrow(new NoAuthorizationException("User has no authority to: UPDATE_CONFIGURATION")).when(user)
                .validateHasPermissionTo("UPDATE_CONFIGURATION");

        assertThrows(NoAuthorizationException.class, () -> resource.updateGlobalConfiguration(CONFIG_NAME, 999_999_999L));

        verifyNoInteractions(repository, moneyHelperInitializationService);
    }

    @Test
    void updateAppliedWithUpdateConfigurationPermission() {
        when(repository.findOneByNameWithNotFoundDetection(CONFIG_NAME)).thenReturn(property);
        when(property.getName()).thenReturn(CONFIG_NAME);

        Response response = resource.updateGlobalConfiguration(CONFIG_NAME, 10L);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(user).validateHasPermissionTo("UPDATE_CONFIGURATION");
        verify(property).setValue(10L);
        verify(repository).save(property);
        verify(repository).removeFromCache(CONFIG_NAME);
        verify(moneyHelperInitializationService, never()).initializeTenantRoundingMode(any());
    }
}
