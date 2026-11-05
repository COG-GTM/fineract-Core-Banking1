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
package org.apache.fineract.integrationtests.organization.teller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersCashiersIdTransactionsResponse;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CashierSummaryAndTransactionsTest {

    private CashierTransactionsHelper cashierTransactionsHelper;

    private ResponseSpecification responseSpecification;
    private RequestSpecification requestSpecification;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();

        requestSpecification = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpecification.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        responseSpecification = new ResponseSpecBuilder().expectStatusCode(200).build();
        cashierTransactionsHelper = new CashierTransactionsHelper(requestSpecification, responseSpecification);
        StaffHelper.createStaff(requestSpecification, responseSpecification);
        cashierTransactionsHelper.createTeller(requestSpecification, responseSpecification);
        cashierTransactionsHelper.createCashier(requestSpecification, responseSpecification);

    }

    @Test
    public void testGetCashierTransactions() {
        Long tellerId = 1L;
        Long cashierId = 1L;

        final GetTellersTellerIdCashiersCashiersIdTransactionsResponse result = cashierTransactionsHelper
                .getTellersTellerIdCashiersCashiersIdTransactionsResponse(tellerId, cashierId, "UGX", 0, 0, null, null);
        assertNotNull(result);
    }


    @Test
    public void testTellerReadsRequireReadTellerPermission() {
        final Integer roleId = RolesHelper.createRole(requestSpecification, responseSpecification);
        final Integer staffId = StaffHelper.createStaff(requestSpecification, responseSpecification);
        final String username = Utils.uniqueRandomStringGenerator("noTellerRead", 5);
        UserHelper.createUser(requestSpecification, responseSpecification, roleId, staffId, username, UserHelper.SIMPLE_USER_PASSWORD,
                "resourceId");

        final RequestSpecification noPermissionRequest = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        noPermissionRequest.header("Authorization",
                "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, UserHelper.SIMPLE_USER_PASSWORD));
        final ResponseSpecification forbidden = new ResponseSpecBuilder().expectStatusCode(403).build();

        Utils.performServerGet(noPermissionRequest, forbidden, "/fineract-provider/api/v1/tellers?" + Utils.TENANT_IDENTIFIER, null);
        Utils.performServerGet(noPermissionRequest, forbidden, "/fineract-provider/api/v1/tellers/1/cashiers?" + Utils.TENANT_IDENTIFIER,
                null);
        Utils.performServerGet(noPermissionRequest, forbidden,
                "/fineract-provider/api/v1/tellers/1/cashiers/1/summaryandtransactions?currencyCode=UGX&" + Utils.TENANT_IDENTIFIER, null);
    }

}
