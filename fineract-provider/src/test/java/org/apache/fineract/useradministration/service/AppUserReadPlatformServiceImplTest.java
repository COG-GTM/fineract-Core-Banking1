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
package org.apache.fineract.useradministration.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
public class AppUserReadPlatformServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final String HEAD_OFFICE_HIERARCHY = ".";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private OfficeReadPlatformService officeReadPlatformService;
    @Mock
    private RoleReadPlatformService roleReadPlatformService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private StaffReadPlatformService staffReadPlatformService;

    @InjectMocks
    private AppUserReadPlatformServiceImpl underTest;

    @Test
    void retrieveUserOutsideCallerHierarchyIsRejected() {
        Office headOffice = mock(Office.class);
        when(headOffice.getHierarchy()).thenReturn(HEAD_OFFICE_HIERARCHY);
        AppUser headOfficeAdmin = mock(AppUser.class);
        when(headOfficeAdmin.getOffice()).thenReturn(headOffice);
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(headOfficeAdmin));
        doThrow(new NoAuthorizationException("The user doesn't have enough permissions to access the resource.")).when(context)
                .validateAccessRights(HEAD_OFFICE_HIERARCHY);

        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveUser(USER_ID));

        verifyNoInteractions(roleReadPlatformService, staffReadPlatformService);
    }
}
