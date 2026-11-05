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
package org.apache.fineract.infrastructure.contentstore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.TestConfiguration;
import org.apache.fineract.infrastructure.contentstore.exception.ContentPolicyException;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@Slf4j
@SpringBootTest
@ContextConfiguration(classes = TestConfiguration.class)
class FileContentStoreServiceTest {

    @Autowired
    private FileContentStoreService fileContentStoreService;

    @Autowired
    private FineractProperties properties;

    @Test
    void upload() {
        setTenant();

        var path = fileContentStoreService.upload("some/path/test.jpg",
                FileContentStoreServiceTest.class.getClassLoader().getResourceAsStream("test.jpg"), "image/jpeg");

        var rootFolder = getRootFolder();

        log.info("Path: {} ({})", path, rootFolder);

        var p = rootFolder.resolve(path);

        log.info("Complete path: {}", p);

        assertTrue(Files.exists(p));
    }

    @Test
    void uploadRejectsTraversalHiddenBehindLineEndCharacters() throws IOException {
        setTenant();

        var rootFolder = getRootFolder();
        var escapedFolder = rootFolder.resolveSibling("OtherTenant").resolve("documents").resolve("clients").resolve("1").normalize();

        for (var separator : List.of("\r", "\n", "\u0085", "\u2028", "\u2029")) {
            var path = "documents/clients/1/x" + separator + "/../../../../../OtherTenant/documents/clients/1/contract.jpg";

            assertThrows(ContentPolicyException.class, () -> fileContentStoreService.upload(path,
                    FileContentStoreServiceTest.class.getClassLoader().getResourceAsStream("test.jpg"), "image/jpeg"));
        }

        assertFalse(Files.exists(escapedFolder));
    }

    @Test
    void uploadRejectsPathsResolvingOutsideTenantRoot() {
        setTenant();

        var paths = List.of("documents/../../OtherTenant/contract.jpg", "../OtherTenant/contract.jpg", "documents/..",
                "documents\\..\\..\\OtherTenant\\contract.jpg");

        paths.forEach(path -> assertThrows(RuntimeException.class, () -> fileContentStoreService.upload(path,
                FileContentStoreServiceTest.class.getClassLoader().getResourceAsStream("test.jpg"), "image/jpeg")));

        assertFalse(Files.exists(getRootFolder().resolveSibling("OtherTenant")));
    }

    @Test
    void uploadDeletesFileRejectedByPostUploadPolicy() throws IOException {
        setTenant();

        var folder = getRootFolder().resolve("documents").resolve("clients").resolve("99");

        assertThrows(ContentPolicyException.class, () -> fileContentStoreService.upload("documents/clients/99/fake.jpg",
                FileContentStoreServiceTest.class.getClassLoader().getResourceAsStream("test.png"), "image/jpeg"));

        if (Files.exists(folder)) {
            try (var files = Files.walk(folder)) {
                assertTrue(files.noneMatch(Files::isRegularFile));
            }
        }
    }

    @Test
    void relativePath() {
        var rootPath = Path.of("/tmp/fineract/DefaultDemoTenant");
        var fullPath = Path.of("/tmp/fineract/DefaultDemoTenant/images/staff/1/PwVhJDLHyxUppiby/michael.vorburger-crepes.jpg");
        var relativePath = rootPath.relativize(fullPath);

        assertEquals(Path.of("images/staff/1/PwVhJDLHyxUppiby/michael.vorburger-crepes.jpg"), relativePath);

        log.info("Relative path: {}", relativePath);
    }

    private Path getRootFolder() {
        return Path.of(properties.getContent().getFilesystem().getRootFolder(),
                ThreadLocalContextUtil.getTenant().getName().replaceAll(" ", "").trim());
    }

    private void setTenant() {
        ThreadLocalContextUtil
                .setTenant(FineractPlatformTenant.builder().id(1L).name("Test Tenant").tenantIdentifier("test-tenant").build());
    }
}
