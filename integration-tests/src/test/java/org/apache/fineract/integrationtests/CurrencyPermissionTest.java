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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.fineract.client.models.CurrencyData;
import org.apache.fineract.client.models.CurrencyUpdateRequest;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.client.util.FineractClient;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

public class CurrencyPermissionTest extends IntegrationTest {

    @Test
    public void updateCurrenciesRequiresUpdateCurrencyPermission() {
        final List<String> selectedCurrencies = Calls.ok(fineractClient().currencies.retrieveCurrencies()).getSelectedCurrencyOptions()
                .stream().map(CurrencyData::getCode).toList();
        final CurrencyUpdateRequest request = new CurrencyUpdateRequest().currencies(selectedCurrencies);

        final String roleName = Utils.uniqueRandomStringGenerator("CURRENCY_ROLE_", 10);
        final Long roleId = Calls
                .ok(fineractClient().roles.createRole(new PostRolesRequest().name(roleName).description("Currency permission test role")))
                .getResourceId();
        Calls.ok(fineractClient().roles.updateRolePermissions(roleId, new PutRolesRoleIdPermissionsRequest()
                .putPermissionsItem("READ_CURRENCY", true).putPermissionsItem("UPDATE_CURRENCY", false)));

        final String userName = Utils.uniqueRandomStringGenerator("currencyUser", 4);
        final String password = "AKleRbDhK421$";
        Calls.ok(fineractClient().users.create16(new PostUsersRequest().addRolesItem(roleId).email(userName + "@whatever.mifos.org")
                .firstname(Utils.randomFirstNameGenerator()).lastname(Utils.randomLastNameGenerator()).repeatPassword(password)
                .sendPasswordToEmail(false).officeId(1L).username(userName).password(password)));
        final FineractClient readOnlyClient = newFineractClient(userName, password);

        assertEquals(200, Calls.executeU(readOnlyClient.currencies.retrieveCurrencies()).code());
        final Response<?> forbidden = Calls.executeU(readOnlyClient.currencies.updateCurrencies(request));
        assertEquals(403, forbidden.code());

        Calls.ok(fineractClient().roles.updateRolePermissions(roleId,
                new PutRolesRoleIdPermissionsRequest().putPermissionsItem("UPDATE_CURRENCY", true)));

        final Response<?> allowed = Calls.executeU(readOnlyClient.currencies.updateCurrencies(request));
        assertEquals(200, allowed.code());
    }
}
