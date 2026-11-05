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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import org.apache.fineract.portfolio.loanaccount.service.ProgressiveLoanModelProcessingService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgressiveLoanModelCheckerFilterTest {

    @Mock
    private GLIMAccountInfoRepository glimAccountInfoRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LoanRescheduleRequestRepository loanRescheduleRequestRepository;
    @Mock
    private ProgressiveLoanModelProcessingService progressiveLoanModelProcessingService;
    @Mock
    private FilterChain filterChain;

    private ProgressiveLoanModelCheckerFilter testObj;

    @BeforeEach
    void setUp() throws Exception {
        ProgressiveLoanModelCheckerHelper helper = new ProgressiveLoanModelCheckerHelper(glimAccountInfoRepository, loanRepository,
                loanRescheduleRequestRepository);
        helper.afterPropertiesSet();
        testObj = new ProgressiveLoanModelCheckerFilter(loanRepository, progressiveLoanModelProcessingService, helper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipModelCheckWithoutReadingBodyWhenNotAuthenticated() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = new MockHttpServletResponse();
        given(request.getMethod()).willReturn("POST");
        given(request.getPathInfo()).willReturn("/v1/batches");

        testObj.doFilterInternal(request, response, filterChain);

        verify(request, never()).getInputStream();
        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(loanRepository, progressiveLoanModelProcessingService, glimAccountInfoRepository,
                loanRescheduleRequestRepository);
    }

    @Test
    void shouldSkipModelCheckForAnonymousAuthentication() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        MockHttpServletRequest request = batchRequest(duplicatedBatchBody(100, "loans/external-id/ext-1/transactions"));
        HttpServletResponse response = new MockHttpServletResponse();

        testObj.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verifyNoInteractions(loanRepository, progressiveLoanModelProcessingService);
    }

    @Test
    void shouldQueryEachLoanOnlyOnceForDuplicatedBatchSubRequests() throws ServletException, IOException {
        authenticate();
        given(loanRepository.findIdByExternalId(any(ExternalId.class))).willReturn(1L);
        given(loanRepository.isProgressiveLoan(anyLong())).willReturn(true);
        given(progressiveLoanModelProcessingService.allowedLoanStatuses(anyLong())).willReturn(true);
        given(progressiveLoanModelProcessingService.hasValidModel(anyLong(), anyString())).willReturn(false);
        MockHttpServletRequest request = batchRequest(duplicatedBatchBody(1000, "loans/external-id/ext-1/transactions"));

        testObj.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(loanRepository, times(1)).findIdByExternalId(any(ExternalId.class));
        verify(loanRepository, times(1)).isProgressiveLoan(1L);
        verify(progressiveLoanModelProcessingService, times(1)).recalculateModelAndSave(1L);
        verify(filterChain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void shouldRecalculateModelForAuthenticatedLoanWrite() throws ServletException, IOException {
        authenticate();
        given(loanRepository.isProgressiveLoan(12L)).willReturn(true);
        given(progressiveLoanModelProcessingService.allowedLoanStatuses(12L)).willReturn(true);
        given(progressiveLoanModelProcessingService.hasValidModel(eq(12L), anyString())).willReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/fineract-provider/api/v1/loans/12/transactions");
        request.setPathInfo("/v1/loans/12/transactions");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        testObj.doFilterInternal(request, new MockHttpServletResponse(), filterChain);

        verify(progressiveLoanModelProcessingService, times(1)).recalculateModelAndSave(12L);
        verify(filterChain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    private void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(mock(AppUser.class), null, List.of()));
    }

    private static MockHttpServletRequest batchRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/fineract-provider/api/v1/batches");
        request.setPathInfo("/v1/batches");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static String duplicatedBatchBody(int count, String relativeUrl) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (int i = 0; i < count; i++) {
            joiner.add("{\"requestId\":" + i + ",\"method\":\"POST\",\"relativeUrl\":\"" + relativeUrl
                    + "\",\"body\":\"{\\\"loanId\\\":1}\"}");
        }
        return joiner.toString();
    }
}
