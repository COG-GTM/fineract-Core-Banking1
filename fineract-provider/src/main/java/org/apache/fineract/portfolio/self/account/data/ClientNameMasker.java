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
package org.apache.fineract.portfolio.self.account.data;

public final class ClientNameMasker {

    private static final char MASK_CHAR = '*';

    private ClientNameMasker() {}

    /**
     * Keeps only the first character of each word, e.g. "John Doe" becomes "J*** D**".
     */
    public static String mask(final String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        final StringBuilder masked = new StringBuilder(name.length());
        boolean wordStart = true;
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (Character.isWhitespace(c)) {
                masked.append(c);
                wordStart = true;
            } else if (wordStart) {
                masked.append(c);
                wordStart = false;
            } else {
                masked.append(MASK_CHAR);
            }
        }
        return masked.toString();
    }
}
