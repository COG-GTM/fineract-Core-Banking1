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
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.apache.fineract.infrastructure.core.exception.PlatformPayloadTooLargeException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects bulk import uploads whose declared Content-Length exceeds the configured limit before the multipart body is
 * read. Requests without a Content-Length get a size-limited input stream instead.
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
        if (request.getContentLengthLong() < 0) {
            filterChain.doFilter(new SizeLimitedRequest(request, maxFileSize + MULTIPART_OVERHEAD_BYTES), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Caps the body of requests without a Content-Length (chunked) so the multipart reader cannot consume an unbounded body.
     */
    static final class SizeLimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream limitedStream;

        SizeLimitedRequest(final HttpServletRequest request, final long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedStream == null) {
                limitedStream = new SizeLimitedInputStream(super.getInputStream(), limit);
            }
            return limitedStream;
        }
    }

    static final class SizeLimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        SizeLimitedInputStream(final ServletInputStream delegate, final long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            final int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int n = delegate.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(final int n) throws IOException {
            count += n;
            if (count > limit) {
                final String message = "Request body exceeds the maximum allowed size of " + limit + " bytes.";
                throw new PlatformPayloadTooLargeException(ERROR_CODE, message);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
