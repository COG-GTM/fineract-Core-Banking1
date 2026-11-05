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
package org.apache.fineract.mix.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateRequest;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateResponse;
import org.apache.fineract.mix.service.MixReportXBRLBuilder;
import org.apache.fineract.mix.service.MixReportXBRLResultService;
import org.apache.fineract.mix.service.MixTaxonomyMappingReadService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MixApiResourcePermissionTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private MixReportXBRLResultService xbrlResultService;
    @Mock
    private MixReportXBRLBuilder xbrlBuilder;
    @Mock
    private MixTaxonomyMappingReadService readTaxonomyMappingService;
    @Mock
    private CommandPipeline commandPipeline;

    private MixReportApiResource reportResource;
    private MixTaxonomyMappingApiResource mappingResource;

    @BeforeEach
    void setUp() {
        reportResource = new MixReportApiResource(context, xbrlResultService, xbrlBuilder);
        mappingResource = new MixTaxonomyMappingApiResource(context, readTaxonomyMappingService, commandPipeline);
    }

    @Test
    void reportRejectsUserWithoutMixPermission() {
        loginWith("READ_CLIENT");

        assertThrows(NoAuthorizationException.class, () -> reportResource.retrieveXBRLReport(null, null, "USD"));
        verify(xbrlResultService, never()).getXBRLResult(any(), any(), any());
    }

    @Test
    void reportAllowsReadMixReport() {
        loginWith("READ_MIX_REPORT");

        assertDoesNotThrow(() -> reportResource.retrieveXBRLReport(null, null, "USD"));
        verify(xbrlResultService).getXBRLResult(null, null, "USD");
    }

    @Test
    void mappingReadRejectsUserWithoutMixPermission() {
        loginWith("READ_CLIENT");

        assertThrows(NoAuthorizationException.class, () -> mappingResource.retrieveTaxonomyMapping());
        verify(readTaxonomyMappingService, never()).retrieveTaxonomyMapping();
    }

    @Test
    void mappingReadAllowsReadOrUpdateMixMapping() {
        loginWith("READ_MIX_MAPPING");
        assertDoesNotThrow(() -> mappingResource.retrieveTaxonomyMapping());

        loginWith("UPDATE_XBRLMAPPING");
        assertDoesNotThrow(() -> mappingResource.retrieveTaxonomyMapping());
    }

    @Test
    void mappingUpdateRejectsUserWithoutMixPermission() {
        loginWith("READ_MIX_MAPPING");

        assertThrows(NoAuthorizationException.class, () -> mappingResource.updateTaxonomyMapping(new MixTaxonomyMappingUpdateRequest()));
        verify(commandPipeline, never()).send(any());
    }

    @Test
    void mappingUpdateAllowsUpdateXbrlMapping() {
        loginWith("UPDATE_XBRLMAPPING");
        stubCommandPipeline();

        assertDoesNotThrow(() -> mappingResource.updateTaxonomyMapping(new MixTaxonomyMappingUpdateRequest()));
        verify(commandPipeline).send(any());
    }

    @Test
    void allFunctionsUserIsAllowedEverywhere() {
        loginWith("ALL_FUNCTIONS");
        stubCommandPipeline();

        assertDoesNotThrow(() -> reportResource.retrieveXBRLReport(null, null, "USD"));
        assertDoesNotThrow(() -> mappingResource.retrieveTaxonomyMapping());
        assertDoesNotThrow(() -> mappingResource.updateTaxonomyMapping(new MixTaxonomyMappingUpdateRequest()));
    }

    private void stubCommandPipeline() {
        final Supplier<MixTaxonomyMappingUpdateResponse> response = MixTaxonomyMappingUpdateResponse::new;
        when(commandPipeline.<MixTaxonomyMappingUpdateRequest, MixTaxonomyMappingUpdateResponse>send(any())).thenReturn(response);
    }

    private void loginWith(final String permissionCode) {
        final int separator = permissionCode.indexOf('_');
        final Role role = new Role("test", "test");
        role.updatePermission(new Permission("test", permissionCode.substring(separator + 1), permissionCode.substring(0, separator)),
                true);
        final AppUser user = newAppUser();
        user.getRoles().add(role);
        when(context.authenticatedUser()).thenReturn(user);
    }

    private static AppUser newAppUser() {
        try {
            final Constructor<AppUser> constructor = AppUser.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
