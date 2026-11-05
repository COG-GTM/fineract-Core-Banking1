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
package org.apache.fineract.command.implementation;

import static org.apache.fineract.command.core.CommandConstants.COMMAND_AUDIT_FIELD_MAX_LENGTH;
import static org.apache.fineract.command.core.CommandConstants.COMMAND_REQUEST_ATTRIBUTE_IP;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.command.core.Command;
import org.apache.fineract.command.core.CommandExecutor;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.command.core.CommandProperties;
import org.apache.fineract.command.core.exception.CommandIllegalArgumentException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseCommandPipeline implements CommandPipeline {

    protected final CommandExecutor executor;
    protected final CommandProperties properties;

    protected void setAttributes(Command<?> command) {
        if (StringUtils.isEmpty(command.getIpAddress())) {
            command.setIpAddress(getClientIpAddress());
        }
        command.setIpAddress(StringUtils.truncate(command.getIpAddress(), COMMAND_AUDIT_FIELD_MAX_LENGTH));
        if (StringUtils.isEmpty(command.getIdempotencyKey())) {
            command.setIdempotencyKey(getHeader(properties.getIdemPotencyKeyHeaderName()));
        }
        if (StringUtils.length(command.getIdempotencyKey()) > COMMAND_AUDIT_FIELD_MAX_LENGTH) {
            throw new CommandIllegalArgumentException(command,
                    "idempotency key must not be longer than " + COMMAND_AUDIT_FIELD_MAX_LENGTH + " characters");
        }
        if (command.getCreatedAt() == null) {
            command.setCreatedAt(Instant.now());
        }
        if (StringUtils.isEmpty(command.getInitiatedByUsername())) {
            command.setInitiatedByUsername(getUsername());
        }
    }

    protected String getHeader(String name) {
        return getHeader(name, false);
    }

    protected String getHeader(String name, boolean searchParameter) {
        var attributes = RequestContextHolder.getRequestAttributes();

        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            var value = servletAttributes.getRequest().getHeader(name.toLowerCase());

            if (searchParameter && StringUtils.isEmpty(value)) {
                value = servletAttributes.getRequest().getParameter(name.toLowerCase());
            }

            return value;
        }

        return null;
    }

    /**
     * Resolves the caller IP on the server side: the value resolved by the IP tracking filter (request attribute) if
     * present, otherwise the remote address of the request (which honours the configured forwarded headers strategy).
     * Client-sent free-form headers are deliberately not used.
     */
    protected String getClientIpAddress() {
        var attributes = RequestContextHolder.getRequestAttributes();

        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            var request = servletAttributes.getRequest();

            if (request.getAttribute(COMMAND_REQUEST_ATTRIBUTE_IP) instanceof String ip && StringUtils.isNotBlank(ip)) {
                return ip;
            }

            return request.getRemoteAddr();
        }

        return null;
    }

    protected String getUsername() {
        final var context = SecurityContextHolder.getContext();

        if (context != null) {
            final var auth = context.getAuthentication();

            if (auth != null) {
                return auth.getName();
            }
        }

        return null;
    }
}
