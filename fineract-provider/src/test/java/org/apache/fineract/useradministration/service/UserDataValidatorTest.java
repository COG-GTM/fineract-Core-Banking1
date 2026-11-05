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
package org.apache.fineract.useradministration.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserDataValidatorTest {

    private UserDataValidator underTest;
    private AppUser selfServiceUser;
    private AppUser staffWithoutUpdateUser;
    private AppUser staffWithUpdateUser;

    @BeforeEach
    void setUp() {
        underTest = new UserDataValidator(new FromJsonHelper(), mock(PasswordValidationPolicyRepository.class));

        selfServiceUser = mock(AppUser.class);
        when(selfServiceUser.hasAnyPermission("ALL_FUNCTIONS", "UPDATE_USER")).thenReturn(false);
        when(selfServiceUser.isSelfServiceUser()).thenReturn(true);

        staffWithoutUpdateUser = mock(AppUser.class);
        when(staffWithoutUpdateUser.hasAnyPermission("ALL_FUNCTIONS", "UPDATE_USER")).thenReturn(false);

        staffWithUpdateUser = mock(AppUser.class);
        when(staffWithUpdateUser.hasAnyPermission("ALL_FUNCTIONS", "UPDATE_USER")).thenReturn(true);
    }

    @Test
    void selfServiceUserCannotChangeOwnClientMapping() {
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateFieldLevelACL("{\"clients\":[2,3]}", selfServiceUser));

        assertEquals("not.enough.permission.to.update.fields", ex.getErrors().get(0).getUserMessageGlobalisationCode());
        assertEquals("clients", ex.getErrors().get(0).getParameterName());
    }

    @Test
    void selfServiceUserCannotChangeSelfServiceFlag() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateFieldLevelACL("{\"isSelfServiceUser\":true}", selfServiceUser));
    }

    @Test
    void selfServiceUserCanStillChangePassword() {
        assertDoesNotThrow(() -> underTest.validateFieldLevelACL("{\"password\":\"x\",\"repeatPassword\":\"x\"}", selfServiceUser));
    }

    @Test
    void userWithoutUpdateUserPermissionCannotChangeClients() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateFieldLevelACL("{\"clients\":[2]}", staffWithoutUpdateUser));
    }

    @Test
    void userWithUpdateUserPermissionCanChangeClients() {
        assertDoesNotThrow(() -> underTest.validateFieldLevelACL("{\"clients\":[2],\"isSelfServiceUser\":true}", staffWithUpdateUser));
    }

    @Test
    void nonObjectBodyDoesNotFailOnImmutableParamSet() {
        assertDoesNotThrow(() -> underTest.validateFieldLevelACL("[]", selfServiceUser));
    }
}
