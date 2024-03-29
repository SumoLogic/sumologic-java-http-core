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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class AggregatingHttpHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(AggregatingHttpHandler.class);
    private final static String REQUEST_ENCODING = "UTF-8";
    private final List<MaterializedHttpRequest> exchanges = new ArrayList<MaterializedHttpRequest>();
    private final Queue<Integer> forceReturnCodes = new ArrayBlockingQueue<Integer>(100);

    // Extract and materialize HTTP Request Body into a String
    private String readRequestBody(HttpExchange httpExchange) throws IOException {
        StringBuilder content = new StringBuilder();
        InputStreamReader is = new InputStreamReader(httpExchange.getRequestBody(), REQUEST_ENCODING);
        int c;
        while ((c = is.read()) != -1) {
            content.append((char) c);
        }

        return content.toString();
    }

    // Extract and materialize HTTP Request from HTTP Exchange
    private MaterializedHttpRequest requestFor(HttpExchange exchange) throws IOException {
        MaterializedHttpRequest request = new MaterializedHttpRequest();
        request.setMethod(exchange.getRequestMethod());
        request.setHeaders(exchange.getRequestHeaders());
        request.setBody(readRequestBody(exchange));

        return request;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (!forceReturnCodes.isEmpty()) {
            int returnCode = forceReturnCodes.poll();
            if (returnCode == HttpURLConnection.HTTP_OK) {
                logger.debug("Handling exchange " + httpExchange);
                exchanges.add(requestFor(httpExchange));
            }
            httpExchange.sendResponseHeaders(returnCode, 0);
            httpExchange.close();
        } else {
            logger.debug("Handling exchange " + httpExchange);
            exchanges.add(requestFor(httpExchange));
            httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
            httpExchange.close();
        }
    }

    public List<MaterializedHttpRequest> getExchanges() {
        return Collections.unmodifiableList(exchanges);
    }

    public void clearExchanges() {
        exchanges.clear();
    }

    public void addForceReturnCode(int returnCode) {
        forceReturnCodes.add(returnCode);
    }

}
