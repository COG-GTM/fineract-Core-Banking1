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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects bulk import uploads whose declared Content-Length exceeds the configured limit before the multipart body is
 * read. Requests without a Content-Length are bounded while being read in {@code BulkImportWorkbookServiceImpl}.
 */
@RequiredArgsConstructor
public class BulkImportUploadSizeFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE = "error.msg.bulkimport.file.too.large";
    // Room for the multipart boundaries and the small locale/dateFormat form fields.
    static final long MULTIPART_OVERHEAD_BYTES = 64 * 1024L;
    private static final String UPLOAD_PATH_SUFFIX = "/uploadtemplate";

    private final FineractProperties fineractProperties;

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        return !HttpMethod.POST.matches(request.getMethod()) || uri == null || !uri.endsWith(UPLOAD_PATH_SUFFIX);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        final long maxFileSize = fineractProperties.getContent().getBulkImportMaxFileSize().toBytes();
        if (request.getContentLengthLong() > maxFileSize + MULTIPART_OVERHEAD_BYTES) {
            final String message = "Uploaded file exceeds the maximum allowed size of " + maxFileSize + " bytes.";
            final int status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(ApiGlobalErrorResponse.create(status, ERROR_CODE, message, message).toJson());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
