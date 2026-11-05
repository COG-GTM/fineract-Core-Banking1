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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.contentstore.detector.ContentDetectorManager;
import org.apache.fineract.infrastructure.contentstore.exception.ContentStoreException;
import org.apache.fineract.infrastructure.contentstore.policy.DefaultDeleteContentPolicy;
import org.apache.fineract.infrastructure.contentstore.policy.DefaultDownloadContentPolicy;
import org.apache.fineract.infrastructure.contentstore.policy.DefaultPostUploadContentPolicy;
import org.apache.fineract.infrastructure.contentstore.policy.DefaultPreUploadContentPolicy;
import org.apache.fineract.infrastructure.contentstore.util.DefaultContentPathRandomizer;
import org.apache.fineract.infrastructure.contentstore.util.DefaultContentPathSanitizer;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3ContentStoreServiceTest {

    private static final String BUCKET = "fineract-documents";
    private static final String PATH = "documents/clients/17/contract.pdf";

    private InMemoryS3Client s3Client;
    private S3ContentStoreService service;

    @BeforeEach
    void setUp() {
        final var properties = mock(FineractProperties.class, RETURNS_DEEP_STUBS);
        when(properties.getContent().getS3().getBucketName()).thenReturn(BUCKET);

        s3Client = new InMemoryS3Client();
        service = new S3ContentStoreService(s3Client, new DefaultContentPathSanitizer(properties),
                new DefaultContentPathRandomizer(properties), mock(DefaultDownloadContentPolicy.class),
                mock(DefaultPreUploadContentPolicy.class), mock(DefaultPostUploadContentPolicy.class),
                mock(DefaultDeleteContentPolicy.class), mock(ContentDetectorManager.class), properties);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void sameUploadPathInTwoTenantsUsesDifferentTenantScopedKeys() {
        setTenant("acme");
        final var acmeLocation = upload(PATH, "acme contract");

        setTenant("globex");
        final var globexLocation = upload(PATH, "forged contract");

        assertEquals(2, s3Client.objects.size());
        assertTrue(s3Client.objects.containsKey("acme/" + acmeLocation));
        assertTrue(s3Client.objects.containsKey("globex/" + globexLocation));
        assertTrue(s3Client.objects.keySet().stream().noneMatch(PATH::equals));

        setTenant("acme");
        assertEquals("acme contract", download(acmeLocation));
    }

    @Test
    void uploadAddsRandomSegmentAndUsesConditionalWrite() {
        setTenant("acme");

        final var first = upload(PATH, "one");
        final var second = upload(PATH, "two");

        assertNotEquals(first, second);
        assertTrue(first.matches("documents/clients/17/[A-Za-z]{16}/contract\\.pdf"), first);
        assertTrue(s3Client.puts.stream().allMatch(r -> "*".equals(r.ifNoneMatch())));
        assertEquals("one", download(first));
        assertEquals("two", download(second));
    }

    @Test
    void deleteInOneTenantLeavesOtherTenantObject() {
        setTenant("acme");
        final var acmeLocation = upload(PATH, "acme contract");

        setTenant("globex");
        final var globexLocation = upload(PATH, "globex contract");
        service.delete(globexLocation);
        // globex replays acme's location: resolved under globex/, acme's object is untouched
        service.delete(acmeLocation);

        assertEquals(1, s3Client.objects.size());
        assertTrue(s3Client.objects.containsKey("acme/" + acmeLocation));

        setTenant("acme");
        assertEquals("acme contract", download(acmeLocation));
    }

    @Test
    void downloadOfOtherTenantLocationDoesNotReadOtherTenantObject() {
        setTenant("acme");
        final var acmeLocation = upload(PATH, "acme contract");

        setTenant("globex");
        assertThrows(ContentStoreException.class, () -> service.download(acmeLocation));
    }

    @Test
    void reuploadToOwnExistingLocationOverwritesInPlace() {
        setTenant("acme");
        final var location = upload(PATH, "v1");

        final var updated = upload(location, "v2");

        assertEquals(location, updated);
        assertEquals(1, s3Client.objects.size());
        assertEquals("v2", download(location));
    }

    @Test
    void conditionalWriteRejectsExistingObject() {
        s3Client.objects.put("acme/x", "existing".getBytes(StandardCharsets.UTF_8));

        assertThrows(S3Exception.class, () -> s3Client.putObject(
                PutObjectRequest.builder().bucket(BUCKET).key("acme/x").ifNoneMatch("*").build(), RequestBody.fromString("new")));
    }

    @Test
    void missingOrInvalidTenantIsRejected() {
        ThreadLocalContextUtil.reset();
        assertThrows(ContentStoreException.class, () -> upload(PATH, "data"));

        for (final var identifier : new String[] { "", "..", "a/b", "a\\b", ".hidden" }) {
            setTenant(identifier);
            assertThrows(ContentStoreException.class, () -> upload(PATH, "data"), identifier);
        }

        assertTrue(s3Client.objects.isEmpty());
    }

    private String upload(String path, String content) {
        return service.upload(path, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "application/pdf");
    }

    private String download(String location) {
        try (var in = service.download(location)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void setTenant(String identifier) {
        ThreadLocalContextUtil
                .setTenant(FineractPlatformTenant.builder().id(1L).name("Tenant " + identifier).tenantIdentifier(identifier).build());
    }

    private static final class InMemoryS3Client implements S3Client {

        private final Map<String, byte[]> objects = new HashMap<>();
        private final List<PutObjectRequest> puts = new ArrayList<>();

        @Override
        public HeadObjectResponse headObject(HeadObjectRequest request) {
            assertEquals(BUCKET, request.bucket());
            if (!objects.containsKey(request.key())) {
                throw (NoSuchKeyException) NoSuchKeyException.builder().statusCode(404).message("Not Found").build();
            }
            return HeadObjectResponse.builder().contentLength((long) objects.get(request.key()).length).build();
        }

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            assertEquals(BUCKET, request.bucket());
            puts.add(request);
            if ("*".equals(request.ifNoneMatch()) && objects.containsKey(request.key())) {
                throw S3Exception.builder().statusCode(412).message("Precondition Failed").build();
            }
            try (var in = body.contentStreamProvider().newStream()) {
                objects.put(request.key(), in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return PutObjectResponse.builder().build();
        }

        @Override
        public <T> T getObject(GetObjectRequest request, ResponseTransformer<GetObjectResponse, T> transformer) {
            assertEquals(BUCKET, request.bucket());
            final var data = objects.get(request.key());
            if (data == null) {
                throw (NoSuchKeyException) NoSuchKeyException.builder().statusCode(404).message("Not Found").build();
            }
            try {
                return transformer.transform(GetObjectResponse.builder().contentLength((long) data.length).build(),
                        AbortableInputStream.create(new ByteArrayInputStream(data)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
            return getObject(request, ResponseTransformer.toInputStream());
        }

        @Override
        public DeleteObjectResponse deleteObject(DeleteObjectRequest request) {
            assertEquals(BUCKET, request.bucket());
            objects.remove(request.key());
            return DeleteObjectResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "s3";
        }

        @Override
        public void close() {}
    }
}
