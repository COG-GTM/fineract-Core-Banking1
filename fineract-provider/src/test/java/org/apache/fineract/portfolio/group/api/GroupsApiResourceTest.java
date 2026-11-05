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
package org.apache.fineract.portfolio.group.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.group.exception.GroupNotFoundException;
import org.apache.fineract.portfolio.group.service.GroupReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.GLIMContainer;
import org.apache.fineract.portfolio.loanaccount.service.GLIMAccountInfoReadPlatformService;
import org.apache.fineract.portfolio.savings.data.GSIMContainer;
import org.apache.fineract.portfolio.savings.service.GSIMReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupsApiResourceTest {

    private static final Long OWN_GROUP_ID = 1L;
    private static final Long OTHER_OFFICE_GROUP_ID = 42L;

    private GroupReadPlatformService groupReadPlatformService;
    private GLIMAccountInfoReadPlatformService glimAccountInfoReadPlatformService;
    private GSIMReadPlatformService gsimReadPlatformService;
    private ToApiJsonSerializer<GLIMContainer> glimSerializer;
    private ToApiJsonSerializer<GSIMContainer> gsimSerializer;
    private UriInfo uriInfo;
    private GroupsApiResource resource;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        when(context.authenticatedUser()).thenReturn(mock(AppUser.class));
        groupReadPlatformService = mock(GroupReadPlatformService.class);
        when(groupReadPlatformService.retrieveOne(OTHER_OFFICE_GROUP_ID)).thenThrow(new GroupNotFoundException(OTHER_OFFICE_GROUP_ID));
        glimAccountInfoReadPlatformService = mock(GLIMAccountInfoReadPlatformService.class);
        gsimReadPlatformService = mock(GSIMReadPlatformService.class);
        glimSerializer = mock(ToApiJsonSerializer.class);
        gsimSerializer = mock(ToApiJsonSerializer.class);
        when(glimSerializer.serialize(any(ApiRequestJsonSerializationSettings.class), anyCollection(), anySet())).thenReturn("glim");
        when(gsimSerializer.serialize(any(ApiRequestJsonSerializationSettings.class), anyCollection(), anySet())).thenReturn("gsim");
        uriInfo = mock(UriInfo.class);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());

        resource = new GroupsApiResource(context, groupReadPlatformService, null, null, null, null, null, glimSerializer, gsimSerializer,
                new ApiRequestParameterHelper(), null, null, null, null, null, null, null, null, null, null,
                glimAccountInfoReadPlatformService, gsimReadPlatformService, null);
    }

    @Test
    void glimAccountsOfGroupOutsideOfficeHierarchyAreRejected() {
        assertThrows(GroupNotFoundException.class, () -> resource.retrieveglimAccounts(OTHER_OFFICE_GROUP_ID, null, uriInfo));
        assertThrows(GroupNotFoundException.class, () -> resource.retrieveglimAccounts(OTHER_OFFICE_GROUP_ID, "000000001", uriInfo));

        verify(glimAccountInfoReadPlatformService, never()).findGlimAccount(anyLong());
        verify(glimAccountInfoReadPlatformService, never()).findGlimAccountbyGroupAndAccount(anyLong(), anyString());
    }

    @Test
    void glimAccountsOfGroupInsideOfficeHierarchyAreReturned() {
        when(glimAccountInfoReadPlatformService.findGlimAccount(OWN_GROUP_ID)).thenReturn(Collections.emptyList());

        assertEquals("glim", resource.retrieveglimAccounts(OWN_GROUP_ID, null, uriInfo));
        verify(groupReadPlatformService).retrieveOne(OWN_GROUP_ID);
    }

    @Test
    void gsimAccountsOfGroupOutsideOfficeHierarchyAreRejected() {
        assertThrows(GroupNotFoundException.class, () -> resource.retrieveGsimAccounts(OTHER_OFFICE_GROUP_ID, null, null, uriInfo));

        verify(gsimReadPlatformService, never()).findGSIMAccountContainerByGroupId(anyLong());
    }

    @Test
    void gsimAccountByIdOwnedByGroupOutsideOfficeHierarchyIsRejected() {
        when(gsimReadPlatformService.findGsimAccountContainerbyGsimAccountId(7L)).thenReturn(List.of(gsim(OTHER_OFFICE_GROUP_ID)));

        assertThrows(GroupNotFoundException.class, () -> resource.retrieveGsimAccounts(OWN_GROUP_ID, null, 7L, uriInfo));
        verify(gsimSerializer, never()).serialize(any(ApiRequestJsonSerializationSettings.class), anyCollection(), anySet());
    }

    @Test
    void gsimAccountByNumberOwnedByGroupOutsideOfficeHierarchyIsRejected() {
        when(gsimReadPlatformService.findGsimAccountContainerbyGsimAccountNumber("000000007"))
                .thenReturn(List.of(gsim(OTHER_OFFICE_GROUP_ID)));

        assertThrows(GroupNotFoundException.class, () -> resource.retrieveGsimAccounts(OWN_GROUP_ID, "000000007", null, uriInfo));
        verify(gsimSerializer, never()).serialize(any(ApiRequestJsonSerializationSettings.class), anyCollection(), anySet());
    }

    @Test
    void gsimAccountByIdOwnedByGroupInsideOfficeHierarchyIsReturned() {
        when(gsimReadPlatformService.findGsimAccountContainerbyGsimAccountId(7L)).thenReturn(List.of(gsim(OWN_GROUP_ID)));

        assertEquals("gsim", resource.retrieveGsimAccounts(OWN_GROUP_ID, null, 7L, uriInfo));
        verify(groupReadPlatformService).retrieveOne(OWN_GROUP_ID);
    }

    private static GSIMContainer gsim(Long groupId) {
        return new GSIMContainer(BigDecimal.valueOf(7), BigDecimal.valueOf(groupId), "000000007", Collections.emptyList(), BigDecimal.ZERO,
                "Active");
    }
}
