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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.batch.domain.BatchResponse;
import org.apache.fineract.infrastructure.core.filters.BatchFilterChain;
import org.apache.fineract.infrastructure.jobs.exception.LoanIdsHardLockedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanCOBBatchFilterTest {

    @Mock
    private LoanCOBFilterHelper helper;
    @Mock
    private BatchFilterChain chain;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private LoanCOBBatchFilter filter;

    private static BatchRequest request(String method, String relativeUrl) {
        BatchRequest request = new BatchRequest();
        request.setRequestId(2L);
        request.setMethod(method);
        request.setRelativeUrl(relativeUrl);
        return request;
    }

    @Test
    void shouldSkipRequestsNotOnApiList() {
        BatchRequest request = request("GET", "v1/loans/123");
        BatchResponse response = new BatchResponse();
        given(helper.isOnApiList(request)).willReturn(false);
        given(chain.serviceCall(request, uriInfo)).willReturn(response);

        assertSame(response, filter.doFilter(request, uriInfo, chain));

        verify(helper, never()).isBypassUser();
        verify(helper, never()).executeInlineCob(any());
    }

    @Test
    void shouldSkipChecksForBypassUser() throws Exception {
        BatchRequest request = request("POST", "v1/loans/123/transactions?command=repayment");
        given(helper.isOnApiList(request)).willReturn(true);
        given(helper.isBypassUser()).willReturn(true);

        filter.doFilter(request, uriInfo, chain);

        verify(helper, never()).calculateRelevantLoanIds(any(BatchRequest.class));
        verify(chain).serviceCall(request, uriInfo);
    }

    @Test
    void shouldRejectResolvedRequestOnHardLockedLoan() throws Exception {
        BatchRequest request = request("POST", "v1/loans/123/transactions?command=repayment");
        given(helper.isOnApiList(request)).willReturn(true);
        given(helper.isBypassUser()).willReturn(false);
        given(helper.calculateRelevantLoanIds(request)).willThrow(new LoanIdsHardLockedException(123L));

        LoanIdsHardLockedException ex = assertThrows(LoanIdsHardLockedException.class, () -> filter.doFilter(request, uriInfo, chain));

        assertEquals(123L, ex.getLoanIdFromRequest());
        verifyNoInteractions(chain);
    }

    @Test
    void shouldRunInlineCobWhenResolvedLoanIsBehind() throws Exception {
        BatchRequest request = request("POST", "v1/loans/123/transactions?command=repayment");
        given(helper.isOnApiList(request)).willReturn(true);
        given(helper.isBypassUser()).willReturn(false);
        given(helper.calculateRelevantLoanIds(request)).willReturn(List.of(123L));
        given(helper.isLoanBehind(List.of(123L))).willReturn(true);

        filter.doFilter(request, uriInfo, chain);

        verify(helper).executeInlineCob(List.of(123L));
        verify(chain).serviceCall(request, uriInfo);
    }

    @Test
    void shouldNotRunInlineCobWhenResolvedLoanIsUpToDate() throws Exception {
        BatchRequest request = request("POST", "v1/loans/123/transactions?command=repayment");
        given(helper.isOnApiList(request)).willReturn(true);
        given(helper.isBypassUser()).willReturn(false);
        given(helper.calculateRelevantLoanIds(request)).willReturn(List.of(123L));
        given(helper.isLoanBehind(List.of(123L))).willReturn(false);

        filter.doFilter(request, uriInfo, chain);

        verify(helper, never()).executeInlineCob(any());
        verify(chain).serviceCall(request, uriInfo);
    }
}
