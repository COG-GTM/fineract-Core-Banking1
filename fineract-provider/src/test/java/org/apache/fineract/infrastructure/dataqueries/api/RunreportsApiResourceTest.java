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
package org.apache.fineract.infrastructure.dataqueries.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.apache.fineract.infrastructure.report.provider.ReportingProcessServiceProvider;
import org.apache.fineract.infrastructure.report.service.ReportingProcessService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunreportsApiResourceTest {

    private static final String PARAMETER = "loanOfficerIdSelectAll";

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final ReadReportingService readReportingService = mock(ReadReportingService.class);
    private final ReportingProcessServiceProvider provider = mock(ReportingProcessServiceProvider.class);
    private final ReportingProcessService processService = mock(ReportingProcessService.class);
    private final AppUser user = mock(AppUser.class);
    private final UriInfo uriInfo = mock(UriInfo.class);
    private final Response ok = mock(Response.class);
    private final RunreportsApiResource resource = new RunreportsApiResource(context, readReportingService, provider);

    @BeforeEach
    void setUp() {
        MultivaluedStringMap queryParams = new MultivaluedStringMap();
        queryParams.putSingle("parameterType", "true");
        queryParams.putSingle("R_officeId", "1");
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(context.authenticatedUser()).thenReturn(user);
        when(user.hasNotPermissionForReport(anyString())).thenReturn(true);
        when(readReportingService.getReportType(anyString(), anyBoolean(), anyBoolean())).thenReturn("Table");
        when(provider.findReportingProcessService("Table")).thenReturn(processService);
        when(processService.processRequest(anyString(), any())).thenReturn(ok);
    }

    @Test
    void parameterLookupRequiresPermissionForAReportUsingTheParameter() {
        when(readReportingService.retrieveReportNamesUsingParameter(PARAMETER, false)).thenReturn(List.of("Active Loans - Summary"));

        assertThrows(NoAuthorizationException.class, () -> resource.runReport(PARAMETER, uriInfo, false));
        verify(processService, never()).processRequest(anyString(), any());

        when(user.hasNotPermissionForReport("Active Loans - Summary")).thenReturn(false);
        assertSame(ok, resource.runReport(PARAMETER, uriInfo, false));
    }

    @Test
    void selfServiceParameterLookupOnlyForSelfServiceReports() {
        when(readReportingService.retrieveReportNamesUsingParameter(PARAMETER, true)).thenReturn(List.of());

        assertThrows(NoAuthorizationException.class, () -> resource.runReport(PARAMETER, uriInfo, true));
        verify(processService, never()).processRequest(anyString(), any());
    }

    @Test
    void standaloneParameterLookupStaysAvailableToStaff() {
        when(readReportingService.retrieveReportNamesUsingParameter("FullReportList", false)).thenReturn(List.of());

        assertSame(ok, resource.runReport("FullReportList", uriInfo, false));
    }

    @Test
    void reportStillRequiresReportPermission() {
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedStringMap());

        assertThrows(NoAuthorizationException.class, () -> resource.runReport("Client Listing", uriInfo, false));
        verify(readReportingService, never()).retrieveReportNamesUsingParameter(anyString(), anyBoolean());
    }
}
