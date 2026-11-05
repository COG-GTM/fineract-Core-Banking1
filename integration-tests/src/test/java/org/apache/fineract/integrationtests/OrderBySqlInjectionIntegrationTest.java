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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.Map;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class OrderBySqlInjectionIntegrationTest {

    private static final String CLIENTS_URL = "/fineract-provider/api/v1/clients?" + Utils.TENANT_IDENTIFIER + "&limit=5";

    private RequestSpecification requestSpec;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    @Test
    public void plainOrderByAndSortOrderStillWork() {
        ResponseSpecification ok = new ResponseSpecBuilder().expectStatusCode(200).build();
        Utils.performServerGet(requestSpec, ok, CLIENTS_URL + "&orderBy=id&sortOrder=desc");
        Utils.performServerGet(requestSpec, ok, CLIENTS_URL + "&orderBy=displayName&sortOrder=ASC");
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep((5)))", "(select pg_sleep((5)))", "c.id desc, (select 1)", "case when 1 then 1 else 2 end" })
    public void expressionInOrderByIsRejectedWithoutExecuting(String payload) {
        assertRejectedQuickly(Map.of("orderBy", payload));
    }

    @ParameterizedTest
    @ValueSource(strings = { "desc, (select sleep((5)))", "asc, (select pg_sleep((5)))" })
    public void expressionInSortOrderIsRejectedWithoutExecuting(String payload) {
        assertRejectedQuickly(Map.of("orderBy", "id", "sortOrder", payload));
    }

    private void assertRejectedQuickly(Map<String, String> queryParams) {
        long start = System.nanoTime();
        given().spec(requestSpec).queryParams(queryParams).when().get(CLIENTS_URL).then().statusCode(400);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis < 4000, "request took " + elapsedMillis + "ms");
    }
}
