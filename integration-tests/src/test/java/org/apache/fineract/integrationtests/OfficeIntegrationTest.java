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

import static io.restassured.RestAssured.given;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.fineract.client.models.GetOfficesResponse;
import org.apache.fineract.client.models.PutOfficesOfficeIdResponse;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.integrationtests.common.OfficeDomain;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import retrofit2.Response;

public class OfficeIntegrationTest {

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
    }

    @Test
    public void testOfficeModification() {
        OfficeHelper oh = new OfficeHelper(requestSpec, responseSpec);
        int officeId = oh.createOffice("01 July 2007");
        String name = Utils.uniqueRandomStringGenerator("New_Office_", 4);
        String date = "02 July 2007";
        String[] dateArr = { "2007", "7", "2" };

        oh.updateOffice(officeId, name, date);
        OfficeDomain newOffice = oh.retrieveOfficeByID(officeId);

        Assertions.assertTrue(name.equals(newOffice.getName()));
        Assertions.assertArrayEquals(dateArr, newOffice.getOpeningDate());
    }

    @Test
    public void testOfficeModificationWithExternalId() throws IOException {
        OfficeHelper oh = new OfficeHelper(requestSpec, responseSpec);
        String externalId = UUID.randomUUID().toString();
        int officeId = oh.createOfficeWithExternalId(externalId, "01 July 2007");
        String date = "02 July 2007";
        String name = Utils.uniqueRandomStringGenerator("New_Office_", 4);
        String[] dateArr = { "2007", "7", "2" };

        Response<PutOfficesOfficeIdResponse> updateResult = oh.updateOfficeUsingExternalId(externalId, name, date);
        Assertions.assertTrue(updateResult.isSuccessful());
        Assertions.assertEquals(officeId, updateResult.body().getOfficeId());
        OfficeDomain newOffice = oh.retrieveOfficeByID(officeId);

        Assertions.assertTrue(name.equals(newOffice.getName()));
        Assertions.assertArrayEquals(dateArr, newOffice.getOpeningDate());
    }

    @Test
    public void testOfficeModificationAndFetchWithExternalId() throws IOException {
        OfficeHelper oh = new OfficeHelper(requestSpec, responseSpec);
        String externalId = UUID.randomUUID().toString();
        int officeId = oh.createOfficeWithExternalId(externalId, "01 July 2007");
        String name = Utils.uniqueRandomStringGenerator("New_Office_", 4);
        String date = "02 July 2007";
        String[] dateArr = { "2007", "7", "2" };

        oh.updateOfficeUsingExternalId(externalId, name, date);
        Response<GetOfficesResponse> officeResult = oh.retrieveOfficeByExternalId(externalId);

        GetOfficesResponse newOffice = officeResult.body();

        Assertions.assertTrue(name.equals(newOffice.getName()));
        Assertions.assertTrue(DateUtils.isEqual(newOffice.getOpeningDate(), LocalDate.of(2007, 7, 2)));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            id,
            name, ASC
            openingDate, desc
            externalId,
            hierarchy,
            parentId,
            parentName,
            """)
    public void testRetrieveOfficesAcceptsAllowListedSorting(final String orderBy, final String sortOrder) {
        retrieveOfficesWithSorting(orderBy, sortOrder, 200);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            (select 1 from m_appuser) |
            o.id | ,(select 1)
            (case when o.id > (select ascii(substring(password,1,1)) from m_appuser where not id-1) then o.id else o.parent_id end) |
            unknown_column |
            name | ascending
            """)
    public void testRetrieveOfficesRejectsSortingOutsideAllowList(final String orderBy, final String sortOrder) {
        retrieveOfficesWithSorting(orderBy, sortOrder, 400);
    }

    private void retrieveOfficesWithSorting(final String orderBy, final String sortOrder, final int expectedStatusCode) {
        RequestSpecification request = given().spec(requestSpec).queryParam("orderBy", orderBy);
        if (sortOrder != null) {
            request = request.queryParam("sortOrder", sortOrder);
        }
        request.expect().statusCode(expectedStatusCode).when().get("/fineract-provider/api/v1/offices?" + Utils.TENANT_IDENTIFIER);
    }
}
