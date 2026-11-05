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
package org.apache.fineract.portfolio.self.device.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.self.device.domain.DeviceRegistrationData;
import org.apache.fineract.portfolio.self.device.exception.DeviceRegistrationNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class DeviceRegistrationReadPlatformServiceImplTest {

    private static final Long APP_USER_ID = 7L;
    private static final String MAPPING_FILTER = "m_selfservice_user_client_mapping umap where umap.appuser_id = ?";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;

    private DeviceRegistrationReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceRegistrationReadPlatformServiceImpl(context, jdbcTemplate);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(APP_USER_ID);
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveAllIsScopedToCallersMappedClients() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(APP_USER_ID))).thenReturn(List.of());

        service.retrieveAllDeviceRegiistrations();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(APP_USER_ID));
        assertTrue(sql.getValue().contains(MAPPING_FILTER));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveByIdIsScopedToCallersMappedClients() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(20L), eq(APP_USER_ID)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThrows(DeviceRegistrationNotFoundException.class, () -> service.retrieveDeviceRegiistration(20L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(RowMapper.class), eq(20L), eq(APP_USER_ID));
        assertTrue(sql.getValue().contains("cdr.id = ? and " + " cdr.client_id in"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveByClientIdIsScopedToCallersMappedClients() {
        DeviceRegistrationData data = mock(DeviceRegistrationData.class);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(1L), eq(APP_USER_ID))).thenReturn(data);

        service.retrieveDeviceRegiistrationByClientId(1L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(RowMapper.class), eq(1L), eq(APP_USER_ID));
        assertTrue(sql.getValue().contains("c.id = ? and " + " cdr.client_id in"));
    }
}
