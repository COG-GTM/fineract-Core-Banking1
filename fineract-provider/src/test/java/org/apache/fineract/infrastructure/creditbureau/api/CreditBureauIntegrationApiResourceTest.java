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
package org.apache.fineract.infrastructure.creditbureau.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.creditbureau.data.CreditReportData;
import org.apache.fineract.infrastructure.creditbureau.service.CreditReportReadPlatformService;
import org.apache.fineract.infrastructure.creditbureau.service.CreditReportWritePlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditBureauIntegrationApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private DefaultToApiJsonSerializer<CreditReportData> toApiJsonSerializer;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private CreditReportReadPlatformService creditReportReadPlatformService;

    private CreditBureauIntegrationApiResource underTest;

    @BeforeEach
    void setUp() {
        underTest = new CreditBureauIntegrationApiResource(context, mock(DefaultToApiJsonSerializer.class),
                mock(PortfolioCommandSourceWritePlatformService.class), apiRequestParameterHelper,
                mock(CreditReportWritePlatformService.class), creditReportReadPlatformService, toApiJsonSerializer);
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void getSavedCreditReportRejectsUserWithoutReadCreditReportPermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ creditreports")).when(user)
                .validateHasReadPermission("CREDITREPORT");

        assertThrows(NoAuthorizationException.class, () -> underTest.getSavedCreditReport(1L, mock(UriInfo.class)));

        verifyNoInteractions(creditReportReadPlatformService, toApiJsonSerializer);
    }

    @Test
    void getSavedCreditReportReturnsReportsForUserWithReadCreditReportPermission() {
        final List<CreditReportData> reports = List.of(CreditReportData.instance(1L, 1L, "NRC123"));
        final UriInfo uriInfo = mock(UriInfo.class);
        final ApiRequestJsonSerializationSettings settings = mock(ApiRequestJsonSerializationSettings.class);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        when(apiRequestParameterHelper.process(any())).thenReturn(settings);
        when(creditReportReadPlatformService.retrieveCreditReportDetails(1L)).thenReturn(reports);
        when(toApiJsonSerializer.serialize(any(ApiRequestJsonSerializationSettings.class), anyCollection(), anySet())).thenReturn("[]");

        assertEquals("[]", underTest.getSavedCreditReport(1L, uriInfo));

        verify(user).validateHasReadPermission("CREDITREPORT");
        verify(creditReportReadPlatformService).retrieveCreditReportDetails(1L);
    }
}
