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
package org.apache.fineract.integrationtests.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import okhttp3.MultipartBody.Part;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.services.DocumentsApiFixed;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.client.util.Parts;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import retrofit2.Call;

/**
 * Document routes require document permissions for every entity type, and unsupported entity types are rejected.
 */
class DocumentPermissionTest extends IntegrationTest {

    private static final String UNSUPPORTED = "notes";
    private static final String CLIENTS = "clients";
    private static final String PASSWORD = "AKleRbDhK421$";

    final File testFile = new File(getClass().getResource("/michael.vorburger-crepes.jpg").getFile());

    Long clientId;
    Long roleId;
    DocumentsApiFixed admin;
    DocumentsApiFixed user;

    @BeforeAll
    void setup() {
        clientId = new ClientTest().getClientId();
        admin = fineractClient().documents;

        final var role = new PostRolesRequest().name(Utils.uniqueRandomStringGenerator("DOC_ROLE_", 10)).description("No documents");
        roleId = ok(fineractClient().roles.createRole(role)).getResourceId();
        grant("READ_REPORT");

        final String userName = Utils.uniqueRandomStringGenerator("docUser", 6);
        final var request = new PostUsersRequest().addRolesItem(roleId).email(userName + "@whatever.mifos.org")
                .firstname(Utils.randomFirstNameGenerator()).lastname(Utils.randomLastNameGenerator()).repeatPassword(PASSWORD)
                .sendPasswordToEmail(false).officeId(1L).username(userName).password(PASSWORD);
        ok(fineractClient().users.create16(request));
        user = newFineractClient(userName, PASSWORD).documents;
    }

    @Test
    @Order(1)
    void userWithoutDocumentPermissionsIsForbiddenForUnsupportedEntityType() {
        assertEquals(403, status(user.createDocument(UNSUPPORTED, 1L, file(), "name", "description")));
        assertEquals(403, status(user.retrieveAllDocuments(UNSUPPORTED, 1L)));
        assertEquals(403, status(user.getDocument(UNSUPPORTED, 1L, 1L)));
        assertEquals(403, status(user.deleteDocument(UNSUPPORTED, 1L, 1L)));
    }

    @Test
    @Order(2)
    void userWithoutDocumentPermissionsIsForbiddenForDocumentSubResources() {
        final Long documentId = ok(admin.createDocument(CLIENTS, clientId, file(), "name", "description")).getResourceId();

        assertEquals(403, status(user.createDocument(CLIENTS, clientId, file(), "name", "description")));
        assertEquals(403, status(user.getDocument(CLIENTS, clientId, documentId)));
        assertEquals(403, status(user.downloadFile(CLIENTS, clientId, documentId)));
        assertEquals(403, status(user.deleteDocument(CLIENTS, clientId, documentId)));

        ok(admin.deleteDocument(CLIENTS, clientId, documentId));
    }

    @Test
    @Order(3)
    void unsupportedEntityTypeIsRejected() {
        assertEquals(400, status(admin.createDocument(UNSUPPORTED, 1L, file(), "name", "description")));
        assertEquals(400, status(admin.retrieveAllDocuments(UNSUPPORTED, 1L)));
    }

    @Test
    @Order(4)
    void userWithCreateDocumentPermissionCanOnlyUseSupportedEntityTypes() {
        grant("CREATE_DOCUMENT");

        assertEquals(400, status(user.createDocument(UNSUPPORTED, 1L, file(), "name", "description")));

        final var response = Calls.executeU(user.createDocument(CLIENTS, clientId, file(), "name", "description"));
        assertEquals(200, response.code());

        ok(admin.deleteDocument(CLIENTS, clientId, response.body().getResourceId()));
    }

    private void grant(final String permission) {
        final var request = new PutRolesRoleIdPermissionsRequest().putPermissionsItem(permission, true);
        ok(fineractClient().roles.updateRolePermissions(roleId, request));
    }

    private Part file() {
        return Parts.fromFile(testFile);
    }

    private static int status(final Call<?> call) {
        return Calls.executeU(call).code();
    }
}
