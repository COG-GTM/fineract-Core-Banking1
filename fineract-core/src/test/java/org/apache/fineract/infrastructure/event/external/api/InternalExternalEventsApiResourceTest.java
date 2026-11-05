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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.event.external.data.ExternalEventResponse;
import org.apache.fineract.infrastructure.event.external.service.InternalExternalEventService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InternalExternalEventsApiResourceTest {

    @Mock
    private InternalExternalEventService internalExternalEventService;

    @Mock
    private PlatformSecurityContext context;

    @InjectMocks
    private InternalExternalEventsApiResource underTest;

    @Test
    void getAllExternalEventsRejectsUserWithoutAllFunctions() throws Exception {
        when(context.authenticatedUser()).thenReturn(userWithPermissions(new Permission("portfolio", "CLIENT", "READ"),
                new Permission("special", "FUNCTIONS_READ", "ALL")));

        assertThrows(NoAuthorizationException.class, () -> underTest.getAllExternalEvents(null, null, null, null));

        verify(internalExternalEventService, never()).getAllExternalEvents(any(), any(), any(), any());
    }

    @Test
    void deleteAllExternalEventsRejectsUserWithoutAllFunctions() throws Exception {
        when(context.authenticatedUser()).thenReturn(userWithPermissions(new Permission("portfolio", "CLIENT", "READ")));

        assertThrows(NoAuthorizationException.class, () -> underTest.deleteAllExternalEvents());

        verify(internalExternalEventService, never()).deleteAllExternalEvents();
    }

    @Test
    void superUserCanReadAndDeleteExternalEvents() throws Exception {
        when(context.authenticatedUser()).thenReturn(userWithPermissions(new Permission("special", "FUNCTIONS", "ALL")));
        List<ExternalEventResponse> events = List.of();
        when(internalExternalEventService.getAllExternalEvents("key", null, null, 1L)).thenReturn(events);

        assertSame(events, underTest.getAllExternalEvents("key", null, null, 1L));
        underTest.deleteAllExternalEvents();

        verify(internalExternalEventService).deleteAllExternalEvents();
    }

    private static AppUser userWithPermissions(Permission... permissions) throws Exception {
        Role role = new Role("role", "role");
        for (Permission permission : permissions) {
            role.updatePermission(permission, true);
        }
        Constructor<AppUser> constructor = AppUser.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AppUser user = constructor.newInstance();
        ReflectionTestUtils.setField(user, "roles", Set.of(role));
        return user;
    }
}
