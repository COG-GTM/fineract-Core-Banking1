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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.apache.fineract.infrastructure.documentmanagement.exception.DocumentParentEntityNotFoundException;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DocumentParentEntityAccessValidatorTest {

    private static final String NORTH_BRANCH = ".1.";
    private static final String SOUTH_BRANCH = ".2.";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;

    private DocumentParentEntityAccessValidator underTest;

    @BeforeEach
    void setUp() {
        underTest = new DocumentParentEntityAccessValidator(jdbcTemplate, context);
    }

    @ParameterizedTest
    @ValueSource(strings = { "clients", "client_identifiers", "staff", "groups", "loans", "savings" })
    void allowsEntityInsideUserOfficeHierarchy(final String entityType) {
        mockHierarchies(entityType, 4711L, NORTH_BRANCH);
        when(context.officeHierarchy()).thenReturn(NORTH_BRANCH);

        assertDoesNotThrow(() -> underTest.validate(entityType, 4711L));
    }

    @ParameterizedTest
    @ValueSource(strings = { "clients", "client_identifiers", "staff", "groups", "loans", "savings" })
    void rejectsEntityOutsideUserOfficeHierarchy(final String entityType) {
        mockHierarchies(entityType, 4711L, NORTH_BRANCH);
        when(context.officeHierarchy()).thenReturn(SOUTH_BRANCH);

        assertThrows(NoAuthorizationException.class, () -> underTest.validate(entityType, 4711L));
    }

    @Test
    void headOfficeUserCanAccessChildOffice() {
        mockHierarchies("clients", 1L, ".2.5.");
        when(context.officeHierarchy()).thenReturn(".");

        assertDoesNotThrow(() -> underTest.validate("clients", 1L));
    }

    @Test
    void childOfficeUserCannotAccessParentOffice() {
        mockHierarchies("clients", 1L, ".");
        when(context.officeHierarchy()).thenReturn(".2.");

        assertThrows(NoAuthorizationException.class, () -> underTest.validate("clients", 1L));
    }

    @Test
    void allowsClientBeingTransferredToUserOffice() {
        mockHierarchies("clients", 1L, NORTH_BRANCH, SOUTH_BRANCH);
        when(context.officeHierarchy()).thenReturn(SOUTH_BRANCH);

        assertDoesNotThrow(() -> underTest.validate("clients", 1L));
    }

    @Test
    void entityTypeIsCaseInsensitive() {
        mockHierarchies("clients", 1L, NORTH_BRANCH);
        when(context.officeHierarchy()).thenReturn(SOUTH_BRANCH);

        assertThrows(NoAuthorizationException.class, () -> underTest.validate("CLIENTS", 1L));
    }

    @Test
    void rejectsMissingParentEntity() {
        mockHierarchies("loans", 99L);

        assertThrows(DocumentParentEntityNotFoundException.class, () -> underTest.validate("loans", 99L));
    }

    @Test
    void rejectsUnsupportedEntityType() {
        assertThrows(DocumentParentEntityNotFoundException.class, () -> underTest.validate("m_appuser", 1L));
        verifyNoInteractions(jdbcTemplate, context);
    }

    @Test
    void skipsBulkImportDocuments() {
        assertDoesNotThrow(() -> underTest.validate("IMPORT", 1L));
        verifyNoInteractions(jdbcTemplate, context);
    }

    @Test
    void publicApiRejectsNonOfficeScopedEntityTypes() {
        assertThrows(DocumentParentEntityNotFoundException.class,
                () -> DocumentParentEntityAccessValidator.requireOfficeScopedEntityType("IMPORT", 1L));
        assertThrows(DocumentParentEntityNotFoundException.class,
                () -> DocumentParentEntityAccessValidator.requireOfficeScopedEntityType(null, 1L));
        assertDoesNotThrow(() -> DocumentParentEntityAccessValidator.requireOfficeScopedEntityType("client_identifiers", 1L));
    }

    private void mockHierarchies(final String entityType, final Long entityId, final String... hierarchies) {
        when(jdbcTemplate.queryForList(eq(DocumentParentEntityAccessValidator.OFFICE_HIERARCHY_QUERIES.get(entityType)), eq(String.class),
                eq(entityId))).thenReturn(Arrays.asList(hierarchies));
    }
}
