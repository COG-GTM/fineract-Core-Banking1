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

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.PersistenceException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.self.client.service.AppuserClientMapperReadService;
import org.apache.fineract.portfolio.self.device.domain.DeviceRegistration;
import org.apache.fineract.portfolio.self.device.domain.DeviceRegistrationRepositoryWrapper;
import org.apache.fineract.portfolio.self.device.exception.DeviceRegistrationNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceRegistrationWritePlatformServiceImpl implements DeviceRegistrationWritePlatformService {

    private final DeviceRegistrationRepositoryWrapper deviceRegistrationRepository;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final PlatformSecurityContext context;
    private final AppuserClientMapperReadService appuserClientMapperReadService;

    @Autowired
    public DeviceRegistrationWritePlatformServiceImpl(final DeviceRegistrationRepositoryWrapper deviceRegistrationRepository,
            final ClientRepositoryWrapper clientRepositoryWrapper, final PlatformSecurityContext context,
            final AppuserClientMapperReadService appuserClientMapperReadService) {
        this.deviceRegistrationRepository = deviceRegistrationRepository;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.context = context;
        this.appuserClientMapperReadService = appuserClientMapperReadService;
    }

    @Transactional
    @Override
    public DeviceRegistration registerDevice(Long clientId, String registrationId) {
        this.appuserClientMapperReadService.validateAppuserClientsMapping(clientId);
        Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);
        try {
            DeviceRegistration deviceRegistration = DeviceRegistration.instance(client, registrationId);
            this.deviceRegistrationRepository.save(deviceRegistration);
            return deviceRegistration;
        } catch (final EntityExistsException dve) {
            handleDataIntegrityIssues(registrationId, dve);
            return null;
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(registrationId, dve.getMostSpecificCause());
            return null;
        } catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(registrationId, throwable);
            return null;
        } catch (final Exception dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(registrationId, throwable);
            return null;
        }

    }

    private void handleDataIntegrityIssues(final String registrationId, final Throwable realCause) {
        if (realCause.getMessage().contains("registration_key")) {
            throw new PlatformDataIntegrityException("error.msg.duplicate.device.registration.id",
                    "Registration id : " + registrationId + " already exist.", "name", registrationId);
        }
        throw ErrorHandler.getMappable(realCause, "error.msg.charge.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource: " + realCause.getMessage());
    }

    @Transactional
    @Override
    public DeviceRegistration updateDeviceRegistration(Long id, Long clientId, String registrationId) {
        DeviceRegistration deviceRegistration = findOwnedDeviceRegistration(id);
        this.appuserClientMapperReadService.validateAppuserClientsMapping(clientId);
        Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);
        deviceRegistration.setClient(client);
        deviceRegistration.setRegistrationId(registrationId);
        deviceRegistration.setUpdatedOnDate(DateUtils.getLocalDateTimeOfTenant());
        return deviceRegistration;
    }

    @Transactional
    @Override
    public void deleteDeviceRegistration(Long id) {
        DeviceRegistration deviceRegistration = findOwnedDeviceRegistration(id);
        this.deviceRegistrationRepository.delete(deviceRegistration);
    }

    private DeviceRegistration findOwnedDeviceRegistration(final Long id) {
        final AppUser user = this.context.authenticatedUser();
        final DeviceRegistration deviceRegistration = this.deviceRegistrationRepository.findOneWithNotFoundDetection(id);
        final Long clientId = deviceRegistration.getClient() == null ? null : deviceRegistration.getClient().getId();
        if (clientId == null || !Boolean.TRUE.equals(this.appuserClientMapperReadService.isClientMappedToUser(clientId, user.getId()))) {
            throw new DeviceRegistrationNotFoundException(id);
        }
        return deviceRegistration;
    }

}
