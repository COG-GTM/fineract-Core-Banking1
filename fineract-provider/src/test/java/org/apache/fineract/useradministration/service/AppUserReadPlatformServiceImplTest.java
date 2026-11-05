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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.useradministration.data.AppUserData;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AppUserReadPlatformServiceImplTest {

    private static final Long USER_ID = 5L;

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

    private AppUser user;

    @BeforeEach
    void setUp() {
        AppUser currentUser = mock(AppUser.class);
        Office branchOffice = office(2L, "Springfield", ".2.");
        when(currentUser.getOffice()).thenReturn(branchOffice);
        when(context.authenticatedUser()).thenReturn(currentUser);

        user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getUsername()).thenReturn("target");
        when(user.getRoles()).thenReturn(Set.of());
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleReadPlatformService.retrieveAll()).thenReturn(new ArrayList<>());
    }

    @Test
    void retrieveUserThrowsNotFoundForUserInParentOffice() {
        when(user.getOffice()).thenReturn(office(1L, "Head Office", "."));

        assertThrows(UserNotFoundException.class, () -> underTest.retrieveUser(USER_ID));
        verify(roleReadPlatformService, never()).retrieveAll();
    }

    @Test
    void retrieveUserThrowsNotFoundForUserInSiblingOffice() {
        when(user.getOffice()).thenReturn(office(3L, "Shelbyville", ".3."));

        assertThrows(UserNotFoundException.class, () -> underTest.retrieveUser(USER_ID));
    }

    @Test
    void retrieveUserThrowsNotFoundForUserInOfficeWithSharedIdPrefix() {
        when(user.getOffice()).thenReturn(office(21L, "Capital City", ".21."));

        assertThrows(UserNotFoundException.class, () -> underTest.retrieveUser(USER_ID));
    }

    @Test
    void retrieveUserReturnsUserInOwnOffice() {
        when(user.getOffice()).thenReturn(office(2L, "Springfield", ".2."));

        AppUserData result = underTest.retrieveUser(USER_ID);

        assertTrue(result.hasIdentifyOf(USER_ID));
    }

    @Test
    void retrieveUserReturnsUserInChildOffice() {
        when(user.getOffice()).thenReturn(office(5L, "Springfield North", ".2.5."));

        AppUserData result = underTest.retrieveUser(USER_ID);

        assertTrue(result.hasIdentifyOf(USER_ID));
    }

    private static Office office(Long id, String name, String hierarchy) {
        Office office = mock(Office.class);
        when(office.getId()).thenReturn(id);
        when(office.getName()).thenReturn(name);
        when(office.getHierarchy()).thenReturn(hierarchy);
        return office;
    }
}
