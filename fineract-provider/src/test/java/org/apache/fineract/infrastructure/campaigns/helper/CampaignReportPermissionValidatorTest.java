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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CampaignReportPermissionValidatorTest {

    private AppUser user;
    private JdbcTemplate jdbcTemplate;
    private CampaignReportPermissionValidator validator;

    @BeforeEach
    void setUp() {
        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        user = mock(AppUser.class);
        when(context.authenticatedUser()).thenReturn(user);
        jdbcTemplate = mock(JdbcTemplate.class);
        validator = new CampaignReportPermissionValidator(context, jdbcTemplate);
    }

    @Test
    void allowsReportUserIsPermittedToRun() {
        when(user.hasNotPermissionForReport("Active Clients")).thenReturn(false);
        mockReportType("Active Clients", "SMS");
        assertDoesNotThrow(() -> validator.validateReportPermission("{\"reportName\":\"Active Clients\",\"officeId\":\"1\"}"));
    }

    @Test
    void rejectsReportUserIsNotPermittedToRun() {
        when(user.hasNotPermissionForReport("Client Listing")).thenReturn(true);
        assertThrows(NoAuthorizationException.class,
                () -> validator.validateReportPermission("{\"reportName\":\"Client Listing\",\"officeId\":\"1\"}"));
    }

    @Test
    void rejectsPermittedReportThatIsNotACampaignReport() {
        when(user.hasNotPermissionForReport("Client Listing")).thenReturn(false);
        mockReportType("Client Listing", "Table");
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> validator.validateReportPermission("{\"reportName\":\"Client Listing\",\"officeId\":\"1\"}"));
    }

    @Test
    void rejectsMalformedParamValue() {
        GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> validator.validateReportPermission("not json"));
        assertInstanceOf(JsonProcessingException.class, exception.getCause());
        assertThrows(GeneralPlatformDomainRuleException.class, () -> validator.validateReportPermission("[\"reportName\"]"));
    }

    private void mockReportType(final String reportName, final String reportType) {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(reportName))).thenReturn(List.of(reportType));
    }
}
