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
package org.apache.fineract.infrastructure.reportmailingjob.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.lang3.StringUtils;

public final class ReportOutputFileUtil {

    private ReportOutputFileUtil() {}

    /**
     * Resolves the file a generated report is written to, guaranteeing it is a direct child of the root folder.
     *
     * @throws IllegalArgumentException
     *             if the report name contains path separators or would resolve outside the root folder
     */
    public static File resolveReportOutputFile(final String rootFolder, final String reportName, final String fileExtension) {
        if (StringUtils.isBlank(reportName) || StringUtils.containsAny(reportName, '/', '\\', '\0')) {
            throw new IllegalArgumentException("Invalid report name for report output file");
        }
        final Path root = Paths.get(rootFolder).toAbsolutePath().normalize();
        final Path target = root.resolve(reportName + "." + fileExtension).normalize();
        if (!root.equals(target.getParent())) {
            throw new IllegalArgumentException("Report output file must be located inside the content root folder");
        }
        return target.toFile();
    }
}
