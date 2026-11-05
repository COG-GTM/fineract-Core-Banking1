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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.http.BodyCachingHttpServletRequestWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.service.ProgressiveLoanModelProcessingService;
import org.apache.fineract.portfolio.loanproduct.calc.data.ProgressiveLoanInterestScheduleModel;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class ProgressiveLoanModelCheckerFilter extends OncePerRequestFilter {

    private final LoanRepository loanRepository;
    private final ProgressiveLoanModelProcessingService progressiveLoanModelProcessingService;
    private final ProgressiveLoanModelCheckerHelper helper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // unauthenticated requests are rejected later by the authorization filter, so skip the body parsing and DB work
        if (!isAuthenticatedUser()) {
            proceed(filterChain, request, response);
            return;
        }
        request = new BodyCachingHttpServletRequestWrapper(request);

        if (!helper.isOnApiList((BodyCachingHttpServletRequestWrapper) request)) {
            proceed(filterChain, request, response);
        } else {
            List<Long> loanIds = helper.calculateRelevantLoanIds((BodyCachingHttpServletRequestWrapper) request);
            if (!loanIds.isEmpty()) {
                loanIds.forEach(loanId -> {
                    if (isProgressiveLoan(loanId) && allowedLoanStatuses(loanId) && !hasValidModel(loanId)) {
                        progressiveLoanModelProcessingService.recalculateModelAndSave(loanId);
                    }
                });
            }
            proceed(filterChain, request, response);
        }
    }

    private boolean isAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof AppUser;
    }

    private boolean isProgressiveLoan(Long loanId) {
        return loanRepository.isProgressiveLoan(loanId);
    }

    private boolean allowedLoanStatuses(Long loanId) {
        return progressiveLoanModelProcessingService.allowedLoanStatuses(loanId);
    }

    private boolean hasValidModel(Long loanId) {
        return progressiveLoanModelProcessingService.hasValidModel(loanId, ProgressiveLoanInterestScheduleModel.getModelVersion());
    }

    private void proceed(FilterChain filterChain, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        filterChain.doFilter(request, response);
    }

}
