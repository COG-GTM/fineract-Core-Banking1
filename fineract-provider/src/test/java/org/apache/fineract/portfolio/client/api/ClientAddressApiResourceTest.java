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
package org.apache.fineract.portfolio.client.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.address.data.AddressData;
import org.apache.fineract.portfolio.address.filter.ClientAddressSearchParam;
import org.apache.fineract.portfolio.address.service.AddressReadPlatformServiceImpl;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientAddressApiResourceTest {

    private static final String USER_HIERARCHY = ".1.";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AddressReadPlatformServiceImpl readPlatformService;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private AppUser appUser;

    @InjectMocks
    private ClientAddressApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(context.officeHierarchy()).thenReturn(USER_HIERARCHY);
    }

    @Test
    void getAddressesRejectsClientOutsideCallerHierarchy() {
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(7L, USER_HIERARCHY + "%"))
                .thenThrow(new ClientNotFoundException("7", "client.id"));

        assertThrows(ClientNotFoundException.class, () -> resource.getAddresses(null, 0L, 7L));

        verify(readPlatformService, never()).retrieveBySearchParam(any());
    }

    @Test
    void getAddressesReturnsAddressesForClientInCallerHierarchy() {
        final List<AddressData> addresses = List.of();
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(3L, USER_HIERARCHY + "%")).thenReturn(mock(Client.class));
        when(readPlatformService.retrieveBySearchParam(new ClientAddressSearchParam(3L, 0L, null))).thenReturn(addresses);

        assertSame(addresses, resource.getAddresses(null, 0L, 3L));

        verify(appUser).validateHasReadPermission("Address");
    }
}
