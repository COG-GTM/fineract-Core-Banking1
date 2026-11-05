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
package org.apache.fineract.infrastructure.campaigns.email.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.campaigns.email.data.EmailData;
import org.apache.fineract.infrastructure.campaigns.email.service.EmailReadPlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailApiResourceTest {

    private static final Long EMAIL_ID = 1L;

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private AppUser appUser;

    @Mock
    private EmailReadPlatformService readPlatformService;

    @Mock
    private DefaultToApiJsonSerializer<EmailData> toApiJsonSerializer;

    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;

    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Mock
    private SqlValidator sqlValidator;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private ApiRequestJsonSerializationSettings settings;

    private EmailApiResource emailApiResource;

    @BeforeEach
    void setUp() {
        emailApiResource = new EmailApiResource(context, readPlatformService, toApiJsonSerializer, apiRequestParameterHelper,
                commandsSourceWritePlatformService, sqlValidator);
        when(context.authenticatedUser()).thenReturn(appUser);
    }

    @Test
    void retrieveOneWithoutReadEmailPermissionIsRejectedBeforeReading() {
        doThrow(new NoAuthorizationException("User has no authority to: READ_EMAIL")).when(appUser).validateHasReadPermission("Email");

        assertThrows(NoAuthorizationException.class, () -> emailApiResource.retrieveOne(EMAIL_ID, uriInfo));

        verifyNoInteractions(readPlatformService, toApiJsonSerializer);
    }

    @Test
    void retrieveOneWithReadEmailPermissionReturnsEmail() {
        final EmailData emailData = new EmailData();
        when(readPlatformService.retrieveOne(EMAIL_ID)).thenReturn(emailData);
        when(apiRequestParameterHelper.process(any())).thenReturn(settings);
        when(toApiJsonSerializer.serialize(settings, emailData)).thenReturn("{\"id\":1}");

        assertEquals("{\"id\":1}", emailApiResource.retrieveOne(EMAIL_ID, uriInfo));

        final InOrder order = inOrder(appUser, readPlatformService);
        order.verify(appUser).validateHasReadPermission("Email");
        order.verify(readPlatformService).retrieveOne(EMAIL_ID);
    }
}
