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
package org.apache.fineract.infrastructure.hooks.processor;

import static org.apache.fineract.infrastructure.hooks.api.HookApiConstants.payloadURLName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.hooks.domain.Hook;
import org.apache.fineract.infrastructure.hooks.domain.HookConfiguration;
import org.apache.fineract.infrastructure.hooks.domain.HookConfigurationRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.template.service.TemplateMergeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwilioHookProcessorTest {

    private static final String USER_TOKEN = "dXNlcjpzZXNzaW9uLXRva2Vu";
    private static final String BRIDGE_TOKEN = "c21zLWJyaWRnZTpzZXJ2aWNl";
    private static final String PAYLOAD = "{\"clientId\":1}";

    @Mock
    private HookConfigurationRepository hookConfigurationRepository;
    @Mock
    private TemplateMergeService templateMergeService;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;

    private final Map<String, String> receivedBodies = new ConcurrentHashMap<>();
    private HttpServer listener;
    private String listenerUrl;
    private FineractProperties.FineractTwilioHookProperties twilioProperties;
    private TwilioHookProcessor underTest;

    @BeforeEach
    void setUp() throws Exception {
        listener = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        listener.createContext("/", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                receivedBodies.put(exchange.getRequestURI().getPath(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            final byte[] response = "\"bridge-api-key\"".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        listener.start();
        listenerUrl = "http://127.0.0.1:" + listener.getAddress().getPort() + "/";

        twilioProperties = new FineractProperties.FineractTwilioHookProperties();
        final FineractProperties.FineractHooksProperties hooksProperties = new FineractProperties.FineractHooksProperties();
        hooksProperties.setTwilio(twilioProperties);
        final FineractProperties fineractProperties = new FineractProperties();
        fineractProperties.setHooks(hooksProperties);

        underTest = new TwilioHookProcessor(hookConfigurationRepository, templateMergeService, clientRepositoryWrapper,
                new ProcessorHelper(), fineractProperties);
    }

    @AfterEach
    void tearDown() {
        listener.stop(0);
    }

    @Test
    void handshakeNeverForwardsTriggeringUserTokenAndWithholdsBridgeTokenOverPlainHttp() throws IOException {
        twilioProperties.setAllowedHosts(List.of("127.0.0.1"));
        twilioProperties.setBridgeToken(BRIDGE_TOKEN);

        underTest.process(hookWithUrl(listenerUrl), PAYLOAD, "CLIENT", "CREATE", context());

        final String handshake = receivedBodies.get("/configuration");
        assertTrue(handshake != null, "SMS bridge handshake should be sent to an allowlisted host");
        assertFalse(handshake.contains(USER_TOKEN), "triggering user's auth token must not be forwarded");
        assertFalse(handshake.contains(BRIDGE_TOKEN), "bridge token must not be sent without TLS");
        verify(hookConfigurationRepository).save(any(HookConfiguration.class));
    }

    @Test
    void redirectFromAllowlistedBridgeIsNotFollowed() throws IOException {
        final HttpServer redirectTarget = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        final Map<String, String> redirectedBodies = new ConcurrentHashMap<>();
        redirectTarget.createContext("/", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                redirectedBodies.put(exchange.getRequestURI().getPath(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        redirectTarget.start();
        listener.removeContext("/");
        listener.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "http://localhost:" + redirectTarget.getAddress().getPort() + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        twilioProperties.setAllowedHosts(List.of("127.0.0.1"));
        try {
            underTest.process(hookWithUrl(listenerUrl), PAYLOAD, "CLIENT", "CREATE", context());
        } finally {
            redirectTarget.stop(0);
        }

        assertEquals(Map.of(), redirectedBodies);
        verify(hookConfigurationRepository, never()).save(any(HookConfiguration.class));
    }

    @Test
    void handshakeWithoutConfiguredBridgeTokenSendsNoToken() throws IOException {
        twilioProperties.setAllowedHosts(List.of("127.0.0.1"));

        underTest.process(hookWithUrl(listenerUrl), PAYLOAD, "CLIENT", "CREATE", context());

        final String handshake = receivedBodies.get("/configuration");
        assertTrue(handshake != null);
        assertFalse(handshake.contains(USER_TOKEN));
        assertFalse(handshake.contains("mifosToken"));
    }

    @Test
    void hookUrlOutsideAllowlistReceivesNothing() throws IOException {
        twilioProperties.setAllowedHosts(List.of("sms-bridge.internal"));
        twilioProperties.setBridgeToken(BRIDGE_TOKEN);

        underTest.process(hookWithUrl(listenerUrl), PAYLOAD, "CLIENT", "CREATE", context());

        assertEquals(Map.of(), receivedBodies);
        verify(hookConfigurationRepository, never()).save(any(HookConfiguration.class));
    }

    @Test
    void emptyAllowlistDisablesTwilioHooks() throws IOException {
        underTest.process(hookWithUrl(listenerUrl), PAYLOAD, "CLIENT", "CREATE", context());

        assertEquals(Map.of(), receivedBodies);
        verify(hookConfigurationRepository, never()).save(any(HookConfiguration.class));
    }

    private Hook hookWithUrl(final String url) {
        final Hook hook = new Hook();
        final Set<HookConfiguration> config = new HashSet<>();
        config.add(HookConfiguration.createNew(hook, "string", payloadURLName, url));
        hook.setConfig(config);
        return hook;
    }

    private FineractContext context() {
        final FineractPlatformTenant tenant = FineractPlatformTenant.builder().id(1L).tenantIdentifier("default").name("Default")
                .timezoneId("UTC").build();
        return FineractContext.builder().tenantContext(tenant).authTokenContext(USER_TOKEN).build();
    }
}
