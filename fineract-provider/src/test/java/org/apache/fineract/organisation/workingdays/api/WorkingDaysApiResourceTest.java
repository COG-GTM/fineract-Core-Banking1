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
package org.apache.fineract.organisation.workingdays.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.workingdays.data.WorkingDaysData;
import org.apache.fineract.organisation.workingdays.data.WorkingDaysUpdateRequest;
import org.apache.fineract.organisation.workingdays.data.WorkingDaysUpdateRequestValidator;
import org.apache.fineract.organisation.workingdays.data.WorkingDaysUpdateResponse;
import org.apache.fineract.organisation.workingdays.service.WorkingDaysReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkingDaysApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private WorkingDaysReadPlatformService workingDaysReadPlatformService;
    @Mock
    private WorkingDaysUpdateRequestValidator workingDaysUpdateRequestValidator;
    @Mock
    private CommandPipeline commandPipeline;
    @Mock
    private AppUser user;

    @InjectMocks
    private WorkingDaysApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void updateRequiresUpdateWorkingDaysPermission() {
        doThrow(new NoAuthorizationException("no")).when(user).validateHasPermissionTo(eq("UPDATE_WORKINGDAYS"), anyList());

        assertThrows(NoAuthorizationException.class, () -> resource.update(new WorkingDaysUpdateRequest()));

        verifyNoInteractions(commandPipeline);
    }

    @Test
    void updateAllowsOnlyWritePermissionsAndSendsCommand() {
        WorkingDaysUpdateResponse expected = WorkingDaysUpdateResponse.builder().build();
        Supplier<WorkingDaysUpdateResponse> supplier = () -> expected;
        when(commandPipeline.<WorkingDaysUpdateRequest, WorkingDaysUpdateResponse>send(any())).thenReturn(supplier);

        assertSame(expected, resource.update(new WorkingDaysUpdateRequest()));

        verify(user).validateHasPermissionTo("UPDATE_WORKINGDAYS",
                List.of("ALL_FUNCTIONS", "ALL_FUNCTIONS_WRITE", "UPDATE_WORKINGDAYS", "UPDATE_WORKING_DAYS"));
    }

    @Test
    void retrieveAllRequiresReadPermission() {
        doThrow(new NoAuthorizationException("no")).when(user).validateHasPermissionTo(eq("READ_WORKINGDAYS"), anyList());

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveAll());

        verify(workingDaysReadPlatformService, never()).retrieve();
    }

    @Test
    void templateRequiresReadPermission() {
        doThrow(new NoAuthorizationException("no")).when(user).validateHasPermissionTo(eq("READ_WORKINGDAYS"), anyList());

        assertThrows(NoAuthorizationException.class, () -> resource.template());

        verify(workingDaysReadPlatformService, never()).repaymentRescheduleType();
    }

    @Test
    void retrieveAllReturnsDataWhenPermitted() {
        WorkingDaysData data = WorkingDaysData.builder().build();
        when(workingDaysReadPlatformService.retrieve()).thenReturn(data);

        assertSame(data, resource.retrieveAll());

        verify(user).validateHasPermissionTo("READ_WORKINGDAYS",
                List.of("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", "READ_WORKINGDAYS", "READ_WORKING_DAYS"));
    }
}
