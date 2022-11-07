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

import com.sumologic.http.aggregation.SumoBufferFlusher;
import com.sumologic.http.queue.BufferWithEviction;
import com.sumologic.http.queue.BufferWithFifoEviction;
import com.sumologic.http.queue.CostBoundedConcurrentQueue;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SumoHttpSenderTest {

    private static final int PORT = 26932;
    private static final String ENDPOINT_URL = "http://localhost:" + PORT;

    private MockHttpServer server;
    private AggregatingHttpHandler handler;
    private SumoHttpSender sender;
    private BufferWithEviction<String> queue;
    private SumoBufferFlusher flusher;

    private void setUpSender(String url, String sourceName, String sourceHost,
                             String sourceCategory, int messagesPerRequest, int maxNumberOfRetries,
                             long maxFlushIntervalMs, boolean flushAllBeforeStopping,
                             String retryableHttpCodeRegex) {
        sender = new SumoHttpSender();
        sender.setUrl(url);
        sender.setSourceHost(sourceHost);
        sender.setSourceName(sourceName);
        sender.setSourceCategory(sourceCategory);
        sender.setClientHeaderValue("testClient");
        sender.setRetryIntervalMs(10);
        sender.setMaxNumberOfRetries(maxNumberOfRetries);
        if (retryableHttpCodeRegex != null) {
            sender.setRetryableHttpCodeRegex(retryableHttpCodeRegex);
        }
        sender.init();

        queue = new BufferWithFifoEviction<String>(1000000,
                new CostBoundedConcurrentQueue.CostAssigner<String>() {
            @Override
            public long cost(String e) {
                // Note: This is only an estimate for total byte usage, since in UTF-8 encoding,
                // the size of one character may be > 1 byte.
                return e.length();
            }
        });

        flusher = new SumoBufferFlusher(100,
            messagesPerRequest,
            maxFlushIntervalMs,
            sender,
            queue,
            flushAllBeforeStopping);
    }

    @Before
    public void setUp() throws Exception {
        handler = new AggregatingHttpHandler();
        server = new MockHttpServer(PORT, handler);
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (flusher != null) {
            flusher.stop();
        }
        if (sender != null) {
            sender.close();
        }
    }

    @Test
    public void testSingleMessage() throws Exception {
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1, -1, 1, false, null);
        queue.add("This is a message\n");
        flusher.start();
        Thread.sleep(200);
        assertEquals(1, handler.getExchanges().size());
        assertEquals("This is a message\n", handler.getExchanges().get(0).getBody());
        for (MaterializedHttpRequest request: handler.getExchanges()) {
            assertEquals(true, request.getHeaders().getFirst("X-Sumo-Name").equals("testSource"));
            assertEquals(true, request.getHeaders().getFirst("X-Sumo-Category").equals("testCategory"));
            assertEquals(true, request.getHeaders().getFirst("X-Sumo-Host").equals("testHost"));
            assertEquals(true, request.getHeaders().getFirst("X-Sumo-Client").equals("testClient"));
        }
    }

    @Test
    public void testSingleMessageWithoutMetadata() throws Exception {
        setUpSender(ENDPOINT_URL, null, null, null,
                1, -1, 1, false, null);
        queue.add("This is a message\n");
        flusher.start();
        Thread.sleep(200);
        assertEquals(1, handler.getExchanges().size());
        assertEquals("This is a message\n", handler.getExchanges().get(0).getBody());
        for (MaterializedHttpRequest request: handler.getExchanges()) {
            assertEquals(true, request.getHeaders().getFirst("X-Sumo-Name") == null);
            assertEquals(true, request.getHeaders().getFirst("X-Sumo-Category") == null);
            assertEquals(true, request.getHeaders().getFirst("X-Sumo-Host") == null);
            assertEquals(true, request.getHeaders().getFirst("X-Sumo-Client").equals("testClient"));
        }
    }

    @Test
    public void testRetry() throws Exception {
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1, -1,1, false, null);
        // retry on 503, don't retry on 429
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(200);    // Test1 succeeds
        handler.addForceReturnCode(429);    // Test2 dropped
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(200);    // Test3 succeeds
        flusher.start();
        queue.add("Test1");
        Thread.sleep(200);
        queue.add("Test2");
        Thread.sleep(200);
        queue.add("Test3");
        Thread.sleep(1000);
        assertEquals(2, handler.getExchanges().size());
        assertEquals("Test1", handler.getExchanges().get(0).getBody());
        assertEquals("Test3", handler.getExchanges().get(1).getBody());
    }

    @Test
    public void testBatching() throws Exception {
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1000, -1,1000, false, null);
        for (int i = 0; i < 1000; i ++) {
            queue.add("info " + i + "\n");
        }
        flusher.start();
        Thread.sleep(200);
        assertEquals(1, handler.getExchanges().size());
        StringBuffer expected = new StringBuffer();
        for (int i = 0; i < 1000; i ++) {
            expected.append("info " + i + "\n");
        }
        assertEquals(expected.toString(), handler.getExchanges().get(0).getBody());
    }

    @Test
    public void testBatchingBySize() throws Exception {
        // Large time window, ensure all messages get batched by number
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                10, -1, 1000, false, null);
        flusher.start();
        for (int i = 0; i < 10; i ++) {
            queue.add("info " + i);
        }
        Thread.sleep(200);  // Needed due to flush accuracy
        for (int i = 0; i < 10; i ++) {
            queue.add("info " + i);
        }
        Thread.sleep(200);
        assertEquals(2, handler.getExchanges().size());
    }

    @Test
    public void testBatchingByWindow() throws Exception {
        // Small time window, ensure all messages get batched by time
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1000, -1, 100, false, null);
        flusher.start();
        queue.add("Test1");
        Thread.sleep(500);
        queue.add("Test2");
        Thread.sleep(500);
        assertEquals(2, handler.getExchanges().size());
    }

    @Test
    public void testNonFlushOnStop() throws Exception {
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1000, -1,1000, false, null);
        for (int i = 0; i < 10; i ++) {
            queue.add("info " + i + "\n");
        }
        flusher.start();
        flusher.stop();
        Thread.sleep(200);
        assertEquals(0, handler.getExchanges().size());
    }

    @Test
    public void testFlushOnStop() throws Exception {
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1000, -1, 1000, true, null);
        for (int i = 0; i < 10; i ++) {
            queue.add("info " + i + "\n");
        }
        flusher.start();
        flusher.stop();
        Thread.sleep(200);
        assertEquals(10, handler.getExchanges().size());
        StringBuffer expected = new StringBuffer();
        for (int i = 0; i < 10; i ++) {
            assertEquals("info " + i + "\n", handler.getExchanges().get(i).getBody());
        }
    }

    @Test(timeout=1000)
    public void testInvalidUrlOnStop() throws Exception {
        setUpSender("fakeUrl", "testSource", "testHost", "testCategory",
                1, -1, 1, true, null);
        queue.add("This is a message\n");
        flusher.start();
        flusher.stop();
        Thread.sleep(200);
        assertEquals(0, handler.getExchanges().size());
    }

    @Test
    public void testRetryRegex() throws Exception {
        String retryRegex = "^5.*|429|407";
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1, -1, 1, true, retryRegex);
        // Retry on 5xx, 429, and 407 only
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(502);
        handler.addForceReturnCode(504);
        handler.addForceReturnCode(200);    // Test1 succeeds
        handler.addForceReturnCode(403);    // Test2 dropped
        handler.addForceReturnCode(429);
        handler.addForceReturnCode(407);
        handler.addForceReturnCode(200);    // Test3 succeeds
        flusher.start();
        queue.add("Test1");
        Thread.sleep(200);
        queue.add("Test2");
        Thread.sleep(200);
        queue.add("Test3");
        Thread.sleep(1000);

        flusher.stop();

        assertEquals(2, handler.getExchanges().size());
        assertEquals("Test1", handler.getExchanges().get(0).getBody());
        assertEquals("Test3", handler.getExchanges().get(1).getBody());
    }

    @Test
    public void testExceedingMaxNumberOfRetries() throws Exception {
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1, 2, 1, false, null);
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(200);

        flusher.start();
        queue.add("Test");
        Thread.sleep(200);
        flusher.stop();

        assertEquals(0, handler.getExchanges().size());
    }

    @Test
    public void testRetriesReachingMaxNumberOfRetries() throws Exception {
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1, 1, 1, false, null);
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(200);

        flusher.start();
        queue.add("Test");
        Thread.sleep(200);
        flusher.stop();

        assertEquals(1, handler.getExchanges().size());
        assertEquals("Test", handler.getExchanges().get(0).getBody());
    }

    @Test
    public void testNotAllowingRetries() throws Exception {
        setUpSender(ENDPOINT_URL, "testSource", "testHost", "testCategory",
                1, 0, 1, false, null);
        handler.addForceReturnCode(503);
        handler.addForceReturnCode(200);

        flusher.start();
        queue.add("Test1");
        Thread.sleep(200);
        queue.add("Test2");
        Thread.sleep(200);
        flusher.stop();

        assertEquals(1, handler.getExchanges().size());
        assertEquals("Test2", handler.getExchanges().get(0).getBody());
    }
}
