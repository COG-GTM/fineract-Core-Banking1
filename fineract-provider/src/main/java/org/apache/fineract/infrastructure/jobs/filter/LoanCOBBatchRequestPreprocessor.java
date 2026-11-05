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

import io.github.resilience4j.core.functions.Either;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.cob.conditions.LoanCOBEnabledCondition;
import org.apache.fineract.infrastructure.core.filters.BatchRequestPreprocessor;
import org.apache.fineract.infrastructure.jobs.exception.LoanIdsHardLockedException;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Re-checks the Loan COB hard lock for every batch sub-request after its references (e.g. {@code loans/$.id}) have
 * been resolved, since {@link LoanCOBApiFilter} cannot know those loan ids before the batch executes.
 */
@Component
@RequiredArgsConstructor
@Conditional(LoanCOBEnabledCondition.class)
public class LoanCOBBatchRequestPreprocessor implements BatchRequestPreprocessor {

    private final LoanCOBFilterHelper helper;

    @Override
    public Either<RuntimeException, BatchRequest> preprocess(BatchRequest batchRequest) {
        try {
            helper.checkResolvedBatchRequestNotHardLocked(batchRequest);
            return Either.right(batchRequest);
        } catch (LoanIdsHardLockedException e) {
            return Either.left(e);
        }
    }
}
