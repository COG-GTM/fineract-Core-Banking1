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
package org.apache.fineract.infrastructure.documentmanagement.domain;

import java.util.Arrays;
import java.util.Locale;
import org.apache.fineract.infrastructure.documentmanagement.exception.InvalidDocumentEntityTypeException;

/**
 * Entity types that documents can be attached to.
 */
public enum DocumentEntityType {

    CLIENTS, //
    CLIENT_IDENTIFIERS, //
    STAFF, //
    LOANS, //
    SAVINGS, //
    GROUPS, //
    IMPORT; //

    public static boolean isSupported(final String entityType) {
        return entityType != null && Arrays.stream(values()).anyMatch(type -> type.name().equalsIgnoreCase(entityType));
    }

    public static void validate(final String entityType) {
        if (!isSupported(entityType)) {
            throw new InvalidDocumentEntityTypeException(entityType);
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
