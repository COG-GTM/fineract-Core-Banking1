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
package org.apache.fineract.infrastructure.dataqueries.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

class ReadReportingServiceImplTest {

    private static final String REPORT_NAME = "Client Listing";
    private static final String REPORT_SQL = "select * from m_office ounder"
            + " where ounder.hierarchy like concat('${currentUserHierarchy}', '%')"
            + " and ounder.id = ${officeId} and ${currentUserId} = ${currentUserId} and '${isSelfServiceUser}' = 'false'"
            + " and d = ${currentDate}";

    private GenericDataService genericDataService;
    private ReadReportingServiceImpl service;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SqlRowSet rowSet = mock(SqlRowSet.class);
        when(rowSet.next()).thenReturn(true);
        when(rowSet.getString("the_sql")).thenReturn(REPORT_SQL);
        when(jdbcTemplate.queryForRowSet(anyString(), eq(REPORT_NAME))).thenReturn(rowSet);

        genericDataService = mock(GenericDataService.class);
        when(genericDataService.replace(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> StringUtils.replace(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(genericDataService.wrapSQL(anyString())).thenAnswer(inv -> inv.getArgument(0));

        SqlInjectionPreventerService sqlInjectionPreventerService = mock(SqlInjectionPreventerService.class);
        when(sqlInjectionPreventerService.quoteIdentifier(anyString())).thenAnswer(inv -> inv.getArgument(0));

        DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        when(sqlGenerator.currentBusinessDate()).thenReturn("'2024-01-01'");
        when(sqlGenerator.currentTenantDateTime()).thenReturn("'2024-01-01 00:00:00'");

        Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(".1.2.");
        AppUser user = mock(AppUser.class);
        when(user.getOffice()).thenReturn(office);
        when(user.getId()).thenReturn(5L);
        PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        when(context.authenticatedUser()).thenReturn(user);

        service = new ReadReportingServiceImpl(jdbcTemplate, context, genericDataService, sqlInjectionPreventerService, sqlGenerator,
                mock(FineractProperties.class));
    }

    @Test
    void sessionPlaceholdersCannotBeOverriddenByQueryParameters() {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("${currentUserHierarchy}", ".");
        queryParams.put("${currentUserId}", "1");
        queryParams.put("${isSelfServiceUser}", "true");
        queryParams.put("${currentDate}", "'1999-01-01'");
        queryParams.put("${officeId}", "1");

        service.retrieveGenericResultset(REPORT_NAME, "report", queryParams, false);

        assertEquals("select * from m_office ounder where ounder.hierarchy like concat('.1.2.', '%') and ounder.id = 1 and 5 = 5"
                + " and 'false' = 'false' and d = '2024-01-01'", capturedSql());
    }

    @Test
    void sessionPlaceholdersAreFilledFromAuthenticatedUser() {
        service.retrieveGenericResultset(REPORT_NAME, "report", Map.of("${officeId}", "2"), true);

        assertEquals("select * from m_office ounder where ounder.hierarchy like concat('.1.2.', '%') and ounder.id = 2 and 5 = 5"
                + " and 'true' = 'false' and d = '2024-01-01'", capturedSql());
    }

    private String capturedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(genericDataService).fillGenericResultSet(sql.capture());
        return sql.getValue();
    }
}
