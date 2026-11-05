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
package org.apache.fineract.integrationtests.common.shares;

import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShareAccountReadPermissionIntegrationTest {

    private static final String PASSWORD = UserHelper.SIMPLE_USER_PASSWORD;
    private static final String SHARE_ACCOUNTS_URL = "/fineract-provider/api/v1/accounts/share";

    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.requestSpec.header("Fineract-Platform-TenantId", "default");
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
    }

    @Test
    public void staffUserNeedsReadShareAccountPermissionToReadShareAccounts() {
        final Integer shareAccountId = createShareAccount();
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec);
        final String username = Utils.uniqueRandomStringGenerator("ShareReader_", 5);
        UserHelper.createUser(requestSpec, responseSpec, roleId, staffId, username, PASSWORD, "resourceId");
        final RequestSpecification userSpec = basicAuthSpec(username);

        final ResponseSpecification forbidden = new ResponseSpecBuilder().expectStatusCode(403).build();
        Utils.performServerGet(userSpec, forbidden, SHARE_ACCOUNTS_URL + "/" + shareAccountId + "?" + Utils.TENANT_IDENTIFIER);
        Utils.performServerGet(userSpec, forbidden, SHARE_ACCOUNTS_URL + "?" + Utils.TENANT_IDENTIFIER);
        Utils.performServerGet(userSpec, forbidden, SHARE_ACCOUNTS_URL + "/template?" + Utils.TENANT_IDENTIFIER);

        RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, Map.of("READ_SHAREACCOUNT", true));

        final Integer returnedId = Utils.performServerGet(userSpec, responseSpec,
                SHARE_ACCOUNTS_URL + "/" + shareAccountId + "?" + Utils.TENANT_IDENTIFIER, "id");
        Assertions.assertEquals(shareAccountId, returnedId);
        final List<Integer> listedIds = Utils.performServerGet(userSpec, responseSpec,
                SHARE_ACCOUNTS_URL + "?limit=1000&" + Utils.TENANT_IDENTIFIER, "pageItems.id");
        Assertions.assertNotNull(listedIds);
    }

    @Test
    public void selfServiceUserCannotReachShareAccountsThroughSelfTypeSegment() {
        final Integer shareAccountId = createShareAccount();
        final Integer clientId = ClientHelper.createClient(requestSpec, responseSpec);
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec);
        final String username = Utils.uniqueRandomStringGenerator("SelfShare_", 5);
        final Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("firstname", "Self");
        user.put("lastname", "Service");
        user.put("email", "self@fineract.example");
        user.put("officeId", 1);
        user.put("staffId", staffId);
        user.put("roles", List.of(RolesHelper.SUPER_USER_ROLE_ID));
        user.put("sendPasswordToEmail", false);
        user.put("password", PASSWORD);
        user.put("repeatPassword", PASSWORD);
        user.put("passwordNeverExpires", true);
        user.put("isSelfServiceUser", true);
        user.put("clients", List.of(clientId));
        Utils.performServerPost(requestSpec, responseSpec, "/fineract-provider/api/v1/users?" + Utils.TENANT_IDENTIFIER,
                new Gson().toJson(user), "resourceId");

        final ResponseSpecification notFound = new ResponseSpecBuilder().expectStatusCode(404).build();
        Utils.performServerGet(basicAuthSpec(username), notFound,
                "/fineract-provider/api/v1/accounts/self/" + shareAccountId + "?" + Utils.TENANT_IDENTIFIER);
    }

    private RequestSpecification basicAuthSpec(final String username) {
        final RequestSpecification spec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        final String credentials = Base64.getEncoder().encodeToString((username + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        spec.header("Authorization", "Basic " + credentials);
        spec.header("Fineract-Platform-TenantId", "default");
        return spec;
    }

    private Integer createShareAccount() {
        final Integer productId = ShareProductTransactionHelper.createShareProduct(new ShareProductHelper().build(), requestSpec,
                responseSpec);
        final Integer clientId = ClientHelper.createClient(requestSpec, responseSpec);
        final Integer savingsAccountId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "1000");
        final String json = new ShareAccountHelper().withClientId(String.valueOf(clientId)).withProductId(String.valueOf(productId))
                .withExternalId(Utils.uniqueRandomStringGenerator("Ext_", 6)).withSavingsAccountId(String.valueOf(savingsAccountId))
                .withSubmittedDate("01 January 2016").withApplicationDate("01 January 2016").withRequestedShares("25").build();
        return ShareAccountTransactionHelper.createShareAccount(json, requestSpec, responseSpec);
    }
}
