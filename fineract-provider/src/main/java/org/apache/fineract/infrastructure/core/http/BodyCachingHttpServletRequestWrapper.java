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
package org.apache.fineract.infrastructure.core.http;

import static java.nio.charset.StandardCharsets.UTF_8;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.apache.commons.io.IOUtils;

public class BodyCachingHttpServletRequestWrapper extends HttpServletRequestWrapper {

    public static final long DEFAULT_MAX_BODY_SIZE = 10L * 1024 * 1024;
    private static final long MAX_SUPPORTED_BODY_SIZE = Integer.MAX_VALUE - 8L;

    private final byte[] cachedBody;
    private ByteArrayInputStream inputStream;

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW")
    public BodyCachingHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_SIZE);
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW")
    public BodyCachingHttpServletRequestWrapper(HttpServletRequest request, long maxBodySize) throws IOException {
        super(request);
        this.cachedBody = readBody(request, Math.min(Math.max(maxBodySize, 0L), MAX_SUPPORTED_BODY_SIZE));
        this.inputStream = new ByteArrayInputStream(cachedBody);
    }

    public static BodyCachingHttpServletRequestWrapper of(HttpServletRequest request, long maxBodySize) throws IOException {
        if (request instanceof BodyCachingHttpServletRequestWrapper cachedRequest) {
            return cachedRequest;
        }
        return new BodyCachingHttpServletRequestWrapper(request, maxBodySize);
    }

    private static byte[] readBody(HttpServletRequest request, long maxBodySize) throws IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodySize) {
            throw new RequestBodyTooLargeException(maxBodySize);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream(contentLength > 0 ? (int) contentLength : 256);
        IOUtils.copyLarge(request.getInputStream(), body, 0, maxBodySize + 1);
        if (body.size() > maxBodySize) {
            throw new RequestBodyTooLargeException(maxBodySize);
        }
        return body.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CachedBodyServletInputStream(inputStream);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream, UTF_8));
    }

    public void resetStream() {
        inputStream = new ByteArrayInputStream(cachedBody);
    }

    public static class CachedBodyServletInputStream extends ServletInputStream {

        private final InputStream inputStream;

        public CachedBodyServletInputStream(ByteArrayInputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public boolean isFinished() {
            try {
                return inputStream.available() == 0;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }
    }
}
