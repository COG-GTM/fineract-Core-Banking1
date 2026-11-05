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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.security.exception.SqlValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

class ReadReportingServiceImplCampaignTest {

    private static final String REPORT_NAME = "Active Clients";
    private static final String REPORT_SQL = "select c.id from m_client c where c.office_id = ${officeId}"
            + " and c.staff_id = ${loanOfficerId}";

    private JdbcTemplate jdbcTemplate;
    private GenericDataService genericDataService;
    private SqlValidator sqlValidator;
    private ReadReportingServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        genericDataService = mock(GenericDataService.class);
        sqlValidator = mock(SqlValidator.class);
        service = new ReadReportingServiceImpl(jdbcTemplate, mock(PlatformSecurityContext.class), genericDataService,
                mock(SqlInjectionPreventerService.class), mock(DatabaseSpecificSQLGenerator.class), mock(FineractProperties.class),
                sqlValidator);

        when(genericDataService.wrapSQL(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(genericDataService.replace(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> StringUtils.replace(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(genericDataService.fillGenericResultSet(anyString())).thenReturn(mock(GenericResultsetData.class));

        final SqlRowSet rowSet = mock(SqlRowSet.class);
        when(rowSet.next()).thenReturn(true);
        when(rowSet.getString("the_sql")).thenReturn(REPORT_SQL);
        when(jdbcTemplate.queryForRowSet(anyString(), any(Object[].class))).thenReturn(rowSet);
        mockReportType("SMS");
    }

    private void mockReportType(final String reportType) {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of(reportType));
    }

    private Map<String, String> params(final String officeId) {
        final Map<String, String> params = new LinkedHashMap<>();
        params.put("reportName", REPORT_NAME);
        params.put("officeId", officeId);
        params.put("loanOfficerId", "-1");
        return params;
    }

    @Test
    void substitutesValidCampaignParameters() {
        service.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report", params("1"));

        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(genericDataService).fillGenericResultSet(sql.capture());
        assertEquals("select c.id from m_client c where c.office_id = 1 and c.staff_id = -1", sql.getValue());
        verify(sqlValidator).validate("1");
        verify(sqlValidator).validate("-1");
    }

    @Test
    void rejectsSqlInjectionInParameterValue() {
        assertThrows(SqlValidationException.class,
                () -> service.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report", params("1 or 1=1")));
        assertThrows(SqlValidationException.class,
                () -> service.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report", params("1' union select 1")));
        assertThrows(SqlValidationException.class,
                () -> service.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report", params("1--")));
        verify(genericDataService, never()).fillGenericResultSet(anyString());
    }

    @Test
    void propagatesSqlValidatorRejection() {
        doThrow(new SqlValidationException("blocked")).when(sqlValidator).validate("union");
        assertThrows(SqlValidationException.class,
                () -> service.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report", params("union")));
        verify(genericDataService, never()).fillGenericResultSet(anyString());
    }

    @Test
    void rejectsUnsafeParameterName() {
        final Map<String, String> params = params("1");
        params.put("officeId} or 1=1 or ${x", "1");
        assertThrows(SqlValidationException.class,
                () -> service.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report", params));
        verify(genericDataService, never()).fillGenericResultSet(anyString());
    }

    @Test
    void rejectsNonCampaignReport() {
        mockReportType("Table");
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.retrieveGenericResultSetForSmsEmailCampaign("Client Listing", "report", params("1")));
        verify(genericDataService, never()).fillGenericResultSet(anyString());
    }

    @Test
    void allowsEmailCampaignReport() {
        mockReportType("Email");
        service.retrieveGenericResultSetForSmsEmailCampaign(REPORT_NAME, "report", params("1"));
        verify(genericDataService).fillGenericResultSet(anyString());
    }
}
