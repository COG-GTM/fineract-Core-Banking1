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
package org.apache.fineract.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.mustachejava.MustacheNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.template.domain.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateMergeServiceTest {

    private final TemplateMergeService service = new TemplateMergeService(null);

    @TempDir
    Path tempDir;

    @Test
    void compilesVariables() {
        Map<String, Object> scopes = new HashMap<>();
        scopes.put("name", "Alice");

        assertThat(compile("Hello {{name}}", scopes)).isEqualTo("Hello Alice");
    }

    @Test
    void rejectsAbsoluteFilePartial() throws IOException {
        Path secret = Files.writeString(tempDir.resolve("secret"), "TOP-SECRET", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> compile("{{> " + secret.toAbsolutePath() + "}}", new HashMap<>()))
                .isInstanceOf(MustacheNotFoundException.class);
    }

    @Test
    void rejectsClasspathPartial() {
        assertThatThrownBy(() -> compile("{{> application.properties}}", new HashMap<>())).isInstanceOf(MustacheNotFoundException.class);
    }

    @Test
    void rejectsTemplateInheritance() {
        assertThatThrownBy(() -> compile("{{< /etc/hostname}}{{/ /etc/hostname}}", new HashMap<>()))
                .isInstanceOf(MustacheNotFoundException.class);
    }

    private String compile(String text, Map<String, Object> scopes) {
        return service.compile(new Template("TemplateName", text, null, null, new ArrayList<>()), scopes);
    }
}
