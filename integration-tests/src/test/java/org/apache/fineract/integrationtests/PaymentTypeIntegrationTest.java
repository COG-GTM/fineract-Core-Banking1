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
package org.apache.fineract.integrationtests;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.Map;
import org.apache.fineract.client.models.PaymentTypeCreateRequest;
import org.apache.fineract.client.models.PaymentTypeData;
import org.apache.fineract.client.models.PaymentTypeUpdateRequest;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PaymentTypeIntegrationTest {

    private static final String PAYMENTTYPE_URL = "/fineract-provider/api/v1/paymenttypes";
    private static final String USER_PASSWORD = "A1b2c3d4e5f$";

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private PaymentTypeHelper paymentTypeHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.paymentTypeHelper = new PaymentTypeHelper();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPaymentType() {
        String name = PaymentTypeHelper.randomNameGenerator("P_T", 5);
        String description = PaymentTypeHelper.randomNameGenerator("PT_Desc", 15);
        Boolean isCashPayment = true;
        Long position = 1L;

        var paymentTypesResponse = paymentTypeHelper.createPaymentType(
                new PaymentTypeCreateRequest().name(name).description(description).isCashPayment(isCashPayment).position(position));
        Long paymentTypeId = paymentTypesResponse.getResourceId();
        Assertions.assertNotNull(paymentTypeId);
        paymentTypeHelper.verifyPaymentTypeCreatedOnServer(paymentTypeId);
        PaymentTypeData paymentTypeResponse = paymentTypeHelper.retrieveById(paymentTypeId);
        Assertions.assertEquals(name, paymentTypeResponse.getName());
        Assertions.assertEquals(description, paymentTypeResponse.getDescription());
        Assertions.assertEquals(isCashPayment, paymentTypeResponse.getIsCashPayment());
        Assertions.assertEquals(position, paymentTypeResponse.getPosition());

        // Update Payment Type
        String newName = PaymentTypeHelper.randomNameGenerator("P_TU", 5);
        String newDescription = PaymentTypeHelper.randomNameGenerator("PTU_Desc", 15);
        Boolean isCashPaymentUpdatedValue = false;
        Long newPosition = 2L;

        paymentTypeHelper.updatePaymentType(paymentTypeId, new PaymentTypeUpdateRequest().name(newName).description(newDescription)
                .isCashPayment(isCashPaymentUpdatedValue).position(newPosition));
        var paymentTypeUpdatedResponse = paymentTypeHelper.retrieveById(paymentTypeId);
        Assertions.assertEquals(newName, paymentTypeUpdatedResponse.getName());
        Assertions.assertEquals(newDescription, paymentTypeUpdatedResponse.getDescription());
        Assertions.assertEquals(isCashPaymentUpdatedValue, paymentTypeUpdatedResponse.getIsCashPayment());
        Assertions.assertEquals(newPosition, paymentTypeUpdatedResponse.getPosition());

        // Delete
        var responseDelete = paymentTypeHelper.deletePaymentType(paymentTypeId);
        Long deletedPaymentTypeId = responseDelete.getResourceId();
        Assertions.assertEquals(paymentTypeId, deletedPaymentTypeId);
        ResponseSpecification responseSpecification = new ResponseSpecBuilder().expectStatusCode(404).build();
        paymentTypeHelper.retrieveById(requestSpec, responseSpecification, paymentTypeId);
    }

    @Test
    public void testPaymentTypeWritesRequirePermissions() {
        Long paymentTypeId = paymentTypeHelper.createPaymentType(new PaymentTypeCreateRequest()
                .name(PaymentTypeHelper.randomNameGenerator("P_T", 5)).description("permission check").isCashPayment(true).position(1L))
                .getResourceId();
        String paymentTypeUrl = PAYMENTTYPE_URL + "/" + paymentTypeId + "?" + Utils.TENANT_IDENTIFIER;
        String collectionUrl = PAYMENTTYPE_URL + "?" + Utils.TENANT_IDENTIFIER;
        String updateJson = "{\"name\":\"" + PaymentTypeHelper.randomNameGenerator("P_TU", 5)
                + "\",\"description\":\"changed\",\"isCashPayment\":false,\"position\":2}";
        String createJson = "{\"name\":\"" + PaymentTypeHelper.randomNameGenerator("P_TC", 5)
                + "\",\"description\":\"created\",\"isCashPayment\":false,\"position\":3}";
        ResponseSpecification forbidden = new ResponseSpecBuilder().expectStatusCode(403).build();

        RequestSpecification readOnlyUser = userWithPermissions(Map.of("READ_PAYMENTTYPE", true));
        Utils.performServerGet(readOnlyUser, responseSpec, collectionUrl);
        Utils.performServerGet(readOnlyUser, responseSpec, paymentTypeUrl);
        Utils.performServerPut(readOnlyUser, forbidden, paymentTypeUrl, updateJson);
        Utils.performServerDelete(readOnlyUser, forbidden, paymentTypeUrl, null);
        Utils.performServerPost(readOnlyUser, forbidden, collectionUrl, createJson);

        RequestSpecification noPermissionUser = userWithPermissions(Map.of());
        Utils.performServerGet(noPermissionUser, forbidden, collectionUrl);
        Utils.performServerGet(noPermissionUser, forbidden, paymentTypeUrl);

        PaymentTypeData unchanged = paymentTypeHelper.retrieveById(paymentTypeId);
        Assertions.assertEquals("permission check", unchanged.getDescription());
        Assertions.assertEquals(Boolean.TRUE, unchanged.getIsCashPayment());

        RequestSpecification updateOnlyUser = userWithPermissions(Map.of("READ_PAYMENTTYPE", true, "UPDATE_PAYMENTTYPE", true));
        Utils.performServerPost(updateOnlyUser, forbidden, collectionUrl, createJson);
        Utils.performServerDelete(updateOnlyUser, forbidden, paymentTypeUrl, null);
        Utils.performServerPut(updateOnlyUser, responseSpec, paymentTypeUrl, updateJson);
        PaymentTypeData updated = paymentTypeHelper.retrieveById(paymentTypeId);
        Assertions.assertEquals("changed", updated.getDescription());
        Assertions.assertEquals(Boolean.FALSE, updated.getIsCashPayment());

        RequestSpecification createOnlyUser = userWithPermissions(Map.of("CREATE_PAYMENTTYPE", true));
        Integer createdId = Utils.performServerPost(createOnlyUser, responseSpec, collectionUrl, createJson, "resourceId");
        Assertions.assertNotNull(createdId);

        RequestSpecification deleteOnlyUser = userWithPermissions(Map.of("DELETE_PAYMENTTYPE", true));
        Utils.performServerPut(deleteOnlyUser, forbidden, paymentTypeUrl, updateJson);
        Utils.performServerDelete(deleteOnlyUser, responseSpec, paymentTypeUrl, null);
        Utils.performServerDelete(deleteOnlyUser, responseSpec, PAYMENTTYPE_URL + "/" + createdId + "?" + Utils.TENANT_IDENTIFIER, null);
        paymentTypeHelper.retrieveById(requestSpec, new ResponseSpecBuilder().expectStatusCode(404).build(), paymentTypeId);
    }

    private RequestSpecification userWithPermissions(final Map<String, Boolean> permissions) {
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec);
        if (!permissions.isEmpty()) {
            RolesHelper.addPermissionsToRole(requestSpec, responseSpec, roleId, permissions);
        }
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec);
        final String username = Utils.uniqueRandomStringGenerator("ptuser", 8);
        UserHelper.createUser(requestSpec, responseSpec, roleId, staffId, username, USER_PASSWORD, "resourceId");
        final RequestSpecification userSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        userSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, USER_PASSWORD));
        return userSpec;
    }
}
