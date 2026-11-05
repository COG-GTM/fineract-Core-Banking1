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
package org.apache.fineract.commands.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PortfolioCommandSourceWritePlatformServiceImplTest {

    private static final Long OWN_USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final CommandProcessingService commandProcessingService = mock(CommandProcessingService.class);
    private final AppUser authenticatedUser = mock(AppUser.class);
    private PortfolioCommandSourceWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new PortfolioCommandSourceWritePlatformServiceImpl(context, mock(CommandSourceRepository.class), new FromJsonHelper(),
                commandProcessingService, mock(SchedulerJobRunnerReadService.class), mock(ConfigurationDomainService.class), List.of());
        when(context.authenticatedUser(any(CommandWrapper.class))).thenReturn(authenticatedUser);
        when(authenticatedUser.getId()).thenReturn(OWN_USER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"password\":\"Abcdef1#2$3%XYZ\",\"repeatPassword\":\"Abcdef1#2$3%XYZ\"}",
            "{\"password\":\"Abcdef1#2$3%XYZ\"}" })
    void ownPasswordOnlyUpdateSkipsMakerChecker(String json) {
        CommandWrapper wrapper = new CommandWrapperBuilder().updateUser(OWN_USER_ID).withJson(json).build();

        underTest.logCommandSource(wrapper);

        verify(commandProcessingService).executeCommand(eq(wrapper), any(), eq(true));
        verify(authenticatedUser, never()).validateHasPermissionTo(anyString());
    }

    @Test
    void ownPasswordChangeOperationSkipsMakerChecker() {
        CommandWrapper wrapper = new CommandWrapperBuilder().changeUserPassword(OWN_USER_ID)
                .withJson("{\"password\":\"Abcdef1#2$3%XYZ\",\"repeatPassword\":\"Abcdef1#2$3%XYZ\"}").build();

        underTest.logCommandSource(wrapper);

        verify(commandProcessingService).executeCommand(eq(wrapper), any(), eq(true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"roles\":[\"1\"]}",
            "{\"password\":\"Abcdef1#2$3%XYZ\",\"repeatPassword\":\"Abcdef1#2$3%XYZ\",\"roles\":[\"1\"]}", "{\"officeId\":2}",
            "{\"staffId\":3}", "{\"firstname\":\"Mallory\"}", "{}" })
    void ownUserUpdateOfOtherFieldsIsNotPreApproved(String json) {
        CommandWrapper wrapper = new CommandWrapperBuilder().updateUser(OWN_USER_ID).withJson(json).build();

        underTest.logCommandSource(wrapper);

        verify(commandProcessingService).executeCommand(eq(wrapper), any(), eq(false));
    }

    @Test
    void updateOfOtherUserRequiresPermissionAndIsNotPreApproved() {
        CommandWrapper wrapper = new CommandWrapperBuilder().updateUser(OTHER_USER_ID).withJson("{\"password\":\"Abcdef1#2$3%XYZ\"}")
                .build();

        underTest.logCommandSource(wrapper);

        verify(authenticatedUser).validateHasPermissionTo("UPDATE_USER");
        verify(commandProcessingService).executeCommand(eq(wrapper), any(), eq(false));
    }

    @Test
    void updateOfOtherUserWithoutPermissionIsRejected() {
        CommandWrapper wrapper = new CommandWrapperBuilder().updateUser(OTHER_USER_ID).withJson("{\"roles\":[\"1\"]}").build();
        doThrow(new NoAuthorizationException("no permission")).when(authenticatedUser).validateHasPermissionTo("UPDATE_USER");

        assertThrows(NoAuthorizationException.class, () -> underTest.logCommandSource(wrapper));

        verify(commandProcessingService, never()).executeCommand(any(), any(), any(Boolean.class));
    }
}
