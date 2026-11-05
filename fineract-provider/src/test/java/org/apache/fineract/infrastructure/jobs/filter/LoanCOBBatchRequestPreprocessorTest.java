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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;

import io.github.resilience4j.core.functions.Either;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.infrastructure.jobs.exception.LoanIdsHardLockedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanCOBBatchRequestPreprocessorTest {

    @Mock
    private LoanCOBFilterHelper helper;

    @InjectMocks
    private LoanCOBBatchRequestPreprocessor preprocessor;

    @Test
    void hardLockedLoanIsRejected() {
        BatchRequest request = new BatchRequest().setMethod("POST").setRelativeUrl("loans/123/transactions?command=repayment");
        doThrow(new LoanIdsHardLockedException(123L)).when(helper).checkResolvedBatchRequestNotHardLocked(request);

        Either<RuntimeException, BatchRequest> result = preprocessor.preprocess(request);

        assertTrue(result.isLeft());
        assertEquals(123L, ((LoanIdsHardLockedException) result.getLeft()).getLoanIdFromRequest());
    }

    @Test
    void unlockedLoanPassesThrough() {
        BatchRequest request = new BatchRequest().setMethod("POST").setRelativeUrl("loans/123/transactions?command=repayment");

        Either<RuntimeException, BatchRequest> result = preprocessor.preprocess(request);

        assertTrue(result.isRight());
        assertSame(request, result.get());
    }
}
