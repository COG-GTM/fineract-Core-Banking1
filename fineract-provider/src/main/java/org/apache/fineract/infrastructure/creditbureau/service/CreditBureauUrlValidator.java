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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Restricts the outbound URLs used for credit bureau integrations to public HTTPS endpoints (optionally limited to an
 * operator-managed host allowlist). Also acts as the {@link Dns} of the credit bureau HTTP client so the check is
 * repeated against the addresses actually used to connect.
 */
@Slf4j
@Component
public class CreditBureauUrlValidator implements Dns {

    public static final String HTTPS = "https";

    private static final Set<String> URL_CONFIGURATION_KEYS = Set.of("tokenurl", "searchurl", "creditreporturl", "addcreditreporturl");

    private final Set<String> allowedHosts;
    private final boolean allowPrivateNetwork;
    private final Dns delegate;

    public CreditBureauUrlValidator(@Value("${fineract.credit-bureau.allowed-hosts:}") final String allowedHosts,
            @Value("${fineract.credit-bureau.allow-private-network:false}") final boolean allowPrivateNetwork) {
        this(allowedHosts, allowPrivateNetwork, Dns.SYSTEM);
    }

    CreditBureauUrlValidator(final String allowedHosts, final boolean allowPrivateNetwork, final Dns delegate) {
        this.allowedHosts = Arrays.stream(StringUtils.defaultString(allowedHosts).split(",")).map(String::trim)
                .filter(StringUtils::isNotEmpty).map(host -> host.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        this.allowPrivateNetwork = allowPrivateNetwork;
        this.delegate = delegate;
    }

    public static boolean isUrlConfigurationKey(final String configurationKey) {
        return configurationKey != null && URL_CONFIGURATION_KEYS.contains(configurationKey.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * @return the validation error code suffix when the URL is not allowed, empty when it may be used
     */
    public Optional<String> findViolation(final String url) {
        final HttpUrl httpUrl = StringUtils.isBlank(url) ? null : HttpUrl.parse(url.trim());
        if (httpUrl == null) {
            return Optional.of("url.invalid");
        }
        if (!allowPrivateNetwork && !HTTPS.equals(httpUrl.scheme())) {
            return Optional.of("url.scheme.not.https");
        }
        final String host = httpUrl.host().toLowerCase(Locale.ROOT);
        if (!allowedHosts.isEmpty() && !allowedHosts.contains(host)) {
            return Optional.of("url.host.not.allowed");
        }
        try {
            lookup(host);
        } catch (NonPublicAddressException e) {
            log.warn("Rejected credit bureau URL host {}: {}", host, e.getMessage());
            return Optional.of("url.host.not.allowed");
        } catch (UnknownHostException e) {
            log.warn("Could not resolve credit bureau URL host {}", host, e);
            return Optional.of("url.host.unresolvable");
        }
        return Optional.empty();
    }

    @Override
    public List<InetAddress> lookup(final String hostname) throws UnknownHostException {
        final List<InetAddress> addresses = delegate.lookup(hostname);
        if (!allowPrivateNetwork) {
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new NonPublicAddressException("Credit bureau host " + hostname + " resolves to a non-public address");
                }
            }
        }
        return addresses;
    }

    static boolean isPublicAddress(final InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        final byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            final int first = bytes[0] & 0xff;
            // fc00::/7 unique local (includes fd00:ec2::254 metadata)
            if ((first & 0xfe) == 0xfc) {
                return false;
            }
            // IPv4-compatible / IPv4-mapped (::a.b.c.d, ::ffff:a.b.c.d) and NAT64 (64:ff9b::/96)
            if (isZero(bytes, 0, 10) && (isZero(bytes, 10, 12) || ((bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff))
                    || (first == 0x00 && (bytes[1] & 0xff) == 0x64 && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b
                            && isZero(bytes, 4, 12))) {
                return isPublicIpv4(Arrays.copyOfRange(bytes, 12, 16));
            }
            // 6to4 (2002::/16) embeds an IPv4 address
            if (first == 0x20 && (bytes[1] & 0xff) == 0x02) {
                return isPublicIpv4(Arrays.copyOfRange(bytes, 2, 6));
            }
            return true;
        }
        return false;
    }

    private static boolean isPublicIpv4(final byte[] b) {
        final int a0 = b[0] & 0xff;
        final int a1 = b[1] & 0xff;
        final int a2 = b[2] & 0xff;
        return !(a0 == 0 // 0.0.0.0/8
                || a0 == 10 // 10.0.0.0/8
                || a0 == 127 // loopback
                || (a0 == 100 && a1 >= 64 && a1 <= 127) // 100.64.0.0/10 carrier-grade NAT
                || (a0 == 169 && a1 == 254) // link-local / cloud metadata
                || (a0 == 172 && a1 >= 16 && a1 <= 31) // 172.16.0.0/12
                || (a0 == 192 && a1 == 0 && a2 == 0) // 192.0.0.0/24
                || (a0 == 192 && a1 == 168) // 192.168.0.0/16
                || (a0 == 198 && (a1 == 18 || a1 == 19)) // 198.18.0.0/15 benchmarking
                || a0 >= 224); // multicast, reserved, broadcast
    }

    static final class NonPublicAddressException extends UnknownHostException {

        NonPublicAddressException(final String message) {
            super(message);
        }
    }

    private static boolean isZero(final byte[] bytes, final int from, final int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
