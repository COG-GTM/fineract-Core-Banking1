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
package org.apache.fineract.infrastructure.instancemode.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import jakarta.ws.rs.core.Response;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.instancemode.InstanceModeMock;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.exception.UnAuthenticatedUserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstanceModeApiResourceTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private AppUser user;

    private FineractProperties fineractProperties;
    private InstanceModeApiResource underTest;

    @BeforeEach
    void setUp() {
        fineractProperties = new FineractProperties();
        fineractProperties.setMode(InstanceModeMock.createModeProps(true, true, true, true));
        underTest = new InstanceModeApiResource(fineractProperties, context);
    }

    @Test
    void changeModeRejectsUnauthenticatedCaller() {
        given(context.authenticatedUser()).willThrow(new UnAuthenticatedUserException());

        assertThrows(UnAuthenticatedUserException.class, () -> underTest.changeMode(disableAllRequest()));

        assertModeUnchanged();
    }

    @Test
    void changeModeRejectsUserWithoutPermission() {
        given(context.authenticatedUser()).willReturn(user);
        willThrow(new NoAuthorizationException("User has no authority to: UPDATE_INSTANCE_MODE")).given(user)
                .validateHasPermissionTo(InstanceModeApiResource.UPDATE_INSTANCE_MODE_PERMISSION);

        assertThrows(NoAuthorizationException.class, () -> underTest.changeMode(disableAllRequest()));

        assertModeUnchanged();
    }

    @Test
    void changeModeAppliesRequestForPermittedUser() {
        given(context.authenticatedUser()).willReturn(user);

        Response response = underTest.changeMode(disableAllRequest());

        assertEquals(200, response.getStatus());
        verify(user).validateHasPermissionTo(InstanceModeApiResource.UPDATE_INSTANCE_MODE_PERMISSION);
        FineractProperties.FineractModeProperties mode = fineractProperties.getMode();
        assertFalse(mode.isReadEnabled());
        assertFalse(mode.isWriteEnabled());
        assertFalse(mode.isBatchWorkerEnabled());
        assertFalse(mode.isBatchManagerEnabled());
    }

    private void assertModeUnchanged() {
        FineractProperties.FineractModeProperties mode = fineractProperties.getMode();
        assertTrue(mode.isReadEnabled());
        assertTrue(mode.isWriteEnabled());
        assertTrue(mode.isBatchWorkerEnabled());
        assertTrue(mode.isBatchManagerEnabled());
    }

    private static InstanceModeApiResourceSwagger.ChangeInstanceModeRequest disableAllRequest() {
        return new InstanceModeApiResourceSwagger.ChangeInstanceModeRequest();
    }
}
