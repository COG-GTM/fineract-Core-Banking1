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
package org.apache.fineract.portfolio.group.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CenterReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ConfigurationDomainService configurationDomainService;

    @InjectMocks
    private CenterReadPlatformServiceImpl centerReadPlatformService;

    @Test
    void retriveAllCentersByMeetingDateRestrictsToCallerOfficeHierarchy() {
        final AppUser currentUser = mock(AppUser.class);
        final Office userOffice = mock(Office.class);
        when(context.authenticatedUser()).thenReturn(currentUser);
        when(currentUser.getOffice()).thenReturn(userOffice);
        when(userOffice.getHierarchy()).thenReturn(".1.3.");

        final Long requestedOfficeId = 5L;
        final Long staffId = 7L;
        centerReadPlatformService.retriveAllCentersByMeetingDate(requestedOfficeId, LocalDate.of(2026, 11, 5), staffId);

        final List<Invocation> queries = mockingDetails(jdbcTemplate).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("query")).toList();
        assertEquals(1, queries.size());
        final Object[] rawArguments = queries.get(0).getRawArguments();
        final String sql = (String) rawArguments[0];
        final Object[] params = (Object[]) rawArguments[2];

        assertTrue(sql.contains("join m_office o on o.id = g.office_id"), sql);
        assertTrue(sql.contains("where g.office_id=? and o.hierarchy like ?"), sql);
        assertEquals(sql.chars().filter(c -> c == '?').count(), params.length);
        assertEquals(requestedOfficeId, params[6]);
        assertEquals(".1.3.%", params[7]);
        assertEquals(staffId, params[8]);
    }
}
