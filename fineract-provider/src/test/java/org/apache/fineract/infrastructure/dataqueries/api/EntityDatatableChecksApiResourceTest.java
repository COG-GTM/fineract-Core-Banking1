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
package org.apache.fineract.infrastructure.dataqueries.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksReadService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityDatatableChecksApiResourceTest {

    private static final String RESOURCE = "ENTITY_DATATABLE_CHECK";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private EntityDatatableChecksReadService readService;
    @Mock
    private ToApiJsonSerializer<GenericResultsetData> serializer;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    private EntityDatatableChecksApiResource underTest;

    @BeforeEach
    void setUp() {
        underTest = new EntityDatatableChecksApiResource(context, readService, serializer, commandsSourceWritePlatformService);
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void retrieveAllRequiresReadPermission() {
        doThrow(new NoAuthorizationException("no read")).when(user).validateHasReadPermission(RESOURCE);

        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveAll(null, null, null, null, null, null));

        verifyNoInteractions(readService);
    }

    @Test
    void getTemplateRequiresReadPermission() {
        doThrow(new NoAuthorizationException("no read")).when(user).validateHasReadPermission(RESOURCE);

        assertThrows(NoAuthorizationException.class, () -> underTest.getTemplate(null));

        verifyNoInteractions(readService);
    }

    @Test
    void retrieveAllWithReadPermissionQueriesService() {
        underTest.retrieveAll(null, 1, "m_client", 2L, 0, 10);

        verify(user).validateHasReadPermission(RESOURCE);
        verify(readService).retrieveAll(any(SearchParameters.class), eq(1), eq("m_client"), eq(2L));
    }

    @Test
    void getTemplateWithReadPermissionQueriesService() {
        underTest.getTemplate(null);

        verify(user).validateHasReadPermission(RESOURCE);
        verify(readService).retrieveTemplate();
    }
}
