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
package org.apache.fineract.infrastructure.documentmanagement.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.documentmanagement.exception.DocumentParentEntityNotFoundException;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures the entity a document is attached to exists and lies within the authenticated user's office hierarchy.
 */
@Component
@RequiredArgsConstructor
public class DocumentParentEntityAccessValidator {

    static final String ENTITY_TYPE_IMPORT = "import";

    private static final String CLIENT_OFFICES = " join m_office o on o.id in (c.office_id, c.transfer_to_office_id)";

    static final Map<String, String> OFFICE_HIERARCHY_QUERIES = Map.of( //
            "clients", "select o.hierarchy from m_client c" + CLIENT_OFFICES + " where c.id = ?", //
            "client_identifiers",
            "select o.hierarchy from m_client_identifier ci join m_client c on c.id = ci.client_id" + CLIENT_OFFICES
                    + " where ci.id = ?", //
            "staff", "select o.hierarchy from m_staff s join m_office o on o.id = s.office_id where s.id = ?", //
            "groups", "select o.hierarchy from m_group g join m_office o on o.id = g.office_id where g.id = ?", //
            "loans",
            "select o.hierarchy from m_loan l left join m_client c on c.id = l.client_id left join m_group g on g.id = l.group_id"
                    + " join m_office o on o.id in (c.office_id, c.transfer_to_office_id, g.office_id) where l.id = ?", //
            "savings",
            "select o.hierarchy from m_savings_account sa left join m_client c on c.id = sa.client_id"
                    + " left join m_group g on g.id = sa.group_id"
                    + " join m_office o on o.id in (c.office_id, c.transfer_to_office_id, g.office_id) where sa.id = ?");

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;

    /**
     * Rejects parent entity types that cannot be reached through the public documents API (e.g. bulk import files).
     */
    public static void requireOfficeScopedEntityType(final String entityType, final Long entityId) {
        if (entityType == null || !OFFICE_HIERARCHY_QUERIES.containsKey(entityType.toLowerCase(Locale.ROOT))) {
            throw new DocumentParentEntityNotFoundException(entityType, entityId);
        }
    }

    public void validate(final String entityType, final Long entityId) {
        final String normalizedType = entityType == null ? null : entityType.toLowerCase(Locale.ROOT);

        // bulk import files are not owned by an office
        if (ENTITY_TYPE_IMPORT.equals(normalizedType)) {
            return;
        }

        final String sql = normalizedType == null ? null : OFFICE_HIERARCHY_QUERIES.get(normalizedType);
        if (sql == null || entityId == null) {
            throw new DocumentParentEntityNotFoundException(entityType, entityId);
        }

        final List<String> hierarchies = jdbcTemplate.queryForList(sql, String.class, entityId);
        if (hierarchies.isEmpty()) {
            throw new DocumentParentEntityNotFoundException(entityType, entityId);
        }

        final String userHierarchy = context.officeHierarchy();
        if (hierarchies.stream().filter(Objects::nonNull).noneMatch(hierarchy -> hierarchy.startsWith(userHierarchy))) {
            throw new NoAuthorizationException("The user doesn't have enough permissions to access the documents of this resource.");
        }
    }
}
