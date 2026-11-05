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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import org.apache.fineract.command.core.Command;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.mix.data.MixTaxonomyMappingData;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateRequest;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateResponse;
import org.apache.fineract.mix.service.MixTaxonomyMappingReadService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MixTaxonomyMappingApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private MixTaxonomyMappingReadService readService;
    @Mock
    private CommandPipeline commandPipeline;

    private MixTaxonomyMappingApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
        resource = new MixTaxonomyMappingApiResource(context, readService, commandPipeline);
    }

    @Test
    void retrieveRejectsUserWithoutReadPermission() {
        doThrow(new NoAuthorizationException("denied")).when(user).validateHasPermissionTo(MixTaxonomyMappingApiResource.READ_PERMISSION,
                MixTaxonomyMappingApiResource.READ_PERMISSIONS);

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveTaxonomyMapping());
        verifyNoInteractions(readService);
    }

    @Test
    void retrieveReturnsMappingForAuthorizedUser() {
        final MixTaxonomyMappingData data = MixTaxonomyMappingData.builder().build();
        when(readService.retrieveTaxonomyMapping()).thenReturn(data);

        assertSame(data, resource.retrieveTaxonomyMapping());
        verify(user).validateHasPermissionTo(MixTaxonomyMappingApiResource.READ_PERMISSION, MixTaxonomyMappingApiResource.READ_PERMISSIONS);
    }

    @Test
    void updateRejectsUserWithoutUpdatePermission() {
        doThrow(new NoAuthorizationException("denied")).when(user)
                .validateHasPermissionTo(MixTaxonomyMappingApiResource.UPDATE_PERMISSION, MixTaxonomyMappingApiResource.UPDATE_PERMISSIONS);

        final MixTaxonomyMappingUpdateRequest request = new MixTaxonomyMappingUpdateRequest();
        assertThrows(NoAuthorizationException.class, () -> resource.updateTaxonomyMapping(request));
        verifyNoInteractions(commandPipeline);
    }

    @Test
    void updateSendsCommandForAuthorizedUser() {
        final MixTaxonomyMappingUpdateResponse expected = MixTaxonomyMappingUpdateResponse.builder().entityId(1L).build();
        final Supplier<MixTaxonomyMappingUpdateResponse> supplier = () -> expected;
        when(commandPipeline.send(any(Command.class))).thenAnswer(invocation -> supplier);

        assertSame(expected, resource.updateTaxonomyMapping(new MixTaxonomyMappingUpdateRequest()));
        verify(user).validateHasPermissionTo(MixTaxonomyMappingApiResource.UPDATE_PERMISSION,
                MixTaxonomyMappingApiResource.UPDATE_PERMISSIONS);
    }
}
