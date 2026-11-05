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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.springframework.stereotype.Component;

/**
 * Restricts the URLs that hooks may call: only configured schemes are accepted, and hosts that resolve to loopback,
 * private, link-local (cloud metadata) or other non-public addresses are rejected unless listed in
 * {@code fineract.hooks.allowed-hosts}.
 */
@Component
public class WebHookUrlValidator {

    private static final Set<String> DEFAULT_ALLOWED_SCHEMES = Set.of("https");

    private final Set<String> allowedSchemes;
    private final Set<String> allowedHosts;

    public WebHookUrlValidator(final FineractProperties fineractProperties) {
        final FineractProperties.FineractHooksProperties hooks = fineractProperties == null ? null : fineractProperties.getHooks();
        final Set<String> schemes = hooks == null ? Set.of() : normalize(hooks.getAllowedSchemes());
        this.allowedSchemes = schemes.isEmpty() ? DEFAULT_ALLOWED_SCHEMES : schemes;
        this.allowedHosts = hooks == null ? Set.of() : normalize(hooks.getAllowedHosts());
    }

    /**
     * @throws IllegalArgumentException
     *             if the URL is malformed, uses a scheme that is not allowed, or its host does not resolve to public addresses only
     */
    public HttpUrl validate(final String url) {
        final HttpUrl httpUrl = url == null ? null : HttpUrl.parse(url.trim());
        if (httpUrl == null || !allowedSchemes.contains(httpUrl.scheme())) {
            throw new IllegalArgumentException("Hook URL is not allowed");
        }
        final String host = httpUrl.host();
        // Also resolves hostnames here: when a JVM proxy is configured OkHttp resolves only the proxy, so dns() never
        // sees the destination. With a proxy the address is checked once, before the request (no rebinding protection).
        if (!isHostAllowlisted(host)) {
            try {
                checkAddresses(host, List.of(InetAddress.getAllByName(host)));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Hook URL is not allowed", e);
            }
        }
        return httpUrl;
    }

    /**
     * DNS resolver for hook HTTP clients: rejects at connection time any non-allowlisted host that resolves to a
     * non-public address, which also covers DNS rebinding after {@link #validate(String)}.
     */
    public Dns dns() {
        return hostname -> {
            final List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
            if (!isHostAllowlisted(hostname)) {
                checkAddresses(hostname, addresses);
            }
            return addresses;
        };
    }

    private void checkAddresses(final String host, final List<InetAddress> addresses) throws UnknownHostException {
        for (final InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new UnknownHostException("Hook host is not allowed: " + host);
            }
        }
    }

    private boolean isHostAllowlisted(final String host) {
        return host != null && allowedHosts.contains(stripBrackets(host).toLowerCase(Locale.ROOT));
    }

    static boolean isPublicAddress(final InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        final byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(b);
        }
        if (address instanceof Inet6Address) {
            // fc00::/7 unique local
            if ((b[0] & 0xFE) == 0xFC) {
                return false;
            }
            // fec0::/10 deprecated site local, 2001:db8::/32 documentation
            if (((b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0xC0)
                    || ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x01 && (b[2] & 0xFF) == 0x0D && (b[3] & 0xFF) == 0xB8)) {
                return false;
            }
            // ::/96 (IPv4-compatible), ::ffff:0:0/96 (IPv4-mapped), 64:ff9b::/96 (NAT64): check the embedded IPv4
            final boolean ipv4Mapped = isZero(b, 0, 10) && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
            final boolean nat64 = b[0] == 0x00 && b[1] == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B && isZero(b, 4, 12);
            if (isZero(b, 0, 12) || ipv4Mapped || nat64) {
                return isPublicIpv4(new byte[] { b[12], b[13], b[14], b[15] });
            }
            // 2002::/16 6to4
            if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02) {
                return isPublicIpv4(new byte[] { b[2], b[3], b[4], b[5] });
            }
            return true;
        }
        return false;
    }

    private static boolean isPublicIpv4(final byte[] b) {
        final int a0 = b[0] & 0xFF;
        final int a1 = b[1] & 0xFF;
        final int a2 = b[2] & 0xFF;
        return !(a0 == 0 // 0.0.0.0/8
                || a0 == 10 // 10.0.0.0/8
                || a0 == 127 // loopback
                || (a0 == 100 && a1 >= 64 && a1 <= 127) // 100.64.0.0/10 carrier-grade NAT
                || (a0 == 169 && a1 == 254) // link-local, cloud metadata
                || (a0 == 172 && a1 >= 16 && a1 <= 31) // 172.16.0.0/12
                || (a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2)) // 192.0.0.0/24, 192.0.2.0/24
                || (a0 == 192 && a1 == 168) // 192.168.0.0/16
                || (a0 == 198 && (a1 == 18 || a1 == 19)) // 198.18.0.0/15
                || (a0 == 198 && a1 == 51 && a2 == 100) // 198.51.100.0/24
                || (a0 == 203 && a1 == 0 && a2 == 113) // 203.0.113.0/24
                || a0 >= 224); // multicast, reserved, broadcast
    }

    private static boolean isZero(final byte[] b, final int from, final int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpLiteral(final String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    private static String stripBrackets(final String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static Set<String> normalize(final List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().filter(v -> v != null && !v.isBlank()).map(v -> stripBrackets(v.trim()).toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
