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
package org.apache.fineract.portfolio.self.registration.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class SelfServiceRegistrationReadPlatformServiceImpl implements SelfServiceRegistrationReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean isClientExist(String accountNumber, String firstName, String lastName, String mobileNumber, String email,
            boolean isEmailAuthenticationMode) {
        String sql = "select count(*) from m_client where account_no = ? and firstname = ? and lastname = ?";
        Object[] params;
        if (isEmailAuthenticationMode) {
            sql = sql + " and email_address is not null and lower(email_address) = lower(?)";
            params = new Object[] { accountNumber, firstName, lastName, email };
        } else {
            sql = sql + " and mobile_no = ?";
            params = new Object[] { accountNumber, firstName, lastName, mobileNumber };
        }
        int count = this.jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count != 0;
    }

    @Override
    public boolean isSelfServiceUserExistForClient(Long clientId) {
        String sql = "select count(*) from m_selfservice_user_client_mapping where client_id = ?";
        int count = this.jdbcTemplate.queryForObject(sql, Integer.class, clientId);
        return count != 0;
    }

}
