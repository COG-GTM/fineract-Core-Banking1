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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class DatatableCoreTableGuardTest {

    @ParameterizedTest
    @ValueSource(strings = { "m_appuser", "M_APPUSER", "m_savings_account", "m_role_permission", "twofactor_access_token", "acc_gl_account",
            "c_configuration", "x_registered_table", "r_enum_value", "stretchy_report", "oauth_access_token", "job_run_history",
            "scheduler_detail", "rpt_sequence", "client_device_registration", "request_audit_table", "batch_custom_job_parameters",
            "databasechangelog", "databasechangeloglock", "command", "job", "topic", "tenants", "timezones", "ppi_scores",
            "ppi_likelihoods", "ppi_likelihoods_ppi" })
    void reservedNamesAreRejected(String tableName) {
        assertTrue(DatatableCoreTableGuard.isReservedName(tableName), tableName);
    }

    @ParameterizedTest
    @ValueSource(strings = { "m_appuser", "M_APPUSER", "m_savings_account", "m_role_permission", "twofactor_access_token", "acc_gl_account",
            "x_registered_table", "databasechangelog", "databasechangeloglock", "BATCH_JOB_INSTANCE", "batch_step_execution" })
    void schemaTablesAreCore(String tableName) {
        assertTrue(DatatableCoreTableGuard.isCoreTable(tableName), tableName);
    }

    @Test
    void customTableWithReservedPrefixIsReservedButNotCore() {
        assertTrue(DatatableCoreTableGuard.isReservedName("m_client_notes"));
        assertFalse(DatatableCoreTableGuard.isCoreTable("m_client_notes"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "extra_client_details", "dt_m_client_abcde", "dt_savings_transaction_x", "ppi_kenya_2005", "Spouse Details",
            "commands_extra", "jobs", "mobile_client_info", "accounts_extra" })
    void customDatatablesAreAllowed(String tableName) {
        assertFalse(DatatableCoreTableGuard.isReservedName(tableName), tableName);
        assertFalse(DatatableCoreTableGuard.isCoreTable(tableName), tableName);
    }

    @Test
    void nullIsNotACoreTable() {
        assertFalse(DatatableCoreTableGuard.isCoreTable(null));
        assertFalse(DatatableCoreTableGuard.isReservedName(null));
    }

    @Test
    void everyTableDefinedInLiquibaseChangelogsIsRejected() throws IOException {
        Set<String> tables = new TreeSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String location : List.of("classpath*:db/changelog/**/*.xml", "classpath*:db/changelog/**/*.sql")) {
            for (Resource changelog : resolver.getResources(location)) {
                try (InputStream in = changelog.getInputStream()) {
                    tables.addAll(DatatableCoreTableGuard.extractTableNames(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        }
        assertTrue(tables.containsAll(List.of("m_appuser", "x_registered_table", "twofactor_access_token", "batch_job_instance")),
                "Liquibase changelogs should be on the test classpath");

        assertTrue(DatatableCoreTableGuard.loadSchemaTables().containsAll(List.of("m_appuser", "x_registered_table")));
        Predicate<String> guarded = t -> DatatableCoreTableGuard.isCoreTable(t) && DatatableCoreTableGuard.isReservedName(t);
        List<String> unguarded = tables.stream().filter(guarded.negate()).toList();
        assertTrue(unguarded.isEmpty(), "Core tables not covered by DatatableCoreTableGuard: " + unguarded);
    }
}
