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
package org.apache.fineract.infrastructure.sms.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.sms.data.SmsData;
import org.apache.fineract.infrastructure.sms.service.SmsReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmsApiResourceTest {

    private static final Long SMS_ID = 1L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private SmsReadPlatformService readPlatformService;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private DefaultToApiJsonSerializer<String> apiJsonSerializer;
    @Mock
    private AppUser user;

    private final SmsData smsData = new SmsData().setId(SMS_ID);

    private SmsApiResource underTest;

    @BeforeEach
    void setUp() {
        underTest = new SmsApiResource(context, readPlatformService, commandsSourceWritePlatformService, apiJsonSerializer);
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void retrieveOneRejectsUserWithoutReadSmsPermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ smss")).when(user).validateHasReadPermission("SMS");

        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveOne(SMS_ID));
        verifyNoInteractions(readPlatformService);
    }

    @Test
    void retrieveOneReturnsSmsForUserWithReadSmsPermission() {
        when(readPlatformService.retrieveOne(SMS_ID)).thenReturn(smsData);

        assertSame(smsData, underTest.retrieveOne(SMS_ID));
        InOrder order = inOrder(user, readPlatformService);
        order.verify(user).validateHasReadPermission("SMS");
        order.verify(readPlatformService).retrieveOne(SMS_ID);
    }
}
