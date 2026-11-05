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
package org.apache.fineract.infrastructure.jobs.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.apache.fineract.infrastructure.core.http.BodyCachingHttpServletRequestWrapper;
import org.apache.fineract.infrastructure.core.http.RequestBodyTooLargeException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;
import org.springframework.web.util.WebUtils;

/**
 * Only batch API requests need their body inspected by the loan filters, so only those are buffered, only for
 * authenticated callers and only up to {@code fineract.api.cached-request-body-size-limit}.
 */
final class CachedRequestBodySupport {

    private static final int SC_PAYLOAD_TOO_LARGE = 413;

    private CachedRequestBodySupport() {}

    static boolean isBatchApi(String pathInfo) {
        return StringUtils.isNotBlank(pathInfo) && pathInfo.startsWith("/v1/batches");
    }

    static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
    }

    static long maxBodySize(FineractProperties fineractProperties) {
        FineractProperties.FineractApiProperties api = fineractProperties == null ? null : fineractProperties.getApi();
        DataSize limit = api == null ? null : api.getCachedRequestBodySizeLimit();
        return limit == null ? BodyCachingHttpServletRequestWrapper.DEFAULT_MAX_BODY_SIZE : limit.toBytes();
    }

    static BodyCachingHttpServletRequestWrapper cachedRequest(HttpServletRequest request) {
        BodyCachingHttpServletRequestWrapper cachedRequest = WebUtils.getNativeRequest(request, BodyCachingHttpServletRequestWrapper.class);
        if (cachedRequest == null) {
            throw new IllegalStateException("Batch API request body has not been cached");
        }
        return cachedRequest;
    }

    static void rejectTooLarge(HttpServletResponse response, RequestBodyTooLargeException e) throws IOException {
        response.setStatus(SC_PAYLOAD_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(ApiGlobalErrorResponse.create(SC_PAYLOAD_TOO_LARGE, "error.msg.request.body.too.large", e.getMessage(),
                "Request body is too large. Maximum allowed size is " + e.getMaxBodySize() + " bytes.").toJson());
    }
}
