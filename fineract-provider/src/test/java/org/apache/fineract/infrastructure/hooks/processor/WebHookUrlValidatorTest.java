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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebHookUrlValidatorTest {

    private static WebHookUrlValidator validator(final List<String> schemes, final List<String> hosts) {
        final FineractProperties properties = new FineractProperties();
        final FineractProperties.FineractHooksProperties hooks = new FineractProperties.FineractHooksProperties();
        hooks.setAllowedSchemes(schemes);
        hooks.setAllowedHosts(hosts);
        properties.setHooks(hooks);
        return new WebHookUrlValidator(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://127.0.0.1:8443/", "https://169.254.169.254/latest/meta-data/", "https://10.0.0.12:9200/",
            "https://localhost/", "https://172.16.5.4/", "https://192.168.1.1/", "https://100.64.0.1/", "https://0.0.0.0/", "https://[::1]/",
            "https://[fd00::1]/", "https://[fe80::1]/", "https://[::ffff:169.254.169.254]/", "https://2130706433/", "not a url", "" })
    void rejectsNonPublicOrMalformedUrls(final String url) {
        assertThrows(IllegalArgumentException.class, () -> validator(List.of("https"), List.of()).validate(url));
    }

    @Test
    void rejectsSchemesThatAreNotAllowed() {
        final WebHookUrlValidator validator = validator(List.of("https"), List.of());
        assertThrows(IllegalArgumentException.class, () -> validator.validate("http://93.184.215.14/"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("ftp://93.184.215.14/"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    @Test
    void defaultsToHttpsOnlyWhenNotConfigured() {
        final WebHookUrlValidator validator = new WebHookUrlValidator(new FineractProperties());
        assertThrows(IllegalArgumentException.class, () -> validator.validate("http://93.184.215.14/"));
        assertEquals("93.184.215.14", validator.validate("https://93.184.215.14/hook").host());
    }

    @Test
    void acceptsPublicHttpsUrlAndConfiguredSchemes() {
        assertEquals("/hook", validator(List.of("https"), List.of()).validate("https://93.184.215.14/hook").encodedPath());
        assertEquals("http", validator(List.of("HTTP", "https"), List.of()).validate("http://93.184.215.14/").scheme());
    }

    @Test
    void allowedHostsBypassAddressChecks() throws UnknownHostException {
        final WebHookUrlValidator validator = validator(List.of("http", "https"), List.of("LOCALHOST", "10.0.0.12", "[::1]"));
        validator.validate("http://10.0.0.12:9200/");
        validator.validate("http://[::1]:8080/");
        assertFalse(validator.dns().lookup("localhost").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> validator.validate("http://127.0.0.1/"));
    }

    @Test
    void dnsRejectsHostnamesResolvingToNonPublicAddresses() {
        assertThrows(UnknownHostException.class, () -> validator(List.of("https"), List.of()).dns().lookup("localhost"));
    }

    @Test
    void classifiesAddresses() throws UnknownHostException {
        assertTrue(WebHookUrlValidator.isPublicAddress(InetAddress.getByName("93.184.215.14")));
        assertTrue(WebHookUrlValidator.isPublicAddress(InetAddress.getByName("2606:2800:21f:cb07:6820:80da:af6b:8b2c")));
        assertFalse(WebHookUrlValidator.isPublicAddress(InetAddress.getByName("169.254.169.254")));
        assertFalse(WebHookUrlValidator.isPublicAddress(InetAddress.getByName("64:ff9b::a9fe:a9fe")));
        assertFalse(WebHookUrlValidator.isPublicAddress(InetAddress.getByName("2002:c0a8:0101::1")));
        assertFalse(WebHookUrlValidator.isPublicAddress(InetAddress.getByName("::10.0.0.1")));
        assertFalse(WebHookUrlValidator.isPublicAddress(InetAddress.getByName("224.0.0.1")));
    }
}
