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
package org.apache.fineract.integrationtests.investor.externalassetowner;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.List;
import java.util.Map;
import org.apache.fineract.integrationtests.BaseLoanIntegrationTest;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class ExternalAssetOwnerReadPermissionTest extends BaseLoanIntegrationTest {

    private static final String BASE_URL = "/fineract-provider/api/v1/external-asset-owners";
    private static final String OWNER_EXTERNAL_ID = Utils.uniqueRandomStringGenerator("eao", 10);

    private static final List<String> OWNER_AND_TRANSFER_READ_URLS = List.of(BASE_URL + "?" + Utils.TENANT_IDENTIFIER,
            BASE_URL + "/transfers?loanId=1&" + Utils.TENANT_IDENTIFIER,
            BASE_URL + "/transfers/active-transfer?loanId=1&" + Utils.TENANT_IDENTIFIER);
    private static final List<String> JOURNAL_ENTRY_READ_URLS = List.of(
            BASE_URL + "/transfers/1/journal-entries?" + Utils.TENANT_IDENTIFIER,
            BASE_URL + "/owners/external-id/" + OWNER_EXTERNAL_ID + "/journal-entries?" + Utils.TENANT_IDENTIFIER);

    private final ResponseSpecification forbidden = createResponseSpecification(Matchers.is(403));
    private final ResponseSpecification allowed = createResponseSpecification(Matchers.oneOf(200, 204));

    @Test
    public void readEndpointsRequirePermissions() {
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
        final RequestSpecification userSpec = createUserWithRole(roleId);

        for (String url : OWNER_AND_TRANSFER_READ_URLS) {
            Utils.performServerGet(userSpec, forbidden, url);
        }
        for (String url : JOURNAL_ENTRY_READ_URLS) {
            Utils.performServerGet(userSpec, forbidden, url);
        }
        Utils.performServerPost(userSpec, forbidden, BASE_URL + "/search?" + Utils.TENANT_IDENTIFIER, "{}");

        RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, Map.of("READ_EXTERNAL_ASSET_OWNER", true));

        for (String url : OWNER_AND_TRANSFER_READ_URLS) {
            Utils.performServerGet(userSpec, allowed, url);
        }
        for (String url : JOURNAL_ENTRY_READ_URLS) {
            Utils.performServerGet(userSpec, forbidden, url);
        }

        RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, Map.of("READ_JOURNALENTRY", true));

        for (String url : JOURNAL_ENTRY_READ_URLS) {
            Utils.performServerGet(userSpec, allowed, url);
        }
    }

    private RequestSpecification createUserWithRole(Integer roleId) {
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec);
        final String username = Utils.uniqueRandomStringGenerator("eaoReader", 6);
        final String password = Utils.randomStringGenerator("Aa1$", 12);
        UserHelper.createUser(requestSpec, responseSpec, roleId, staffId, username, password, "resourceId");
        final String authKey = Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, password);
        return new RequestSpecBuilder().setContentType(ContentType.JSON).build().header("Authorization", "Basic " + authKey);
    }
}
