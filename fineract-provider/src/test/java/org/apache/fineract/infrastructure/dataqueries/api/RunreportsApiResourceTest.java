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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.MultivaluedMap;
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

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final ReadReportingService readReportingService = mock(ReadReportingService.class);
    private final ReportingProcessServiceProvider provider = mock(ReportingProcessServiceProvider.class);
    private final ReportingProcessService processService = mock(ReportingProcessService.class);
    private final AppUser user = mock(AppUser.class);
    private final UriInfo uriInfo = mock(UriInfo.class);
    private RunreportsApiResource resource;

    @BeforeEach
    void setUp() {
        resource = new RunreportsApiResource(context, readReportingService, provider);
        when(context.authenticatedUser()).thenReturn(user);
        when(readReportingService.getReportType(anyString(), anyBoolean(), anyBoolean())).thenReturn("Table");
        when(provider.findReportingProcessService("Table")).thenReturn(processService);
        MultivaluedMap<String, String> params = new MultivaluedStringMap();
        params.putSingle("parameterType", "true");
        params.putSingle("R_officeId", "1");
        when(uriInfo.getQueryParameters()).thenReturn(params);
    }

    @Test
    void parameterLookupRequiresPermissionForAnAttachedReport() {
        when(readReportingService.retrieveReportNamesUsingParameter("loanOfficerIdSelectAll", false)).thenReturn(List.of("Active Loans"));
        when(user.hasNotPermissionForReport("Active Loans")).thenReturn(true);

        assertThatThrownBy(() -> resource.runReport("loanOfficerIdSelectAll", uriInfo, false)).isInstanceOf(NoAuthorizationException.class);
        verify(processService, never()).processRequest(anyString(), any());
    }

    @Test
    void parameterLookupAllowedWhenUserCanRunAnAttachedReport() {
        when(readReportingService.retrieveReportNamesUsingParameter("loanOfficerIdSelectAll", false)).thenReturn(List.of("A", "B"));
        when(user.hasNotPermissionForReport("A")).thenReturn(true);
        when(user.hasNotPermissionForReport("B")).thenReturn(false);

        resource.runReport("loanOfficerIdSelectAll", uriInfo, false);

        verify(processService).processRequest(eq("loanOfficerIdSelectAll"), any());
    }

    @Test
    void selfServiceParameterLookupRestrictedToSelfServiceReports() {
        when(readReportingService.isSpecialParameter("FullParameterList")).thenReturn(true);
        when(readReportingService.retrieveReportNamesUsingParameter(anyString(), eq(true))).thenReturn(List.of());

        assertThatThrownBy(() -> resource.runReport("FullParameterList", uriInfo, true)).isInstanceOf(NoAuthorizationException.class);
        assertThatThrownBy(() -> resource.runReport("loanOfficerIdSelectAll", uriInfo, true)).isInstanceOf(NoAuthorizationException.class);
        verify(processService, never()).processRequest(anyString(), any());
    }

    @Test
    void specialParameterAllowedForStaff() {
        when(readReportingService.isSpecialParameter("FullParameterList")).thenReturn(true);

        resource.runReport("FullParameterList", uriInfo, false);

        verify(processService).processRequest(eq("FullParameterList"), any());
    }
}
