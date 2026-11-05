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
package org.apache.fineract.organisation.staff.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.organisation.staff.exception.StaffNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaffImageIdAdapterTest {

    private static final String USER_HIERARCHY = ".2.";

    @Mock
    private StaffRepository repository;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private Staff staff;

    private StaffImageIdAdapter underTest;

    @BeforeEach
    void setUp() {
        underTest = new StaffImageIdAdapter(repository, context);
        when(context.officeHierarchy()).thenReturn(USER_HIERARCHY);
    }

    @Test
    void validateAccessShouldPassForStaffInUserHierarchy() {
        when(repository.findByIdAndOfficeHierarchy(7L, USER_HIERARCHY + "%")).thenReturn(staff);

        assertDoesNotThrow(() -> underTest.validateAccess(7L));
    }

    @Test
    void validateAccessShouldRejectStaffOutsideUserHierarchy() {
        when(repository.findByIdAndOfficeHierarchy(8L, USER_HIERARCHY + "%")).thenReturn(null);

        assertThrows(StaffNotFoundException.class, () -> underTest.validateAccess(8L));
    }
}
