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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.fineract.command.core.Command;
import org.apache.fineract.command.core.CommandExecutor;
import org.apache.fineract.command.core.CommandProperties;
import org.apache.fineract.command.core.exception.CommandAuditException;
import org.apache.fineract.command.core.exception.CommandIllegalArgumentException;
import org.apache.fineract.command.persistence.domain.CommandRepository;
import org.apache.fineract.command.persistence.mapping.CommandMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CommandAuditIntegrityTest {

    private final CommandExecutor executor = mock(CommandExecutor.class);
    private final CommandProperties properties = CommandProperties.builder().build();
    private final DefaultCommandPipeline pipeline = new DefaultCommandPipeline(executor, properties);

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ipAddressIsNotTakenFromClientHeader() {
        var request = request();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("IP", "10.0.0.1");

        var command = new Command<String>();
        pipeline.send(command);

        assertEquals("192.0.2.10", command.getIpAddress());
    }

    @Test
    void ipAddressResolvedByIpTrackingFilterIsUsed() {
        var request = request();
        request.setRemoteAddr("192.0.2.10");
        request.setAttribute("IP", "198.51.100.7");

        var command = new Command<String>();
        pipeline.send(command);

        assertEquals("198.51.100.7", command.getIpAddress());
    }

    @Test
    void oversizedIpAddressIsTruncated() {
        request().setAttribute("IP", "1".repeat(300));

        var command = new Command<String>();
        pipeline.send(command);

        assertEquals(255, command.getIpAddress().length());
    }

    @Test
    void oversizedIdempotencyKeyIsRejectedBeforeExecution() {
        request().addHeader(properties.getIdemPotencyKeyHeaderName(), "k".repeat(256));

        assertThrows(CommandIllegalArgumentException.class, () -> pipeline.send(new Command<String>()));
        verify(executor, never()).execute(any());
    }

    @Test
    void idempotencyKeyWithinLimitIsAccepted() {
        request().addHeader(properties.getIdemPotencyKeyHeaderName(), "k".repeat(255));

        var command = new Command<String>();
        pipeline.send(command);

        assertEquals(255, command.getIdempotencyKey().length());
        verify(executor).execute(command);
    }

    @Test
    void processingAuditFailureRejectsCommand() {
        var auditor = auditor(properties);
        var command = new Command<String>();

        var e = assertThrows(CommandAuditException.class,
                () -> auditor.processingFallback(command, new RuntimeException("insert failed")));

        assertEquals("insert failed", e.getCause().getMessage());
        assertEquals("insert failed", command.getError());
    }

    @Test
    void processingAuditFailureIsWrittenToDeadLetterQueue(@TempDir Path dir) {
        var auditor = auditor(deadLetterQueue(dir.toString()));
        var command = new Command<String>();
        command.setCreatedAt(Instant.now());

        auditor.processingFallback(command, new RuntimeException("insert failed"));

        File[] files = dir.toFile().listFiles();
        assertTrue(files != null && files.length == 1, "Expected one dead-letter queue entry");
    }

    @Test
    void processingAuditFailureRejectsCommandWhenDeadLetterQueueFails(@TempDir Path dir) throws Exception {
        var file = dir.resolve("not-a-directory").toFile();
        assertTrue(file.createNewFile());
        var auditor = auditor(deadLetterQueue(file.getPath()));
        var command = new Command<String>();
        command.setCreatedAt(Instant.now());

        assertThrows(CommandAuditException.class, () -> auditor.processingFallback(command, new RuntimeException("insert failed")));
    }

    @Test
    void noClientIpOutsideOfRequest() {
        var command = new Command<String>();
        pipeline.send(command);

        assertNull(command.getIpAddress());
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private static DefaultCommandAuditor auditor(CommandProperties properties) {
        return new DefaultCommandAuditor(mock(CommandMapper.class), mock(CommandRepository.class), properties,
                new ObjectMapper().findAndRegisterModules());
    }

    private static CommandProperties deadLetterQueue(String path) {
        return CommandProperties.builder().fileDeadLetterQueueEnabled(true).fileDeadLetterQueuePath(path).build();
    }
}
