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
package org.apache.fineract.portfolio.savings.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

public class DepositAccountDataValidatorInterestCalculationTest {

    private final DepositAccountDataValidator validator = new DepositAccountDataValidator(new FromJsonHelper(), null);

    private static String request(long tenureInMonths) {
        return "{\"principalAmount\":1000,\"annualInterestRate\":10,\"tenureInMonths\":" + tenureInMonths
                + ",\"interestCompoundingPeriodInMonths\":1,\"interestPostingPeriodInMonths\":1}";
    }

    @Test
    public void acceptsTenureAtMaximum() {
        assertDoesNotThrow(() -> validator.validateFixedDepositForInterestCalculation(request(1200L)));
    }

    @Test
    public void rejectsTenureAboveMaximum() {
        assertTenureRejected(1201L);
    }

    @Test
    public void rejectsHugeTenure() {
        assertTenureRejected(100_000_000L);
    }

    private void assertTenureRejected(long tenureInMonths) {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateFixedDepositForInterestCalculation(request(tenureInMonths)));
        List<ApiParameterError> errors = exception.getErrors();
        assertEquals(1, errors.size());
        assertEquals("tenureInMonths", errors.get(0).getParameterName());
        assertEquals("validation.msg.fixeddepositaccount.tenureInMonths.is.greater.than.max",
                errors.get(0).getUserMessageGlobalisationCode());
    }
}
