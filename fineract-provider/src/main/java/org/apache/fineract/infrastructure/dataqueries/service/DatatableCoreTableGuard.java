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
package org.apache.fineract.infrastructure.dataqueries.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Identifies tables owned by the Fineract schema so they can never be registered, altered, dropped or queried through
 * the datatable API. isCoreTable matches tables defined in the Liquibase changelogs exactly and guards every operation
 * on an existing datatable, so custom tables that merely use a reserved-looking name keep working. isReservedName
 * matches core prefixes and guards the creation or registration of new datatables.
 */
@Slf4j
public final class DatatableCoreTableGuard {

    static final List<String> CORE_TABLE_PREFIXES = List.of("m_", "acc_", "c_", "x_", "r_", "ref_", "stretchy_", "twofactor_", "oauth_",
            "job_", "scheduler_", "scheduled_", "rpt_", "mix_", "sms_", "notification_", "topic_", "interop_", "gsim_", "glim_",
            "client_device_", "request_audit_", "batch_", "tenant_", "databasechangelog");

    static final Set<String> CORE_TABLE_NAMES = Set.of("command", "job", "topic", "tenants", "timezones", "ppi_likelihoods",
            "ppi_likelihoods_ppi", "ppi_scores", "databasechangelog", "databasechangeloglock");

    private static final List<String> CHANGELOGS = List.of("classpath*:db/changelog/**/*.xml", "classpath*:db/changelog/**/*.sql");
    private static final Pattern LIQUIBASE_TABLE = Pattern
            .compile("<(?:createTable|renameTable)\\b[^>]*?\\b(?:newTableName|tableName)=\"([^\"]+)\"");
    private static final Pattern SQL_CREATE_TABLE = Pattern.compile("(?i)\\bcreate\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?[`\"]?(\\w+)");
    private static final List<Pattern> CHANGELOG_TABLE_PATTERNS = List.of(LIQUIBASE_TABLE, SQL_CREATE_TABLE);

    private DatatableCoreTableGuard() {}

    public static boolean isCoreTable(final String tableName) {
        if (tableName == null) {
            return false;
        }
        final String normalized = normalize(tableName);
        final Set<String> schemaTables = SchemaTables.NAMES;
        if (schemaTables.isEmpty()) {
            return isReservedName(normalized);
        }
        return CORE_TABLE_NAMES.contains(normalized) || schemaTables.contains(normalized);
    }

    public static boolean isReservedName(final String tableName) {
        if (tableName == null) {
            return false;
        }
        final String normalized = normalize(tableName);
        return CORE_TABLE_NAMES.contains(normalized) || CORE_TABLE_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    static Set<String> loadSchemaTables() {
        final Set<String> tables = new HashSet<>();
        try {
            final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (String location : CHANGELOGS) {
                for (Resource changelog : resolver.getResources(location)) {
                    try (InputStream in = changelog.getInputStream()) {
                        tables.addAll(extractTableNames(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not read Liquibase changelogs, falling back to core table prefixes", e);
            return Set.of();
        }
        return Set.copyOf(tables);
    }

    static Set<String> extractTableNames(final String changelog) {
        final Set<String> tables = new HashSet<>();
        for (Pattern pattern : CHANGELOG_TABLE_PATTERNS) {
            final Matcher matcher = pattern.matcher(changelog);
            while (matcher.find()) {
                tables.add(normalize(matcher.group(1)));
            }
        }
        return tables;
    }

    private static String normalize(final String tableName) {
        return tableName.trim().toLowerCase(Locale.ROOT);
    }

    private static final class SchemaTables {

        private static final Set<String> NAMES = loadSchemaTables();
    }
}
