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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.creditbureau.data.CreditReportData;
import org.apache.fineract.infrastructure.creditbureau.service.CreditReportWritePlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditBureauIntegrationApiResourceTest {

    private static final Long CREDIT_BUREAU_ID = 1L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private CreditReportWritePlatformService creditReportWritePlatformService;
    @Mock
    private DefaultToApiJsonSerializer<CreditReportData> toCreditReportApiJsonSerializer;
    @Mock
    private File creditReport;
    @Mock
    private FormDataContentDisposition fileDetail;

    @InjectMocks
    private CreditBureauIntegrationApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void addCreditReportRejectsUserWithoutUploadPermission() {
        doThrow(new NoAuthorizationException("User has no authority to: UPLOAD_CREDITREPORT")).when(user)
                .validateHasPermissionTo(CreditBureauIntegrationApiResource.UPLOAD_CREDIT_REPORT_PERMISSION);

        assertThrows(NoAuthorizationException.class,
                () -> resource.addCreditReport(creditReport, null, null, fileDetail, CREDIT_BUREAU_ID));

        verify(creditReportWritePlatformService, never()).addCreditReport(any(), any(), any());
    }

    @Test
    void addCreditReportUploadsWhenUserHasUploadPermission() {
        when(creditReportWritePlatformService.addCreditReport(CREDIT_BUREAU_ID, creditReport, fileDetail)).thenReturn("uploaded");
        when(toCreditReportApiJsonSerializer.serialize("uploaded")).thenReturn("\"uploaded\"");

        assertEquals("\"uploaded\"", resource.addCreditReport(creditReport, null, null, fileDetail, CREDIT_BUREAU_ID));

        InOrder order = inOrder(user, creditReportWritePlatformService);
        order.verify(user).validateHasPermissionTo("UPLOAD_CREDITREPORT");
        order.verify(creditReportWritePlatformService).addCreditReport(CREDIT_BUREAU_ID, creditReport, fileDetail);
    }
}
