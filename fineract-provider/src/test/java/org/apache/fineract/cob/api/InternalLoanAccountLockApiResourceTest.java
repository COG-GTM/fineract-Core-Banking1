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
package org.apache.fineract.cob.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import org.apache.fineract.cob.domain.LoanAccountLock;
import org.apache.fineract.cob.domain.LoanAccountLockRepository;
import org.apache.fineract.cob.domain.LockOwner;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalLoanAccountLockApiResourceTest {

    private static final LocalDate COB_DATE = LocalDate.of(2024, 1, 15);

    @Mock
    private LoanAccountLockRepository loanAccountLockRepository;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser appUser;

    @InjectMocks
    private InternalLoanAccountLockApiResource underTest;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", ZoneId.systemDefault().getId(), null));
        HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, COB_DATE.plusDays(1));
        businessDates.put(BusinessDateType.COB_DATE, COB_DATE);
        ThreadLocalContextUtil.setBusinessDates(businessDates);
        when(context.authenticatedUser()).thenReturn(appUser);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void rejectsUserWithoutAllFunctionsPermission() {
        doThrow(new NoAuthorizationException("User has no authority to: ALL_FUNCTIONS")).when(appUser)
                .validateHasPermissionTo("ALL_FUNCTIONS");

        assertThrows(NoAuthorizationException.class,
                () -> underTest.placeLockOnLoanAccount(null, 1L, LockOwner.LOAN_COB_CHUNK_PROCESSING.name(), new LockRequest()));

        verify(loanAccountLockRepository, never()).save(any());
    }

    @Test
    void placesLockWithoutRequestBody() {
        Response response = underTest.placeLockOnLoanAccount(null, 7L, LockOwner.LOAN_COB_CHUNK_PROCESSING.name(), null);

        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
        verify(appUser).validateHasPermissionTo("ALL_FUNCTIONS");
        ArgumentCaptor<LoanAccountLock> captor = ArgumentCaptor.forClass(LoanAccountLock.class);
        verify(loanAccountLockRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getLoanId());
        assertEquals(LockOwner.LOAN_COB_CHUNK_PROCESSING, captor.getValue().getLockOwner());
        assertEquals(COB_DATE, captor.getValue().getLockPlacedOnCobBusinessDate());
        assertNull(captor.getValue().getError());
    }

    @Test
    void placesLockWithError() {
        LockRequest request = new LockRequest();
        request.setError("boom");

        underTest.placeLockOnLoanAccount(null, 8L, LockOwner.LOAN_INLINE_COB_PROCESSING.name(), request);

        ArgumentCaptor<LoanAccountLock> captor = ArgumentCaptor.forClass(LoanAccountLock.class);
        verify(loanAccountLockRepository).save(captor.capture());
        assertEquals("boom", captor.getValue().getError());
    }
}
