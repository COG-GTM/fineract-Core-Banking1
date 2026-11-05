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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SelfServiceRegistrationReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SelfServiceRegistrationReadPlatformServiceImpl service;

    @Test
    void emailModeMatchesStoredEmail() {
        when(jdbcTemplate.queryForObject(
                eq("select count(*) from m_client where account_no = ? and firstname = ? and lastname = ?"
                        + " and email_address is not null and lower(email_address) = lower(?)"),
                eq(Integer.class), eq("1"), eq("Alice"), eq("Smith"), eq("alice@example.com"))).thenReturn(1);

        assertTrue(service.isClientExist("1", "Alice", "Smith", null, "alice@example.com", true));
    }

    @Test
    void mobileModeMatchesStoredMobileNumber() {
        when(jdbcTemplate.queryForObject(
                eq("select count(*) from m_client where account_no = ? and firstname = ? and lastname = ?"
                        + " and mobile_no is not null and mobile_no = ?"),
                eq(Integer.class), eq("1"), eq("Alice"), eq("Smith"), eq("5551234"))).thenReturn(0);

        assertFalse(service.isClientExist("1", "Alice", "Smith", "5551234", "alice@example.com", false));
    }

    @Test
    void detectsExistingSelfServiceUserMapping() {
        when(jdbcTemplate.queryForObject("select count(*) from m_selfservice_user_client_mapping where client_id = ?", Integer.class, 7L))
                .thenReturn(1);

        assertTrue(service.isClientMappedToSelfServiceUser(7L));
    }
}
