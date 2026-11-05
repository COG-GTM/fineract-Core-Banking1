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

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.io.File;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.util.Parts;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.NotesHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Per-item document and note routes must enforce the same DOCUMENT / NOTE permissions as the list routes.
 */
class DocumentNotePermissionTest extends IntegrationTest {

    private static final String API = "/fineract-provider/api/v1";
    private static final String PASSWORD = "AKleRbDhK421$";

    private final File testFile = new File(getClass().getResource("/michael.vorburger-crepes.jpg").getFile());

    private Long clientId;
    private Long documentId;
    private Integer noteId;
    private Long roleId;
    private String userName;

    @BeforeAll
    void setup() {
        Utils.initializeRESTAssured();
        RequestSpecification adminSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build().header("Authorization",
                "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());

        clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        documentId = ok(fineractClient().documents.createDocument("clients", clientId, Parts.fromFile(testFile), "Passport", "secret"))
                .getResourceId();
        noteId = NotesHelper.createClientNote(adminSpec, new ResponseSpecBuilder().expectStatusCode(200).build(), clientId.intValue(),
                "{\"note\": \"internal note\"}");

        roleId = ok(fineractClient().roles
                .createRole(new PostRolesRequest().name(Utils.uniqueRandomStringGenerator("NO_DOC_ROLE_", 6)).description("no doc")))
                .getResourceId();
        userName = Utils.uniqueRandomStringGenerator("nodocuser", 6);
        ok(fineractClient().users.create16(new PostUsersRequest().addRolesItem(roleId).email(userName + "@whatever.mifos.org")
                .firstname(Utils.randomFirstNameGenerator()).lastname(Utils.randomLastNameGenerator()).repeatPassword(PASSWORD)
                .sendPasswordToEmail(false).officeId(1L).username(userName).password(PASSWORD)));
    }

    @Test
    @Order(1)
    void userWithoutPermissionsIsDeniedOnPerItemRoutes() {
        String document = API + "/clients/" + clientId + "/documents/" + documentId;
        String note = API + "/clients/" + clientId + "/notes/" + noteId;

        assertStatus(403, "GET", document);
        assertStatus(403, "GET", document + "/attachment");
        assertStatus(403, "HEAD", document);
        assertStatus(403, "PUT", document);
        assertStatus(403, "DELETE", document);
        assertStatus(403, "GET", note);
        assertStatus(403, "PUT", note);
        assertStatus(403, "DELETE", note);
        assertStatus(403, "GET", API + "/savingsTransactions/1/notes/1");
        assertStatus(403, "GET", API + "/someEntity/" + clientId + "/documents/" + documentId);
    }

    @Test
    @Order(2)
    void readPermissionsAllowReadsButNotWrites() {
        ok(fineractClient().roles.updateRolePermissions(roleId, new PutRolesRoleIdPermissionsRequest()
                .putPermissionsItem("READ_DOCUMENT", true).putPermissionsItem("READ_CLIENTNOTE", true)));

        String document = API + "/clients/" + clientId + "/documents/" + documentId;
        String note = API + "/clients/" + clientId + "/notes/" + noteId;

        assertStatus(200, "GET", document);
        assertStatus(200, "GET", document + "/attachment");
        assertStatus(200, "GET", note);
        assertStatus(200, "HEAD", document);
        assertStatus(200, "HEAD", note);
        assertStatus(403, "DELETE", document);
        assertStatus(403, "DELETE", note);
        assertStatus(403, "PUT", note);
    }

    private void assertStatus(int expected, String method, String path) {
        int actual = RestAssured.given().auth().preemptive().basic(userName, PASSWORD).contentType(ContentType.JSON)
                .body(method.equals("PUT") ? "{\"note\": \"tampered\"}" : "").request(method, path + "?" + Utils.TENANT_IDENTIFIER)
                .getStatusCode();
        assertThat(actual).as(method + " " + path).isEqualTo(expected);
    }
}
