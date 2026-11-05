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
package org.apache.fineract.infrastructure.creditbureau.config;

import okhttp3.OkHttpClient;
import org.apache.fineract.infrastructure.creditbureau.service.CreditBureauUrlValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CreditBureauHttpClientConfig {

    public static final String CREDIT_BUREAU_HTTP_CLIENT = "creditBureauOkHttpClient";

    @Bean(CREDIT_BUREAU_HTTP_CLIENT)
    public OkHttpClient creditBureauOkHttpClient(@Qualifier("okHttpClient") final OkHttpClient okHttpClient, final CreditBureauUrlValidator urlValidator) {
        return okHttpClient.newBuilder().followRedirects(false).followSslRedirects(false).dns(urlValidator).build();
    }
}
