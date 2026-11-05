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

import static org.apache.fineract.infrastructure.hooks.api.HookApiConstants.contentTypeName;
import static org.apache.fineract.infrastructure.hooks.api.HookApiConstants.payloadURLName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

class HookProcessorFineractEndpointTest {

    private static final String CONFIGURED_BASE_URL = "https://fineract.example.com/fineract-provider/";
    private static final String POISONED_BASE_URL = "https://evil.example/fineract-provider/";

    private record ReceivedRequest(String path, String endpointHeader, String body) {}

    private final BlockingQueue<ReceivedRequest> received = new LinkedBlockingQueue<>();
    private HttpServer server;
    private String receiverUrl;
    private FineractContext context;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("baseUrl", POISONED_BASE_URL);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            final String path = exchange.getRequestURI().getPath();
            received.add(new ReceivedRequest(path, exchange.getRequestHeaders().getFirst(WebHookService.ENDPOINT_HEADER),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            final byte[] response = path.endsWith("/configuration") ? "\"api-key\"".getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length == 0 ? -1 : response.length);
            if (response.length > 0) {
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
        receiverUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook/";

        final FineractPlatformTenant tenant = mock(FineractPlatformTenant.class);
        when(tenant.getTenantIdentifier()).thenReturn("default");
        context = FineractContext.builder().tenantContext(tenant).authTokenContext("dXNlcjpwYXNzd29yZA==").build();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        System.clearProperty("baseUrl");
    }

    @Test
    void endpointUrlComesFromConfigurationOnly() throws Exception {
        assertEquals(CONFIGURED_BASE_URL, processorHelper(CONFIGURED_BASE_URL).getFineractEndpointUrl());
        assertNull(processorHelper(null).getFineractEndpointUrl());
        assertNull(new ProcessorHelper(new FineractProperties()).getFineractEndpointUrl());
    }

    @Test
    void webHookAdvertisesConfiguredEndpoint() throws Exception {
        final Hook hook = hookWithConfig(HookConfiguration.createNewWithoutHook("string", payloadURLName, receiverUrl),
                HookConfiguration.createNewWithoutHook("string", contentTypeName, "json"));

        new WebHookProcessor(processorHelper(CONFIGURED_BASE_URL)).process(hook, "{\"loanId\":1}", "LOAN", "APPROVE", context);

        final ReceivedRequest request = received.poll(10, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals(CONFIGURED_BASE_URL, request.endpointHeader());
    }

    @Test
    void elasticSearchHookAdvertisesConfiguredEndpoint() throws Exception {
        final Hook hook = hookWithConfig(HookConfiguration.createNewWithoutHook("string", payloadURLName, receiverUrl),
                HookConfiguration.createNewWithoutHook("string", contentTypeName, "json"));

        new ElasticSearchHookProcessor(processorHelper(CONFIGURED_BASE_URL)).process(hook, "{\"loanId\":1}", "LOAN", "APPROVE", context);

        final ReceivedRequest request = received.poll(10, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals(CONFIGURED_BASE_URL, request.endpointHeader());
    }

    @Test
    void twilioHookRegistersConfiguredEndpoint() throws Exception {
        final Hook hook = hookWithConfig(HookConfiguration.createNewWithoutHook("string", payloadURLName, receiverUrl));
        final TwilioHookProcessor processor = new TwilioHookProcessor(mock(HookConfigurationRepository.class),
                mock(TemplateMergeService.class), mock(ClientRepositoryWrapper.class), processorHelper(CONFIGURED_BASE_URL));

        processor.process(hook, "{\"loanId\":1}", "LOAN", "APPROVE", context);

        final ReceivedRequest request = received.poll(10, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/configuration", request.path());
        final JsonObject smsBridgeConfig = JsonParser.parseString(request.body()).getAsJsonObject();
        assertEquals(CONFIGURED_BASE_URL, smsBridgeConfig.get("endpoint").getAsString());
    }

    private static Hook hookWithConfig(final HookConfiguration... config) {
        final Hook hook = mock(Hook.class);
        when(hook.getId()).thenReturn(1L);
        when(hook.getConfig()).thenReturn(Set.of(config));
        return hook;
    }

    private static ProcessorHelper processorHelper(final String baseUrl) throws Exception {
        final FineractProperties.FineractServerProperties serverProperties = new FineractProperties.FineractServerProperties();
        serverProperties.setBaseUrl(baseUrl);
        final FineractProperties properties = new FineractProperties();
        properties.setServer(serverProperties);
        return new ProcessorHelper(properties);
    }
}
