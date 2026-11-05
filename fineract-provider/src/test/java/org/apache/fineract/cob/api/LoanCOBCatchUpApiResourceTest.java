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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.cob.data.IsCatchUpRunningDTO;
import org.apache.fineract.cob.data.OldestCOBProcessedLoanDTO;
import org.apache.fineract.cob.service.LoanCOBCatchUpService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoanCOBCatchUpApiResourceTest {

    private LoanCOBCatchUpService loanCOBCatchUpService;
    private PlatformSecurityContext context;
    private LoanCOBCatchUpApiResource underTest;

    @BeforeEach
    void setUp() {
        loanCOBCatchUpService = mock(LoanCOBCatchUpService.class);
        context = mock(PlatformSecurityContext.class);
        underTest = new LoanCOBCatchUpApiResource(Optional.of(loanCOBCatchUpService), context);
    }

    @Test
    void catchUpIsRejectedForUserWithoutExecuteJobPermission() {
        authenticateAs("READ_LOAN", "READ_SCHEDULER");

        assertThrows(NoAuthorizationException.class, () -> underTest.executeLoanCOBCatchUp());

        verifyNoInteractions(loanCOBCatchUpService);
    }

    @Test
    void catchUpIsStartedForUserWithExecuteJobPermission() {
        authenticateAs("EXECUTEJOB_SCHEDULER");
        mockPendingCatchUp();

        Response response = underTest.executeLoanCOBCatchUp();

        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
        verify(loanCOBCatchUpService).unlockHardLockedLoans();
        verify(loanCOBCatchUpService).executeLoanCOBCatchUp();
    }

    @Test
    void catchUpIsStartedForUserWithAllFunctionsPermission() {
        authenticateAs("ALL_FUNCTIONS");
        mockPendingCatchUp();

        Response response = underTest.executeLoanCOBCatchUp();

        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());
        verify(loanCOBCatchUpService).executeLoanCOBCatchUp();
    }

    @Test
    void readEndpointsAreRejectedForUserWithoutSchedulerReadPermission() {
        authenticateAs("READ_LOAN");

        assertThrows(NoAuthorizationException.class, () -> underTest.getOldestCOBProcessedLoan());
        assertThrows(NoAuthorizationException.class, () -> underTest.isCatchUpRunning());

        verifyNoInteractions(loanCOBCatchUpService);
    }

    @Test
    void readEndpointsAreAllowedForUserWithSchedulerReadPermission() {
        authenticateAs("READ_SCHEDULER");
        OldestCOBProcessedLoanDTO oldest = new OldestCOBProcessedLoanDTO();
        when(loanCOBCatchUpService.getOldestCOBProcessedLoan()).thenReturn(oldest);
        IsCatchUpRunningDTO running = new IsCatchUpRunningDTO(false, null);
        when(loanCOBCatchUpService.isCatchUpRunning()).thenReturn(running);

        assertEquals(oldest, underTest.getOldestCOBProcessedLoan());
        assertEquals(running, underTest.isCatchUpRunning());
    }

    private void mockPendingCatchUp() {
        when(loanCOBCatchUpService.isCatchUpRunning()).thenReturn(new IsCatchUpRunningDTO(false, null));
        OldestCOBProcessedLoanDTO oldest = new OldestCOBProcessedLoanDTO();
        oldest.setLoanIds(List.of(1L));
        oldest.setCobProcessedDate(LocalDate.of(2024, 1, 1));
        oldest.setCobBusinessDate(LocalDate.of(2024, 1, 5));
        when(loanCOBCatchUpService.getOldestCOBProcessedLoan()).thenReturn(oldest);
    }

    private void authenticateAs(String... permissionCodes) {
        Role role = new Role("test-role", "test role");
        Arrays.stream(permissionCodes).forEach(code -> {
            String[] parts = code.split("_", 2);
            role.updatePermission(new Permission("test", parts[1], parts[0]), true);
        });
        AppUser user = mock(AppUser.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        ReflectionTestUtils.setField(user, "roles", new HashSet<>(List.of(role)));
        when(context.authenticatedUser()).thenReturn(user);
    }
}
