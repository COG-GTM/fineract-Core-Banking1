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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that client and staff images can only be read, replaced or deleted by users whose office hierarchy covers
 * the office of the owning entity.
 */
public class ImageOfficeScopingTest {

    private static final String DATE = "01 January 2023";
    private static final String ORIGINAL_IMAGE = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    private static final String REPLACEMENT_IMAGE = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private RequestSpecification adminSpec;
    private ResponseSpecification okSpec;
    private Integer officeA;
    private Integer officeB;
    private Integer roleId;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        adminSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        adminSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        okSpec = new ResponseSpecBuilder().expectStatusCode(200).build();

        OfficeHelper officeHelper = new OfficeHelper(adminSpec, okSpec);
        officeA = officeHelper.createOffice(DATE);
        officeB = officeHelper.createOffice(DATE);

        roleId = RolesHelper.createRole(adminSpec, okSpec);
        Map<String, Boolean> permissions = new HashMap<>();
        for (String code : List.of("READ_DOCUMENT", "CREATE_CLIENTIMAGE", "DELETE_CLIENTIMAGE", "CREATE_STAFFIMAGE", "DELETE_STAFFIMAGE")) {
            permissions.put(code, true);
        }
        RolesHelper.addPermissionsToRole(adminSpec, okSpec, roleId, permissions);
    }

    @Test
    public void userFromOtherOfficeCannotReadReplaceOrDeleteClientImage() {
        Integer clientId = ClientHelper.createClient(adminSpec, okSpec, DATE, String.valueOf(officeA));
        assertOfficeScoping(imagesUrl("clients", clientId));
    }

    @Test
    public void userFromOtherOfficeCannotReadReplaceOrDeleteStaffImage() {
        Map<String, Object> staff = StaffHelper.getMapWithJoiningDate();
        staff.put("officeId", officeA);
        staff.put("firstname", Utils.uniqueRandomStringGenerator("img_", 5));
        staff.put("lastname", Utils.uniqueRandomStringGenerator("scope_", 5));
        staff.put("isLoanOfficer", false);
        String staffJson = new Gson().toJson(staff);
        Integer staffId = (Integer) StaffHelper.createStaffWithJson(adminSpec, okSpec, staffJson).get("resourceId");
        assertOfficeScoping(imagesUrl("staff", staffId));
    }

    private void assertOfficeScoping(String url) {
        replace(adminSpec, url, ORIGINAL_IMAGE).statusCode(200);

        RequestSpecification officeBUser = userSpec(officeB);
        given(officeBUser).accept("text/plain").get(url).then().statusCode(404);
        create(officeBUser, url, REPLACEMENT_IMAGE).statusCode(404);
        given(officeBUser).delete(url).then().statusCode(404);

        // the image is untouched and remains readable for users of the owning office
        RequestSpecification officeAUser = userSpec(officeA);
        given(officeAUser).accept("text/plain").get(url).then().statusCode(200).body(equalTo(ORIGINAL_IMAGE));
        create(officeAUser, url, REPLACEMENT_IMAGE).statusCode(200);
        given(adminSpec).accept("text/plain").get(url).then().statusCode(200).body(equalTo(REPLACEMENT_IMAGE));
        given(officeAUser).delete(url).then().statusCode(200);
    }

    private ValidatableResponse replace(RequestSpecification spec, String url, String dataUrl) {
        return given(spec).contentType(ContentType.TEXT).body(dataUrl).put(url).then();
    }

    private ValidatableResponse create(RequestSpecification spec, String url, String dataUrl) {
        return given(spec).contentType(ContentType.TEXT).body(dataUrl).post(url).then();
    }

    private RequestSpecification userSpec(Integer officeId) {
        String username = Utils.uniqueRandomStringGenerator("ImgScope", 6);
        String password = UserHelper.SIMPLE_USER_PASSWORD;
        UserHelper.createUser(adminSpec, okSpec, new PostUsersRequest().username(username).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).email("imagescope@example.com").password(password).repeatPassword(password)
                .sendPasswordToEmail(false).passwordNeverExpires(true).officeId(officeId.longValue()).roles(List.of(roleId.longValue())));
        String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        RequestSpecification spec = new RequestSpecBuilder().build();
        spec.header("Authorization", "Basic " + basic);
        return spec;
    }

    private static String imagesUrl(String entityType, Integer entityId) {
        return "/fineract-provider/api/v1/" + entityType + "/" + entityId + "/images?" + Utils.TENANT_IDENTIFIER;
    }
}
