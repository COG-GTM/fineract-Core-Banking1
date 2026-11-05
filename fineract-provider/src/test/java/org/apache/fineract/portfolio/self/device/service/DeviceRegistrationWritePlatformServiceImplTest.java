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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.portfolio.self.client.service.AppuserClientMapperReadService;
import org.apache.fineract.portfolio.self.device.domain.DeviceRegistration;
import org.apache.fineract.portfolio.self.device.domain.DeviceRegistrationRepositoryWrapper;
import org.apache.fineract.portfolio.self.device.exception.DeviceRegistrationNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceRegistrationWritePlatformServiceImplTest {

    private static final Long APP_USER_ID = 7L;
    private static final Long OWN_CLIENT_ID = 1L;
    private static final Long OTHER_CLIENT_ID = 2L;
    private static final Long OTHER_REGISTRATION_ID = 20L;
    private static final Long OWN_REGISTRATION_ID = 10L;

    @Mock
    private DeviceRegistrationRepositoryWrapper deviceRegistrationRepository;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppuserClientMapperReadService appuserClientMapperReadService;

    private DeviceRegistrationWritePlatformServiceImpl service;
    private Client ownClient;
    private DeviceRegistration ownRegistration;
    private DeviceRegistration otherRegistration;

    @BeforeEach
    void setUp() {
        service = new DeviceRegistrationWritePlatformServiceImpl(deviceRegistrationRepository, clientRepositoryWrapper, context,
                appuserClientMapperReadService);

        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(APP_USER_ID);
        when(context.authenticatedUser()).thenReturn(user);

        ownClient = mock(Client.class);
        when(ownClient.getId()).thenReturn(OWN_CLIENT_ID);
        Client otherClient = mock(Client.class);
        when(otherClient.getId()).thenReturn(OTHER_CLIENT_ID);

        ownRegistration = new DeviceRegistration().setClient(ownClient).setRegistrationId("own-token");
        otherRegistration = new DeviceRegistration().setClient(otherClient).setRegistrationId("victim-token");
        when(deviceRegistrationRepository.findOneWithNotFoundDetection(OWN_REGISTRATION_ID)).thenReturn(ownRegistration);
        when(deviceRegistrationRepository.findOneWithNotFoundDetection(OTHER_REGISTRATION_ID)).thenReturn(otherRegistration);
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(OWN_CLIENT_ID)).thenReturn(ownClient);

        when(appuserClientMapperReadService.isClientMappedToUser(OWN_CLIENT_ID, APP_USER_ID)).thenReturn(true);
        when(appuserClientMapperReadService.isClientMappedToUser(OTHER_CLIENT_ID, APP_USER_ID)).thenReturn(false);
        doThrow(new ClientNotFoundException(OTHER_CLIENT_ID)).when(appuserClientMapperReadService)
                .validateAppuserClientsMapping(OTHER_CLIENT_ID);
    }

    @Test
    void registerDeviceForOwnClientSaves() {
        DeviceRegistration registration = service.registerDevice(OWN_CLIENT_ID, "own-token");

        assertSame(ownClient, registration.getClient());
        verify(deviceRegistrationRepository).save(registration);
    }

    @Test
    void registerDeviceForUnmappedClientIsRejected() {
        assertThrows(ClientNotFoundException.class, () -> service.registerDevice(OTHER_CLIENT_ID, "attacker-token"));

        verify(clientRepositoryWrapper, never()).findOneWithNotFoundDetection(OTHER_CLIENT_ID);
        verify(deviceRegistrationRepository, never()).save(any());
    }

    @Test
    void updateOwnRegistrationSucceeds() {
        DeviceRegistration registration = service.updateDeviceRegistration(OWN_REGISTRATION_ID, OWN_CLIENT_ID, "new-token");

        assertEquals("new-token", registration.getRegistrationId());
    }

    @Test
    void updateOtherClientsRegistrationIsNotFound() {
        assertThrows(DeviceRegistrationNotFoundException.class,
                () -> service.updateDeviceRegistration(OTHER_REGISTRATION_ID, OWN_CLIENT_ID, "attacker-token"));

        assertEquals("victim-token", otherRegistration.getRegistrationId());
    }

    @Test
    void updateOwnRegistrationToUnmappedClientIsRejected() {
        assertThrows(ClientNotFoundException.class,
                () -> service.updateDeviceRegistration(OWN_REGISTRATION_ID, OTHER_CLIENT_ID, "own-token"));

        assertSame(ownClient, ownRegistration.getClient());
    }

    @Test
    void deleteOwnRegistrationSucceeds() {
        service.deleteDeviceRegistration(OWN_REGISTRATION_ID);

        verify(deviceRegistrationRepository).delete(ownRegistration);
    }

    @Test
    void deleteOtherClientsRegistrationIsNotFound() {
        assertThrows(DeviceRegistrationNotFoundException.class, () -> service.deleteDeviceRegistration(OTHER_REGISTRATION_ID));

        verify(deviceRegistrationRepository, never()).delete(any());
    }
}
