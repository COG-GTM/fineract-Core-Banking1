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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.dataqueries.exception.ReportNotFoundException;
import org.apache.fineract.infrastructure.security.exception.SqlValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

@ExtendWith(MockitoExtension.class)
class ReadReportingServiceImplCampaignTest {

    private static final String REPORT_NAME = "Active Clients";
    private static final String REPORT_SQL = "select c.id from m_client c where c.office_id = '${officeId}'"
            + " and c.staff_id = ${loanOfficerId}";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private GenericDataService genericDataService;
    @Mock
    private SqlInjectionPreventerService sqlInjectionPreventerService;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private FineractProperties fineractProperties;
    @Mock
    private SqlRowSet campaignReportRow;

    private ReadReportingServiceImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new ReadReportingServiceImpl(jdbcTemplate, context, genericDataService, sqlInjectionPreventerService, sqlGenerator,
                fineractProperties);
        lenient().when(genericDataService.replace(anyString(), anyString(), anyString()))
                .thenAnswer(i -> i.<String>getArgument(0).replace(i.<String>getArgument(1), i.<String>getArgument(2)));
        lenient().when(genericDataService.wrapSQL(anyString())).thenAnswer(i -> "select x.* from (" + i.getArgument(0) + ") x");
    }

    private void givenCampaignReport(final String name, final String sql) {
        when(jdbcTemplate.queryForRowSet(contains("report_type IN (?, ?)"), eq(name), eq("SMS"), eq("Email")))
                .thenReturn(campaignReportRow);
        when(campaignReportRow.next()).thenReturn(sql != null);
        lenient().when(campaignReportRow.getString("the_sql")).thenReturn(sql);
    }

    @Test
    void substitutesValidCampaignParameters() {
        givenCampaignReport(REPORT_NAME, REPORT_SQL);

        underTest.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report",
                Map.of("reportName", REPORT_NAME, "officeId", "1", "loanOfficerId", "-1"));

        verify(genericDataService)
                .fillGenericResultSet("select x.* from (select c.id from m_client c where c.office_id = '1' and c.staff_id = -1) x");
    }

    @Test
    void rejectsReportsThatAreNotSmsOrEmailCampaignReports() {
        givenCampaignReport("Client Listing", null);

        assertThrows(ReportNotFoundException.class, () -> underTest.retrieveGenericResultSetForSmsEmailCampaign("Client Listing", "report",
                Map.of("officeId", "1", "currentUserHierarchy", ".")));
        verify(genericDataService, never()).fillGenericResultSet(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "1' or 1=1 --", "1) x union select username, password from m_appuser --", "-1 or 1=1",
            "cast((select password from m_appuser limit 1) as int)", "coalesce(c.staff_id,-10)", "1; drop table m_client", "" })
    void rejectsInjectionLikeParameterValues(final String value) {
        givenCampaignReport(REPORT_NAME, REPORT_SQL);

        assertThrows(SqlValidationException.class, () -> underTest.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report",
                Map.of("officeId", "1", "loanOfficerId", value)));
        verify(genericDataService, never()).fillGenericResultSet(anyString());
    }
}
