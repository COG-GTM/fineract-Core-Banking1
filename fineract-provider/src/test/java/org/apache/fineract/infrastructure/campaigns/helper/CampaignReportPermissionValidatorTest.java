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
package org.apache.fineract.infrastructure.campaigns.helper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CampaignReportPermissionValidatorTest {

    private AppUser user;
    private CampaignReportPermissionValidator validator;

    @BeforeEach
    void setUp() {
        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        user = mock(AppUser.class);
        when(context.authenticatedUser()).thenReturn(user);
        validator = new CampaignReportPermissionValidator(context);
    }

    @Test
    void allowsReportUserIsPermittedToRun() {
        when(user.hasNotPermissionForReport("Active Clients")).thenReturn(false);
        assertDoesNotThrow(() -> validator.validateReportPermission("{\"reportName\":\"Active Clients\",\"officeId\":\"1\"}"));
    }

    @Test
    void rejectsReportUserIsNotPermittedToRun() {
        when(user.hasNotPermissionForReport("Client Listing")).thenReturn(true);
        assertThrows(NoAuthorizationException.class,
                () -> validator.validateReportPermission("{\"reportName\":\"Client Listing\",\"officeId\":\"1\"}"));
    }

    @Test
    void rejectsMalformedParamValue() {
        assertThrows(GeneralPlatformDomainRuleException.class, () -> validator.validateReportPermission("not json"));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> validator.validateReportPermission("[\"reportName\"]"));
    }
}
