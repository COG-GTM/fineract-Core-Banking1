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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BodyCachingHttpServletRequestWrapperTest {

    @Test
    void cachesBodyWithinLimitAndAllowsRereading() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/batches");
        request.setContent("[{\"requestId\":1}]".getBytes(UTF_8));

        BodyCachingHttpServletRequestWrapper wrapper = new BodyCachingHttpServletRequestWrapper(request, 1024);

        assertEquals("[{\"requestId\":1}]", IOUtils.toString(wrapper.getInputStream(), UTF_8));
        wrapper.resetStream();
        assertEquals("[{\"requestId\":1}]", IOUtils.toString(wrapper.getInputStream(), UTF_8));
        assertEquals("[{\"requestId\":1}]", IOUtils.toString(wrapper.getReader()));
    }

    @Test
    void acceptsBodyExactlyAtLimit() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/batches");
        request.setContent(new byte[16]);

        BodyCachingHttpServletRequestWrapper wrapper = new BodyCachingHttpServletRequestWrapper(request, 16);

        assertArrayEquals(new byte[16], IOUtils.toByteArray(wrapper.getInputStream()));
    }

    @Test
    void rejectsDeclaredContentLengthAboveLimitWithoutReadingBody() {
        CountingServletInputStream body = new CountingServletInputStream(Long.MAX_VALUE);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/batches") {

            @Override
            public ServletInputStream getInputStream() {
                return body;
            }

            @Override
            public long getContentLengthLong() {
                return 5L * 1024 * 1024 * 1024;
            }
        };

        RequestBodyTooLargeException e = assertThrows(RequestBodyTooLargeException.class,
                () -> new BodyCachingHttpServletRequestWrapper(request, 1024));

        assertEquals(1024, e.getMaxBodySize());
        assertEquals(0, body.bytesRead);
    }

    @Test
    void rejectsStreamedBodyAboveLimitAfterReadingAtMostLimitPlusOneByte() {
        CountingServletInputStream body = new CountingServletInputStream(Long.MAX_VALUE);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/batches") {

            @Override
            public ServletInputStream getInputStream() {
                return body;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };

        assertThrows(RequestBodyTooLargeException.class, () -> new BodyCachingHttpServletRequestWrapper(request, 1024));
        assertTrue(body.bytesRead <= 1025, "read " + body.bytesRead + " bytes");
    }

    @Test
    void defaultConstructorAppliesDefaultLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/batches");
        request.setContent(new byte[(int) BodyCachingHttpServletRequestWrapper.DEFAULT_MAX_BODY_SIZE + 1]);

        assertThrows(RequestBodyTooLargeException.class, () -> new BodyCachingHttpServletRequestWrapper(request));
    }

    @Test
    void ofReusesAlreadyCachedRequest() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/batches");
        request.setContent("[]".getBytes(UTF_8));
        BodyCachingHttpServletRequestWrapper wrapper = BodyCachingHttpServletRequestWrapper.of(request, 1024);

        assertSame(wrapper, BodyCachingHttpServletRequestWrapper.of(wrapper, 1024));
        assertFalse(wrapper.getInputStream().isFinished());
    }

    private static final class CountingServletInputStream extends ServletInputStream {

        private final long size;
        private long bytesRead;

        private CountingServletInputStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (bytesRead >= size) {
                return -1;
            }
            bytesRead++;
            return 'a';
        }

        @Override
        public boolean isFinished() {
            return bytesRead >= size;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }
}
