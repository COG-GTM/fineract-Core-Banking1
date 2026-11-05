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
package org.apache.fineract.infrastructure.contentstore.util;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.contentstore.exception.ContentPolicyException;

public final class ContentFileNames {

    private ContentFileNames() {}

    /**
     * Reduces a client supplied file name to its last path segment so it can never add directories to a store path.
     */
    public static String sanitize(String fileName) {
        if (fileName == null || containsControlCharacter(fileName)) {
            throw new ContentPolicyException("File name contains illegal characters");
        }

        final var name = FilenameUtils.getName(fileName).trim();

        if (StringUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)) {
            throw new ContentPolicyException(String.format("File name not allowed: %s", fileName));
        }

        return name;
    }

    public static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029);
    }
}
