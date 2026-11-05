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
package org.apache.fineract.infrastructure.core.filters;

package org.apache.fineract.infrastructure.jobs.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.batch.domain.BatchResponse;
import org.apache.fineract.cob.conditions.LoanCOBEnabledCondition;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.filters.BatchFilter;
import org.apache.fineract.infrastructure.core.filters.BatchFilterChain;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Re-applies the {@link LoanCOBApiFilter} hard-lock and inline COB catch-up checks to each batch sub-request after its
 * {@code $.} references were resolved from the parent response, since the servlet filter cannot see those loan IDs.
 */
@Component
@RequiredArgsConstructor
@Conditional(LoanCOBEnabledCondition.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoanCOBBatchFilter implements BatchFilter {

    private final LoanCOBFilterHelper helper;

    @Override
    public BatchResponse doFilter(BatchRequest batchRequest, UriInfo uriInfo, BatchFilterChain chain) {
        if (helper.isOnApiList(batchRequest) && !helper.isBypassUser()) {
            List<Long> loanIds = calculateRelevantLoanIds(batchRequest);
            if (!loanIds.isEmpty() && helper.isLoanBehind(loanIds)) {
                helper.executeInlineCob(loanIds);
            }
        }
        return chain.serviceCall(batchRequest, uriInfo);
    }

    private List<Long> calculateRelevantLoanIds(BatchRequest batchRequest) {
        try {
            return helper.calculateRelevantLoanIds(batchRequest);
        } catch (JsonProcessingException e) {
            throw new PlatformApiDataValidationException("error.msg.batch.request.body.invalid", "Invalid batch request body",
                    List.of(), e);
        }
    }
}
