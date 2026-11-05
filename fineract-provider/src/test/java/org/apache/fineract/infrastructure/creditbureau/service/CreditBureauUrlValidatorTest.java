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
package org.apache.fineract.infrastructure.creditbureau.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import okhttp3.Dns;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreditBureauUrlValidatorTest {

    private static final Dns PUBLIC_DNS = hostname -> List.of(InetAddress.getByAddress(hostname, new byte[] { 93, (byte) 184, (byte) 215, 14 }));
    private static final Dns INTERNAL_DNS = hostname -> List.of(InetAddress.getByAddress(hostname, new byte[] { 10, 0, 0, 5 }));

    @ParameterizedTest
    @ValueSource(strings = { "http://169.254.169.254/latest/meta-data/", "https://169.254.169.254/latest/meta-data/", "https://127.0.0.1/",
            "https://localhost/", "https://10.1.2.3/", "https://172.16.0.1/", "https://192.168.1.1/", "https://100.100.100.200/",
            "https://0.0.0.0/", "https://[::1]/", "https://[fd00:ec2::254]/", "https://[::ffff:169.254.169.254]/", "https://[fe80::1]/",
            "https://[64:ff9b::a9fe:a9fe]/", "https://[2002:a9fe:a9fe::]/", "ftp://bureau.example.com/", "not a url", "" })
    void rejectsNonPublicOrNonHttpsUrls(String url) {
        assertTrue(new CreditBureauUrlValidator("", false).findViolation(url).isPresent(), url);
    }

    @Test
    void acceptsPublicHttpsUrl() {
        assertEquals(Optional.empty(), new CreditBureauUrlValidator("", false, PUBLIC_DNS).findViolation("https://api.bureau.example/search/"));
        assertEquals(Optional.empty(), new CreditBureauUrlValidator("", false).findViolation("https://93.184.215.14/token"));
    }

    @Test
    void rejectsPlainHttp() {
        assertEquals(Optional.of("url.scheme.not.https"),
                new CreditBureauUrlValidator("", false, PUBLIC_DNS).findViolation("http://api.bureau.example/search/"));
    }

    @Test
    void rejectsHostnameResolvingToPrivateAddress() {
        CreditBureauUrlValidator validator = new CreditBureauUrlValidator("", false, INTERNAL_DNS);
        assertEquals(Optional.of("url.host.not.allowed"), validator.findViolation("https://bureau.internal/search/"));
        assertThrows(UnknownHostException.class, () -> validator.lookup("bureau.internal"));
    }

    @Test
    void enforcesHostAllowlist() {
        CreditBureauUrlValidator validator = new CreditBureauUrlValidator(" api.bureau.example , other.example ", false, PUBLIC_DNS);
        assertEquals(Optional.empty(), validator.findViolation("https://API.bureau.example/search/"));
        assertEquals(Optional.of("url.host.not.allowed"), validator.findViolation("https://attacker.example/search/"));
    }

    @Test
    void allowPrivateNetworkPermitsLocalHttpForTesting() {
        CreditBureauUrlValidator validator = new CreditBureauUrlValidator("", true, INTERNAL_DNS);
        assertEquals(Optional.empty(), validator.findViolation("http://localhost:3558/search/"));
        assertDoesNotThrow(() -> validator.lookup("bureau.internal"));
    }

    @Test
    void recognisesUrlConfigurationKeys() {
        assertTrue(CreditBureauUrlValidator.isUrlConfigurationKey("searchurl"));
        assertTrue(CreditBureauUrlValidator.isUrlConfigurationKey("CREDITREPORTURL"));
        assertTrue(CreditBureauUrlValidator.isUrlConfigurationKey("addCreditReporturl"));
        assertTrue(CreditBureauUrlValidator.isUrlConfigurationKey("tokenurl"));
        assertFalse(CreditBureauUrlValidator.isUrlConfigurationKey("Username"));
        assertFalse(CreditBureauUrlValidator.isUrlConfigurationKey(null));
    }
}
