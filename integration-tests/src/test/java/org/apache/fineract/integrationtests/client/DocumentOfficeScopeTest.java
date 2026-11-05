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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.apache.fineract.client.models.DocumentData;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostOfficesRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.util.CallFailedRuntimeException;
import org.apache.fineract.client.util.FineractClient;
import org.apache.fineract.client.util.Parts;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import retrofit2.Call;

/**
 * Verifies that documents are only reachable for entities inside the caller's office hierarchy and that the document id
 * must belong to the entity named in the URL.
 */
class DocumentOfficeScopeTest extends IntegrationTest {

    private static final long SUPER_USER_ROLE_ID = 1L;
    private static final String PASSWORD = "AKleRbDhK421$";

    private final File testFile = new File(getClass().getResource("/michael.vorburger-crepes.jpg").getFile());

    private Long officeAClientId;
    private Long officeADocumentId;
    private Long officeBClientId;
    private Long officeBDocumentId;
    private FineractClient officeBUser;

    @BeforeAll
    void setUpOffices() {
        final Long officeAId = createOffice();
        final Long officeBId = createOffice();

        officeAClientId = createClient(officeAId);
        officeBClientId = createClient(officeBId);
        officeADocumentId = createDocument(fineractClient(), officeAClientId);
        officeBDocumentId = createDocument(fineractClient(), officeBClientId);

        final String username = Utils.uniqueRandomStringGenerator("docOfficeB", 6);
        ok(fineractClient().users.create16(new PostUsersRequest().username(username).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).email(username + "@whatever.mifos.org").password(PASSWORD)
                .repeatPassword(PASSWORD).sendPasswordToEmail(false).officeId(officeBId).roles(List.of(SUPER_USER_ROLE_ID))));
        officeBUser = newFineractClient(username, PASSWORD);
    }

    @Test
    @Order(1)
    void userCanManageDocumentsOfOwnOffice() {
        assertThat(ok(officeBUser.documents.retrieveAllDocuments("clients", officeBClientId))).extracting(DocumentData::getId)
                .contains(officeBDocumentId);
        assertThat(ok(officeBUser.documents.getDocument("clients", officeBClientId, officeBDocumentId)).getId())
                .isEqualTo(officeBDocumentId);
        okR(officeBUser.documents.downloadFile("clients", officeBClientId, officeBDocumentId)).body().close();
        ok(officeBUser.documents.updateDocument("clients", officeBClientId, officeBDocumentId, null, "renamed", null));
    }

    @Test
    @Order(2)
    void userCannotReadDocumentsOfAnotherOffice() {
        assertForbidden(officeBUser.documents.retrieveAllDocuments("clients", officeAClientId));
        assertForbidden(officeBUser.documents.getDocument("clients", officeAClientId, officeADocumentId));
        assertForbidden(officeBUser.documents.downloadFile("clients", officeAClientId, officeADocumentId));
    }

    @Test
    @Order(3)
    void userCannotCreateUpdateOrDeleteDocumentsOfAnotherOffice() {
        assertForbidden(officeBUser.documents.createDocument("clients", officeAClientId, Parts.fromFile(testFile), "x", null));
        assertForbidden(officeBUser.documents.updateDocument("clients", officeAClientId, officeADocumentId, null, "hijacked", null));
        assertForbidden(officeBUser.documents.deleteDocument("clients", officeAClientId, officeADocumentId));
    }

    @Test
    @Order(4)
    void documentIdMustBelongToEntityInUrl() {
        assertNotFound(officeBUser.documents.getDocument("clients", officeBClientId, officeADocumentId));
        assertNotFound(officeBUser.documents.downloadFile("clients", officeBClientId, officeADocumentId));
        assertNotFound(officeBUser.documents.updateDocument("clients", officeBClientId, officeADocumentId, null, "hijacked", null));
        assertNotFound(officeBUser.documents.deleteDocument("clients", officeBClientId, officeADocumentId));
    }

    @Test
    @Order(5)
    void officeADocumentIsUntouched() {
        final DocumentData doc = ok(fineractClient().documents.getDocument("clients", officeAClientId, officeADocumentId));
        assertThat(doc.getName()).isEqualTo("Test");
        assertThat(doc.getParentEntityId()).isEqualTo(officeAClientId);
    }

    @Test
    @Order(6)
    void nonOfficeScopedEntityTypeIsNotExposed() {
        assertNotFound(fineractClient().documents.retrieveAllDocuments("IMPORT", 1L));
    }

    private Long createOffice() {
        return ok(fineractClient().offices.createOffice(new PostOfficesRequest().name(Utils.randomStringGenerator("DocOffice_", 6))
                .parentId(1L).openingDate(LocalDate.now(ZoneId.of("UTC"))).dateFormat("yyyy-MM-dd").locale("en_US"))).getOfficeId();
    }

    private Long createClient(final Long officeId) {
        return ok(fineractClient().clients.create6(new PostClientsRequest().legalFormId(1L).officeId(officeId)
                .fullname(Utils.randomStringGenerator("DocClient_", 6)).dateFormat(Utils.DATE_FORMAT).locale("en_US"))).getClientId();
    }

    private Long createDocument(final FineractClient client, final Long clientId) {
        return ok(client.documents.createDocument("clients", clientId, Parts.fromFile(testFile), "Test", "office scope")).getResourceId();
    }

    private void assertForbidden(final Call<?> call) {
        assertStatus(403, call);
    }

    private void assertNotFound(final Call<?> call) {
        assertStatus(404, call);
    }

    private void assertStatus(final int expected, final Call<?> call) {
        final CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class, () -> ok(call));
        assertEquals(expected, exception.getResponse().code());
    }
}
