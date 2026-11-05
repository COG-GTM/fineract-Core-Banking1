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
package org.apache.fineract.portfolio.repaymentwithpostdatedchecks.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.data.PostDatedChecksData;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.service.RepaymentWithPostDatedChecksReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepaymentWithPostDatedChecksApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private FromJsonHelper fromJsonHelper;
    @Mock
    private DefaultToApiJsonSerializer<PostDatedChecksData> apiJsonSerializer;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private RepaymentWithPostDatedChecksReadPlatformService readPlatformService;
    @Mock
    private AppUser user;

    private RepaymentWithPostDatedChecksApiResource underTest;

    @BeforeEach
    void setUp() {
        underTest = new RepaymentWithPostDatedChecksApiResource(context, fromJsonHelper, apiJsonSerializer,
                commandsSourceWritePlatformService, readPlatformService);
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void getPostDatedChecksRequiresLoanReadPermission() {
        doThrow(new NoAuthorizationException("no READ_LOAN")).when(user).validateHasReadPermission("LOAN");

        assertThrows(NoAuthorizationException.class, () -> underTest.getPostDatedChecks(41L));
        verifyNoInteractions(readPlatformService);
    }

    @Test
    void getPostDatedCheckRequiresLoanReadPermission() {
        doThrow(new NoAuthorizationException("no READ_LOAN")).when(user).validateHasReadPermission("LOAN");

        assertThrows(NoAuthorizationException.class, () -> underTest.getPostDatedCheck(1, 41L));
        verifyNoInteractions(readPlatformService);
    }

    @Test
    void getPostDatedChecksReturnsDataWithLoanReadPermission() {
        underTest.getPostDatedChecks(41L);

        verify(user).validateHasReadPermission("LOAN");
        verify(readPlatformService).getPostDatedChecks(anyLong());
        verify(apiJsonSerializer).serialize(any(Object.class));
    }
}
