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
package org.apache.fineract.mix.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MixTaxonomyMappingFormulaEvaluatorTest {

    private static final Map<String, BigDecimal> BALANCES = Map.of("1001", new BigDecimal("100.50"), "1002", new BigDecimal("20"),
            "GL-3.A_1", new BigDecimal("-7"));

    @Test
    void evaluatesPlaceholdersWithOperatorPrecedence() {
        assertThat(MixTaxonomyMappingFormulaEvaluator.evaluate("{1001}+{1002}*2", BALANCES)).isEqualByComparingTo("140.50");
    }

    @Test
    void evaluatesParenthesesUnaryMinusAndDivision() {
        assertThat(MixTaxonomyMappingFormulaEvaluator.evaluate(" -( {1001} - {1002} ) / 2 ", BALANCES)).isEqualByComparingTo("-40.25");
        assertThat(MixTaxonomyMappingFormulaEvaluator.evaluate("{GL-3.A_1} * -1.5", BALANCES)).isEqualByComparingTo("10.5");
    }

    @Test
    void treatsMissingBalanceAsZero() {
        assertThat(MixTaxonomyMappingFormulaEvaluator.evaluate("{9999}+{1002}", BALANCES)).isEqualByComparingTo("20");
    }

    @Test
    void rejectsDivisionByZero() {
        assertThatThrownBy(() -> MixTaxonomyMappingFormulaEvaluator.evaluate("{1001}/{9999}", BALANCES))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Division by zero");
    }

    @Test
    void acceptsDivisionByPlaceholderAtValidationTime() {
        MixTaxonomyMappingFormulaEvaluator.validate("{1001}/{1002}");
        MixTaxonomyMappingFormulaEvaluator.validate("{1001}/({1002}-{1003})");
        assertThat(MixTaxonomyMappingFormulaEvaluator.evaluate("{1001}/{1002}", BALANCES)).isEqualByComparingTo("5.025");
    }

    @Test
    void evaluatesGlCodesContainingPunctuation() {
        final Map<String, BigDecimal> balances = Map.of("ASSET:100", new BigDecimal("3"), "10'+'01", new BigDecimal("4"));
        assertThat(MixTaxonomyMappingFormulaEvaluator.evaluate("{ASSET:100}+{10'+'01}", balances)).isEqualByComparingTo("7");
    }

    @ParameterizedTest
    @ValueSource(strings = { "{1001}+{1002}", "12000", "-{1}", "((1+2)*3)/4", "{12000}+{11000}", "1.5 * {A-1}" })
    void acceptsArithmeticFormulas(final String formula) {
        MixTaxonomyMappingFormulaEvaluator.validate(formula);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Java.type('java.lang.Runtime').getRuntime().exec('id')", "this.engine", "1;2", "{1001}+", "(1+2", "1+2)",
            "{a{b}", "{}", "{1001", "1..2", "1e5", "Math.max(1,2)", "1 % 2", "1 ** 2", "", "   " })
    void rejectsNonArithmeticFormulas(final String formula) {
        assertThatThrownBy(() -> MixTaxonomyMappingFormulaEvaluator.validate(formula)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullOversizedAndDeeplyNestedFormulas() {
        assertThatThrownBy(() -> MixTaxonomyMappingFormulaEvaluator.validate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MixTaxonomyMappingFormulaEvaluator.validate("1+".repeat(1000) + "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MixTaxonomyMappingFormulaEvaluator.validate("(".repeat(500) + "1" + ")".repeat(500)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
