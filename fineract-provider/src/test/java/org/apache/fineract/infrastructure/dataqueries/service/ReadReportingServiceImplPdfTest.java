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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseType;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.springframework.jdbc.core.JdbcTemplate;

class ReadReportingServiceImplPdfTest {

    private static final String REPORT_NAME = "Client Listing";

    private final ReadReportingServiceImpl service = new ReadReportingServiceImpl(mock(JdbcTemplate.class),
            mock(PlatformSecurityContext.class), mock(GenericDataService.class), mock(SqlInjectionPreventerService.class),
            mock(DatabaseSpecificSQLGenerator.class)) {

        @Override
        public GenericResultsetData retrieveGenericResultset(String name, String type, Map<String, String> queryParams,
                boolean isSelfServiceUserReport) {
            List<ResultsetColumnHeaderData> headers = List.of(ResultsetColumnHeaderData.basic("client", "varchar", DatabaseType.MYSQL));
            List<ResultsetRowData> rows = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                rows.add(ResultsetRowData.create(List.of(queryParams.get("${officeId}") + "-client-" + i)));
            }
            return new GenericResultsetData(headers, rows);
        }
    };

    @Test
    void retrieveReportPdfReturnsPdfBytesForReportNamesWithSpaces() throws Exception {
        byte[] pdf = service.retrieveReportPDF(REPORT_NAME, "report", Map.of("${officeId}", "office1"), false);

        assertTrue(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
        assertTrue(pageText(pdf).contains("office1-client-0"));
    }

    @Test
    void concurrentPdfExportsOfSameReportDoNotShareContent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<byte[]>> alice = new ArrayList<>();
            List<Future<byte[]>> mallory = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                alice.add(executor.submit(export("alice")));
                mallory.add(executor.submit(export("mallory")));
            }
            for (Future<byte[]> result : alice) {
                String text = pageText(result.get());
                assertTrue(text.contains("alice-client-0"));
                assertFalse(text.contains("mallory-client"));
            }
            for (Future<byte[]> result : mallory) {
                String text = pageText(result.get());
                assertTrue(text.contains("mallory-client-0"));
                assertFalse(text.contains("alice-client"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<byte[]> export(String office) {
        return () -> service.retrieveReportPDF(REPORT_NAME, "report", Map.of("${officeId}", office), false);
    }

    private static String pageText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new String(reader.getPageContent(1), StandardCharsets.ISO_8859_1);
        } finally {
            reader.close();
        }
    }
}
