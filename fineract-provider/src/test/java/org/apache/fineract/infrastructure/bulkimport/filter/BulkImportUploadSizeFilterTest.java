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
package org.apache.fineract.infrastructure.bulkimport.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.exception.PlatformPayloadTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

class BulkImportUploadSizeFilterTest {

    private static final long MAX_FILE_SIZE = DataSize.ofKilobytes(100).toBytes();

    private BulkImportUploadSizeFilter underTest;

    @BeforeEach
    void setUp() {
        FineractProperties.FineractContentProperties content = new FineractProperties.FineractContentProperties();
        content.setBulkImportMaxFileSize(DataSize.ofBytes(MAX_FILE_SIZE));
        FineractProperties properties = new FineractProperties();
        properties.setContent(content);
        underTest = new BulkImportUploadSizeFilter(properties);
    }

    @Test
    void rejectsUploadWithDeclaredLengthAboveLimit() throws Exception {
        MockHttpServletRequest request = uploadRequest("/fineract-provider/api/v1/clients/uploadtemplate");
        request.setContent(new byte[(int) (MAX_FILE_SIZE + BulkImportUploadSizeFilter.MULTIPART_OVERHEAD_BYTES + 1)]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        underTest.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.getStatus());
        assertTrue(response.getContentAsString().contains(BulkImportUploadSizeFilter.ERROR_CODE));
        assertNull(chain.getRequest());
    }

    @Test
    void allowsUploadWithinLimit() throws Exception {
        MockHttpServletRequest request = uploadRequest("/fineract-provider/api/v1/users/uploadtemplate");
        request.setContent(new byte[(int) MAX_FILE_SIZE]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        underTest.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void ignoresOtherEndpoints() throws Exception {
        MockHttpServletRequest request = uploadRequest("/fineract-provider/api/v1/clients/1/documents");
        request.setContent(new byte[(int) (MAX_FILE_SIZE * 2)]);
        MockFilterChain chain = new MockFilterChain();

        underTest.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void capsBodyOfUploadWithoutContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/fineract-provider/api/v1/clients/uploadtemplate") {

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContentType("multipart/form-data; boundary=xyz");
        request.setContent(new byte[(int) (MAX_FILE_SIZE + BulkImportUploadSizeFilter.MULTIPART_OVERHEAD_BYTES + 1)]);
        MockFilterChain chain = new MockFilterChain();

        underTest.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        InputStream body = chain.getRequest().getInputStream();
        assertThrows(PlatformPayloadTooLargeException.class, () -> body.transferTo(OutputStream.nullOutputStream()));
    }

    private static MockHttpServletRequest uploadRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("multipart/form-data; boundary=xyz");
        return request;
    }
}
