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
package org.apache.fineract.infrastructure.dataqueries.service.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PdfDatatableReportExportServiceTest {

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void exportReturnsPdfBytesAndFileNameWithoutServerPath() {
        byte[] pdf = "%PDF-1.4 report".getBytes(StandardCharsets.ISO_8859_1);
        ReadReportingService readReportingService = mock(ReadReportingService.class);
        when(readReportingService.retrieveReportPDF(eq("Client Listing"), eq("report"), anyMap(), eq(false))).thenReturn(pdf);

        ResponseHolder response = new PdfDatatableReportExportService(readReportingService).export("Client Listing",
                new MultivaluedStringMap(), Map.of(), false, "report");

        assertEquals(Response.Status.OK, response.status());
        assertEquals("application/pdf", response.contentType());
        assertArrayEquals(pdf, (byte[]) response.entity());
        assertEquals(1, response.headers().size());
        assertEquals("Content-Disposition", response.headers().get(0).getKey());
        String disposition = response.headers().get(0).getValue();
        assertTrue(disposition.matches("attachment; filename=\"Client_Listing\\(\\)_\\d{14}\\.pdf\""), disposition);
        assertFalse(disposition.contains("/"));
        assertFalse(disposition.contains("\\"));
    }
}
