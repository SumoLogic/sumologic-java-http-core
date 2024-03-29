/**
 *    _____ _____ _____ _____    __    _____ _____ _____ _____
 *   |   __|  |  |     |     |  |  |  |     |   __|     |     |
 *   |__   |  |  | | | |  |  |  |  |__|  |  |  |  |-   -|   --|
 *   |_____|_____|_|_|_|_____|  |_____|_____|_____|_____|_____|
 *
 *                UNICORNS AT WARP SPEED SINCE 2010
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.sumologic.http.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.regex.Pattern;


public class SumoHttpSender {
    private static final Logger logger = LoggerFactory.getLogger(SumoHttpSender.class);

    private static final String SUMO_SOURCE_NAME_HEADER = "X-Sumo-Name";
    private static final String SUMO_SOURCE_CATEGORY_HEADER = "X-Sumo-Category";
    private static final String SUMO_SOURCE_HOST_HEADER = "X-Sumo-Host";
    private static final String SUMO_CLIENT_HEADER = "X-Sumo-Client";
    private static final String SUMO_FIELDS_HEADER = "X-Sumo-Fields";

    private long retryIntervalMs = 10000L;
    private int maxNumberOfRetries = -1;
    private int connectionTimeoutMs = 1000;
    private int socketTimeoutMs = 60000;
    private String url = null;
    private String sourceName = null;
    private String sourceCategory = null;
    private String sourceHost = null;
    private ProxySettings proxySettings = null;
    private CloseableHttpClient httpClient = null;
    private String clientHeaderValue = null;
    private String fieldsHeaderValue = null;
    private String retryableHttpCodeRegex = "^5.*";
    private Pattern retryableHttpCodeRegexPattern = null;
    private SumoHttpSender fields = null;

    public ProxySettings getProxySettings() {
        return proxySettings;
    }

    public void setProxySettings(ProxySettings proxySettings) {
        this.proxySettings = proxySettings;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    public void setMaxNumberOfRetries(int maxNumberOfRetries) {
        this.maxNumberOfRetries = maxNumberOfRetries;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public void setSourceCategory(String sourceCategory) {
        this.sourceCategory = sourceCategory;
    }

    public void setSourceHost(String sourceHost) {
        this.sourceHost = sourceHost;
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public void setClientHeaderValue(String clientHeaderValue) {
        this.clientHeaderValue = clientHeaderValue;
    }

    public void setFieldsHeaderValue(String fieldsHeaderValue) {
        this.fieldsHeaderValue = fieldsHeaderValue;
    }

    public void setRetryableHttpCodeRegex(String retryableHttpCodeRegex) {
        this.retryableHttpCodeRegex = retryableHttpCodeRegex;
    }

    public boolean isInitialized() {
        return httpClient != null;
    }

    public void init() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(socketTimeoutMs)
                .setConnectTimeout(connectionTimeoutMs)
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();

        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(new PoolingHttpClientConnectionManager())
                .setDefaultRequestConfig(requestConfig);

        if (proxySettings != null) {
            HttpProxySettingsCreator creator = new HttpProxySettingsCreator(proxySettings);
            creator.configureProxySettings(builder);
        }

        httpClient = builder.build();

        retryableHttpCodeRegexPattern = Pattern.compile(retryableHttpCodeRegex);
    }

    public void close() throws IOException {
        httpClient.close();
        httpClient = null;
    }

    public void send(String body) {
        keepTrying(body);
    }

    private void keepTrying(String body) {
        boolean success = false;
        int tries = 0;
        do {
            tries++;

            try {
                trySend(body);
                success = true;
            } catch (Exception e) {
                try {
                    Thread.sleep(retryIntervalMs);
                } catch (InterruptedException e1) {
                    break;
                }
            }

            if ((tries - 1 == maxNumberOfRetries) && (maxNumberOfRetries >= 0)) {
                if (!success) {
                    logger.warn("Dropping message, because max number of retries has been reached. Message: %s", body);
                }
                break;
            }
        } while (!success && !Thread.currentThread().isInterrupted());
    }

    private void trySend(String body) throws IOException {
        HttpPost post = null;
        try {
            if (url == null)
                throw new IOException("Unknown endpoint");

            post = new HttpPost(url);
            safeSetHeader(post, SUMO_SOURCE_NAME_HEADER, sourceName);
            safeSetHeader(post, SUMO_SOURCE_CATEGORY_HEADER, sourceCategory);
            safeSetHeader(post, SUMO_SOURCE_HOST_HEADER, sourceHost);
            safeSetHeader(post, SUMO_CLIENT_HEADER, clientHeaderValue);
            safeSetHeader(post, SUMO_FIELDS_HEADER, fieldsHeaderValue);
            post.setEntity(new StringEntity(body, Consts.UTF_8));
            HttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                logger.warn("Received non-200 response code from Sumo Service: " + statusCode);
                // Not success. Only retry if status matches retryableHttpCodeRegex
                if (retryableHttpCodeRegexPattern.matcher(String.valueOf(statusCode)).find()) {
                    //need to consume the body if you want to re-use the connection.
                    EntityUtils.consume(response.getEntity());
                    throw new IOException("Encountered retryable status code: " + statusCode);
                }
            } else {
                logger.debug("Successfully sent log request to Sumo Logic");
            }
            //need to consume the body if you want to re-use the connection.
            EntityUtils.consume(response.getEntity());
        } catch (ClientProtocolException e) {
            logger.warn("Dropping message due to invalid URL: " + url);
            try {
                post.abort();
            } catch (Exception ignore) { }
            // Don't throw exception any further
        } catch (IOException e) {
            logger.warn("Could not send log to Sumo Logic", e);
            try {
                post.abort();
            } catch (Exception ignore) { }
            throw e;
        }
    }

    private void safeSetHeader(HttpPost post, String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            post.setHeader(name, value);
        }
    }
}
