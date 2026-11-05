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
package org.apache.fineract.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

class LocalstackS3ClientCustomizerTest {

    private static final String LOCALSTACK_ENDPOINT = "http://localstack:4666";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LocalstackS3ClientCustomizer.class);

    @Test
    void appliesPathStyleWithoutTestProfileWhenEndpointConfigured() {
        contextRunner.withPropertyValues("AWS_ENDPOINT_URL=" + LOCALSTACK_ENDPOINT).run(context -> {
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context).hasSingleBean(LocalstackS3ClientCustomizer.class);

            S3ClientBuilder builder = mock(S3ClientBuilder.class, RETURNS_SELF);
            context.getBean(LocalstackS3ClientCustomizer.class).customize(builder);

            verify(builder).endpointOverride(URI.create(LOCALSTACK_ENDPOINT));
            verify(builder).forcePathStyle(true);
        });
    }

    @Test
    void registeredButNoOpWhenEndpointNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LocalstackS3ClientCustomizer.class);

            S3ClientBuilder builder = mock(S3ClientBuilder.class, RETURNS_SELF);
            context.getBean(LocalstackS3ClientCustomizer.class).customize(builder);

            verifyNoInteractions(builder);
        });
    }
}
