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
package org.apache.fineract.infrastructure.jobs.filter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.cob.loan.RetrieveLoanIdService;
import org.apache.fineract.cob.service.InlineLoanCOBExecutorServiceImpl;
import org.apache.fineract.cob.service.LoanAccountLockService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.http.BodyCachingHttpServletRequestWrapper;
import org.apache.fineract.infrastructure.jobs.exception.LoanIdsHardLockedException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LoanCOBFilterHelperTest {

    @Mock
    private GLIMAccountInfoRepository glimAccountInfoRepository;
    @Mock
    private LoanAccountLockService loanAccountLockService;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private InlineLoanCOBExecutorServiceImpl inlineLoanCOBExecutorService;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private FineractProperties fineractProperties;
    @Mock
    private RetrieveLoanIdService retrieveLoanIdService;

    @Mock
    private LoanRescheduleRequestRepository loanRescheduleRequestRepository;

    @InjectMocks
    private LoanCOBFilterHelper helper;

    @BeforeEach
    public void initLoanCOBFilterHelper() throws Exception {
        helper.afterPropertiesSet();
    }

    @Test
    public void testCOBFilterUnescapedChars() throws IOException {
        String json = """
                [
                    {
                        "requestId": 1,
                        "relativeUrl": "clients",
                        "method": "POST",
                        "headers": [
                            {
                                "name": "Idempotency-Key",
                                "value": "{{temp_idempotencyKey1}}"
                            },
                            {
                                "name": "Content-Type",
                                "value": "application/json"
                            },
                            {
                                "name": "Fineract-Platform-TenantId",
                                "value": "{{tenantId}}"
                            },
                            {
                                "name": "Authorization",
                                "value": "Basic bWlmb3M6cGFzc3dvcmQ="
                            }
                        ],
                        "body":"{
                    \\"officeId\\": 1,
                    \\"legalFormId\\": 2,
                    \\"isStaff\\": false,
                    \\"active\\": true,
                    \\"fullname\\": \\"Current Company 1\\",
                    \\"clientNonPersonDetails\\": {
                        \\"constitutionId\\": 1,
                        \\"incorpValidityTillDate\\": \\"\\",
                        \\"incorpNumber\\": \\"\\",
                        \\"mainBusinessLineId\\": \\"\\",
                        \\"remarks\\": \\"\\"
                    },
                    \\"activationDate\\": \\"01 January 2022\\",
                    \\"familyMembers\\": [],
                    \\"dateFormat\\": \\"dd MMMM yyyy\\",
                    \\"locale\\": \\"en\\"
                    }"}
                ]
                """;

        BodyCachingHttpServletRequestWrapper httpServletRequest = Mockito.mock(BodyCachingHttpServletRequestWrapper.class);
        Mockito.when(httpServletRequest.getPathInfo()).thenReturn("/v1/batches/endpoint");
        BodyCachingHttpServletRequestWrapper.CachedBodyServletInputStream inputStream = new BodyCachingHttpServletRequestWrapper.CachedBodyServletInputStream(
                new ByteArrayInputStream(json.getBytes(Charset.forName("UTF-8"))));
        Mockito.when(httpServletRequest.getInputStream()).thenReturn(inputStream);
        List<Long> loanIds = helper.calculateRelevantLoanIds(httpServletRequest);
        Assertions.assertEquals(0, loanIds.size());
    }

    @Test
    public void testBatchIdReferenceIsLeftToPostResolutionCheck() throws IOException {
        String json = """
                [
                    {"requestId": 1, "relativeUrl": "clients/5/accounts", "method": "GET"},
                    {"requestId": 2, "relativeUrl": "loans/$.loanAccounts[0].id/transactions?command=repayment",
                     "method": "POST", "reference": 1, "body": "{\\"transactionAmount\\": 10}"},
                    {"requestId": 3, "relativeUrl": "rescheduleloans/$.id?command=approve", "method": "POST", "reference": 1,
                     "body": "{}"}
                ]
                """;
        List<Long> loanIds = helper.calculateRelevantLoanIds(batchRequest(json));
        Assertions.assertEquals(List.of(), loanIds);
        Mockito.verifyNoInteractions(loanRescheduleRequestRepository, loanAccountLockService);
    }

    @Test
    public void testBatchReferenceOutsideLoanSegmentKeepsKnownLoanId() throws IOException {
        String json = """
                [
                    {"requestId": 1, "relativeUrl": "clients/5", "method": "GET"},
                    {"requestId": 2, "relativeUrl": "loans/123/transactions/$.id?command=adjust", "method": "POST",
                     "reference": 1, "body": "{}"}
                ]
                """;
        List<Long> loanIds = helper.calculateRelevantLoanIds(batchRequest(json));
        Assertions.assertEquals(List.of(123L), loanIds);
    }

    @Test
    public void testResolvedBatchRequestOnHardLockedLoanIsRejected() {
        mockBypassUser(false);
        Mockito.when(loanAccountLockService.isLoanHardLocked(123L)).thenReturn(true);
        Mockito.when(loanAccountLockService.isLockOverrulable(123L)).thenReturn(false);

        LoanIdsHardLockedException e = Assertions.assertThrows(LoanIdsHardLockedException.class,
                () -> helper.checkResolvedBatchRequestNotHardLocked(resolved("POST", "loans/123/transactions?command=repayment", "{}")));
        Assertions.assertEquals(123L, e.getLoanIdFromRequest());
    }

    @Test
    public void testResolvedVersionedBatchRequestOnHardLockedLoanIsRejected() {
        mockBypassUser(false);
        Mockito.when(loanAccountLockService.isLoanHardLocked(123L)).thenReturn(true);
        Mockito.when(loanAccountLockService.isLockOverrulable(123L)).thenReturn(false);

        Assertions.assertThrows(LoanIdsHardLockedException.class,
                () -> helper.checkResolvedBatchRequestNotHardLocked(resolved("PUT", "v1/loans/123", "{}")));
    }

    @Test
    public void testResolvedBatchRequestWithHardLockedBodyLoanIdIsRejected() {
        mockBypassUser(false);
        Mockito.when(loanAccountLockService.isLoanHardLocked(123L)).thenReturn(true);
        Mockito.when(loanAccountLockService.isLockOverrulable(123L)).thenReturn(false);

        Assertions.assertThrows(LoanIdsHardLockedException.class,
                () -> helper.checkResolvedBatchRequestNotHardLocked(resolved("POST", "rescheduleloans", "{\"loanId\": 123}")));
    }

    @Test
    public void testResolvedBatchRequestOnUnlockedOrOverrulableLoanIsAllowed() {
        mockBypassUser(false);
        Mockito.when(loanAccountLockService.isLoanHardLocked(123L)).thenReturn(false);
        Mockito.when(loanAccountLockService.isLoanHardLocked(124L)).thenReturn(true);
        Mockito.when(loanAccountLockService.isLockOverrulable(124L)).thenReturn(true);

        Assertions.assertDoesNotThrow(
                () -> helper.checkResolvedBatchRequestNotHardLocked(resolved("POST", "loans/123/transactions?command=repayment", "{}")));
        Assertions.assertDoesNotThrow(
                () -> helper.checkResolvedBatchRequestNotHardLocked(resolved("POST", "loans/124/transactions?command=repayment", "{}")));
    }

    @Test
    public void testResolvedBatchRequestSkipsReadsNonLoanWritesAndBypassUser() {
        Assertions.assertDoesNotThrow(() -> helper.checkResolvedBatchRequestNotHardLocked(resolved("GET", "loans/123", null)));
        Assertions.assertDoesNotThrow(() -> helper.checkResolvedBatchRequestNotHardLocked(resolved("POST", "clients", "{}")));
        Assertions.assertDoesNotThrow(() -> helper.checkResolvedBatchRequestNotHardLocked(resolved("POST", "loans/catch-up", null)));
        Mockito.verifyNoInteractions(loanAccountLockService, context);

        mockBypassUser(true);
        Assertions.assertDoesNotThrow(
                () -> helper.checkResolvedBatchRequestNotHardLocked(resolved("POST", "loans/123/transactions?command=repayment", "{}")));
        Mockito.verifyNoInteractions(loanAccountLockService);
    }

    private void mockBypassUser(boolean bypass) {
        AppUser user = Mockito.mock(AppUser.class);
        Mockito.when(user.isBypassUser()).thenReturn(bypass);
        Mockito.when(context.authenticatedUser()).thenReturn(user);
    }

    private static BatchRequest resolved(String method, String relativeUrl, String body) {
        BatchRequest batchRequest = new BatchRequest();
        batchRequest.setRequestId(1L);
        batchRequest.setMethod(method);
        batchRequest.setRelativeUrl(relativeUrl);
        batchRequest.setBody(body);
        return batchRequest;
    }

    private static BodyCachingHttpServletRequestWrapper batchRequest(String json) throws IOException {
        BodyCachingHttpServletRequestWrapper httpServletRequest = Mockito.mock(BodyCachingHttpServletRequestWrapper.class);
        Mockito.when(httpServletRequest.getPathInfo()).thenReturn("/v1/batches");
        Mockito.when(httpServletRequest.getInputStream()).thenReturn(new BodyCachingHttpServletRequestWrapper.CachedBodyServletInputStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        return httpServletRequest;
    }

}
