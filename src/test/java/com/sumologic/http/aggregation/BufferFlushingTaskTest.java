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

package com.sumologic.http.aggregation;

import com.sumologic.http.queue.BufferWithFifoEviction;
import com.sumologic.http.queue.CostBoundedConcurrentQueue;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BufferFlushingTaskTest {

    public CostBoundedConcurrentQueue.CostAssigner<String> sizeElements =
        new CostBoundedConcurrentQueue.CostAssigner<String>() {
            @Override
            public long cost(String e) {
                return e.length();
            }
    };

    private List<List<String>> tasks;
    private BufferWithFifoEviction<String> queue;

    @Before
    public void setUp() {
        tasks = new ArrayList<List<String>>();
        queue = new BufferWithFifoEviction<String>(1000, sizeElements);
    }

    @Test
    public void testFlushBySize() {
        BufferFlushingTask<String, List<String>> task =
                createTask(Integer.MAX_VALUE, 3);

        task.run();
        assertTrue(tasks.isEmpty());
        queue.add("msg1");
        queue.add("msg2");

        task.run();
        assertTrue(tasks.isEmpty());
        queue.add("msg3");

        task.run();
        assertEquals(1, tasks.size());
        List<String> aggregatedResult = tasks.get(0);
        assertEquals(3, aggregatedResult.size());
        assertEquals(Arrays.asList("msg1", "msg2", "msg3"), aggregatedResult);
    }

    @Test
    public void testFlushByDate_Immediate() {
        BufferFlushingTask<String, List<String>> task =
                createTask(-1, Integer.MAX_VALUE);

        task.run();
        assertTrue(tasks.isEmpty());

        queue.add("msg1");
        queue.add("msg2");
        task.run();
        assertEquals(1, tasks.size());

        queue.add("msg3");
        task.run();
        assertEquals(2, tasks.size());

        List<String> aggregatedResult = tasks.get(0);
        assertEquals(2, aggregatedResult.size());
        assertEquals(Arrays.asList("msg1", "msg2"), aggregatedResult);
    }

    @Test
    public void testFlushByDate_LongInterval() {
        BufferFlushingTask<String, List<String>> task =
                createTask(Integer.MAX_VALUE, Integer.MAX_VALUE);
        task.run();
        assertTrue(tasks.isEmpty());
        queue.add("msg1");
        queue.add("msg2");

        task.run();
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void testFlushDoesNotSendTooManyMessagesAtOnce() {
        // given
        BufferFlushingTask<String, List<String>> task =
                createTask(Integer.MAX_VALUE, 2);

        // when
        queue.add("msg1");
        queue.add("msg2");
        queue.add("msg3");
        task.flushAndSend();

        // test
        assertEquals(2, tasks.get(0).size());
    }

    private BufferFlushingTask<String, List<String>> createTask(
            final long maxFlushIntervalMs, final int messagesPerRequest) {

        return new BufferFlushingTask<String, List<String>>(queue) {

            @Override
            protected long getMaxFlushIntervalMs() {
                return maxFlushIntervalMs;
            }

            @Override
            protected int getMessagesPerRequest() {
                return messagesPerRequest;
            }

            @Override
            protected List<String> aggregate(List<String> messages) {
                return messages;
            }

            @Override
            protected void sendOut(List<String> body) {
                tasks.add(body);
            }
        };
    }
}
