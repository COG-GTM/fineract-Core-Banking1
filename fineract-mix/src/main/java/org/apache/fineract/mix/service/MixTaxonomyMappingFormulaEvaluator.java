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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Arithmetic-only evaluator for MIX taxonomy mapping formulas.
 *
 * <p>
 * Grammar: numbers, {@code {glCode}} placeholders, {@code + - * /}, unary {@code + -}, parentheses and whitespace.
 * Anything else is rejected, so formulas can never be executed as code.
 */
public final class MixTaxonomyMappingFormulaEvaluator {

    public static final int MAX_FORMULA_LENGTH = 2000;
    private static final int MAX_NESTING_DEPTH = 64;
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;
    private static final Pattern GL_CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private MixTaxonomyMappingFormulaEvaluator() {}

    /**
     * Checks that the formula is syntactically valid.
     *
     * @throws IllegalArgumentException
     *             if the formula is not a valid arithmetic expression
     */
    public static void validate(final String formula) {
        new Parser(formula, Map.of()).parse();
    }

    /**
     * Evaluates the formula, replacing every {@code {glCode}} placeholder with its balance (missing balances count as
     * zero).
     *
     * @throws IllegalArgumentException
     *             if the formula is not a valid arithmetic expression or divides by zero
     */
    public static BigDecimal evaluate(final String formula, final Map<String, BigDecimal> balances) {
        return new Parser(formula, balances).parse();
    }

    private static final class Parser {

        private final String input;
        private final Map<String, BigDecimal> balances;
        private int pos;
        private int depth;

        Parser(final String input, final Map<String, BigDecimal> balances) {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("Mapping formula must not be empty");
            }
            if (input.length() > MAX_FORMULA_LENGTH) {
                throw new IllegalArgumentException("Mapping formula exceeds " + MAX_FORMULA_LENGTH + " characters");
            }
            this.input = input;
            this.balances = balances;
        }

        BigDecimal parse() {
            final BigDecimal result = parseExpression();
            skipWhitespace();
            if (pos < input.length()) {
                throw error("Unexpected character '" + input.charAt(pos) + "'");
            }
            return result;
        }

        private BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (true) {
                if (consume('+')) {
                    value = value.add(parseTerm(), MATH_CONTEXT);
                } else if (consume('-')) {
                    value = value.subtract(parseTerm(), MATH_CONTEXT);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (true) {
                if (consume('*')) {
                    value = value.multiply(parseFactor(), MATH_CONTEXT);
                } else if (consume('/')) {
                    final int divisorPos = pos;
                    final BigDecimal divisor = parseFactor();
                    if (divisor.signum() == 0) {
                        pos = divisorPos;
                        throw error("Division by zero");
                    }
                    value = value.divide(divisor, MATH_CONTEXT);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal parseFactor() {
            if (++depth > MAX_NESTING_DEPTH) {
                throw error("Mapping formula is nested too deeply");
            }
            try {
                if (consume('+')) {
                    return parseFactor();
                }
                if (consume('-')) {
                    return parseFactor().negate();
                }
                if (consume('(')) {
                    final BigDecimal value = parseExpression();
                    if (!consume(')')) {
                        throw error("Expected ')'");
                    }
                    return value;
                }
                if (consume('{')) {
                    return parsePlaceholder();
                }
                return parseNumber();
            } finally {
                depth--;
            }
        }

        private BigDecimal parsePlaceholder() {
            final int end = input.indexOf('}', pos);
            if (end < 0) {
                throw error("Expected '}'");
            }
            final String glCode = input.substring(pos, end);
            if (!GL_CODE_PATTERN.matcher(glCode).matches()) {
                throw error("Invalid GL code placeholder");
            }
            pos = end + 1;
            final BigDecimal balance = balances.get(glCode);
            return balance != null ? balance : BigDecimal.ZERO;
        }

        private BigDecimal parseNumber() {
            final int start = pos;
            while (pos < input.length() && isNumberChar(input.charAt(pos))) {
                pos++;
            }
            if (start == pos) {
                throw error(pos < input.length() ? "Unexpected character '" + input.charAt(pos) + "'" : "Unexpected end of formula");
            }
            try {
                return new BigDecimal(input.substring(start, pos));
            } catch (final NumberFormatException e) {
                pos = start;
                throw error("Invalid number");
            }
        }

        private static boolean isNumberChar(final char c) {
            return (c >= '0' && c <= '9') || c == '.';
        }

        private boolean consume(final char expected) {
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private IllegalArgumentException error(final String message) {
            return new IllegalArgumentException(message + " at position " + pos + " in mapping formula");
        }
    }
}
