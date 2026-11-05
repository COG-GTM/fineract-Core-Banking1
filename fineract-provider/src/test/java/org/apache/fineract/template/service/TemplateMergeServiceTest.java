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
package org.apache.fineract.template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.template.domain.Template;
import org.apache.fineract.template.domain.TemplateMapper;
import org.apache.fineract.template.exception.TemplateForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateMergeServiceTest {

    private HttpServer trustedServer;
    private HttpServer attackerServer;
    private final AtomicInteger trustedHits = new AtomicInteger();
    private final AtomicInteger attackerHits = new AtomicInteger();
    private final List<String> trustedPaths = new CopyOnWriteArrayList<>();
    private FineractProperties.FineractTemplateProperties templateProperties;
    private TemplateMergeService service;

    @BeforeEach
    void setUp() throws IOException {
        trustedServer = startServer(trustedHits, trustedPaths, "{\"name\":\"trusted\"}");
        attackerServer = startServer(attackerHits, new CopyOnWriteArrayList<>(), "{\"name\":\"attacker\"}");
        templateProperties = new FineractProperties.FineractTemplateProperties();
        templateProperties.setRegexWhitelistEnabled(true);
        templateProperties.setRegexWhitelist(List.of("http://127\\.0\\.0\\.1:\\d+/.*"));
        final FineractProperties properties = new FineractProperties();
        properties.setTemplate(templateProperties);
        service = new TemplateMergeService(properties);
        ThreadLocalContextUtil.setAuthToken("dXNlcjpwYXNz");
    }

    @AfterEach
    void tearDown() {
        trustedServer.stop(0);
        attackerServer.stop(0);
        ThreadLocalContextUtil.reset();
    }

    @Test
    void relativeMapperIgnoresBaseUriFromScope() {
        final Map<String, Object> scopes = new HashMap<>();
        scopes.put("BASE_URI", baseOf(attackerServer));
        scopes.put("clientId", "1");

        final String result = service.compile(template("v1/clients/{{clientId}}"), scopes, baseOf(trustedServer));

        assertEquals("trusted", result);
        assertEquals(1, trustedHits.get());
        assertEquals(0, attackerHits.get());
    }

    @Test
    void relativeMapperWithoutServerBaseUriIsRejected() {
        final Map<String, Object> scopes = new HashMap<>();
        scopes.put("BASE_URI", baseOf(attackerServer));

        assertThrows(TemplateForbiddenException.class, () -> service.compile(template("v1/clients/1"), scopes));
        assertEquals(0, attackerHits.get());
    }

    @Test
    void relativeMapperCannotEscapeServerOrigin() {
        final String trustedOrigin = "http://127.0.0.1:" + trustedServer.getAddress().getPort();
        final Map<String, Object> scopes = new HashMap<>();
        scopes.put("target", "@127.0.0.1:" + attackerServer.getAddress().getPort() + "/api/v1/clients");

        assertThrows(TemplateForbiddenException.class, () -> service.compile(template("{{target}}"), scopes, trustedOrigin));
        assertEquals(0, attackerHits.get());
        assertEquals(0, trustedHits.get());
    }

    @Test
    void configuredBaseUriTakesPrecedenceOverRequestBaseUri() {
        templateProperties.setBaseUri(baseOf(trustedServer));

        final String result = service.compile(template("v1/clients/1"), new HashMap<>(), baseOf(attackerServer));

        assertEquals("trusted", result);
        assertEquals(1, trustedHits.get());
        assertEquals(0, attackerHits.get());
    }

    @Test
    void configuredBaseUriWithoutTrailingSlashIsNormalized() {
        final String base = baseOf(trustedServer);
        templateProperties.setBaseUri(base.substring(0, base.length() - 1));

        final String result = service.compile(template("v1/clients/1"), new HashMap<>(), baseOf(attackerServer));

        assertEquals("trusted", result);
        assertEquals(List.of("/api/v1/clients/1"), trustedPaths);
        assertEquals(0, attackerHits.get());
    }

    private static Template template(final String mapperUrl) {
        return new Template("t", "{{#client}}{{name}}{{/client}}", null, null, List.of(new TemplateMapper(0, "client", mapperUrl)));
    }

    private static String baseOf(final HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/";
    }

    private static HttpServer startServer(final AtomicInteger hits, final List<String> paths, final String body) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            paths.add(exchange.getRequestURI().getPath());
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
