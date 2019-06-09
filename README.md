[![Build Status](https://api.travis-ci.org/SumoLogic/sumologic-java-http-core.svg?branch=master)](https://travis-ci.org/SumoLogic/sumologic-java-http-core)

# sumologic-java-http-core

Core Java components for sending data to Sumo Logic HTTP sources.  Used by [Log4j](https://github.com/SumoLogic/sumo-log4j-appender), [Log4j2](https://github.com/SumoLogic/sumologic-log4j2-appender), and [Logback](https://github.com/SumoLogic/sumologic-logback-appender) appenders to send logs to Sumo Logic.

## Installation

The library can be added to your project using Maven Central by adding the following dependency to a POM file:

```
<dependency>
    <groupId>com.sumologic.plugins.http</groupId>
    <artifactId>sumologic-http-core</artifactId>
    <version>1.3</version>
</dependency>
```

## Usage

The Sumo Logic Java HTTP Core library provides reusable components for sending logs to a [Sumo Logic HTTP Source](https://help.sumologic.com/Send-Data/Sources/02Sources-for-Hosted-Collectors/HTTP-Source).

The three main components are:

- Aggregation: `SumoBufferFlusher` and `BufferFlushingTask`
- Queuing: `BufferWithEviction`, `BufferWithFifoEviction`, and `CostBoundedConcurrentQueue`
- Sending: `SumoBufferFlushingTask`, `SumoBufferFlusher`, and some proxy-related helpers

To use the library, you'll probably want to create a `SumoBufferFlusher` which asynchronously invokes sending via a `SumoHttpSender` based on data placed concurrently in a queue.  For an example, see `SumoHttpSenderTest`.

### Parameters
| Parameter             | Required? | Default Value | Description                                                                                                                                |
|-----------------------|----------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| url                   | Yes      |               | HTTP collection endpoint URL                                                                                                               |
| sourceName            | No       | "Http Input"              | Source name to appear when searching on Sumo Logic by `_sourceName`                                                                                                        |
| sourceHost            | No       | Client IP Address              | Source host to appear when searching on Sumo Logic by `_sourceHost`                                                                                                         |
| sourceCategory        | No       | "Http Input"              | Source category to appear when searching on Sumo Logic by `_sourceCategory`                                                                                                         |
| proxyHost             | No       |               | Proxy host IP address                                                                                                                      |
| proxyPort             | No       |               | Proxy host port number                                                                                                                     |
| proxyAuth             | No       |               | For basic authentication proxy, set to "basic". For NTLM authentication proxy, set to "ntlm". For no authentication proxy, do not specify. |
| proxyUser             | No       |               | Proxy host username for basic and NTLM authentication. For no authentication proxy, do not specify.                                        |
| proxyPassword         | No       |               | Proxy host password for basic and NTLM authentication. For no authentication proxy, do not specify.                                        |
| proxyDomain           | No       |               | Proxy host domain name for NTLM authentication only                                                                                        |
| retryIntervalMs         | No       | 10000         | Retry interval (in ms) if a request fails                                                                                                  |
| connectionTimeoutMs     | No       | 1000          | Timeout (in ms) for connection                                                                                                             |
| socketTimeoutMs         | No       | 60000         | Timeout (in ms) for a socket                                                                                                               |
| messagesPerRequest    | No       | 100           | Number of messages needed to be in the queue before flushing                                                                               |
| maxFlushIntervalMs      | No       | 10000         | Maximum interval (in ms) between flushes                                                                                                   |
| flushingAccuracyMs      | No       | 250           | How often (in ms) that the flushing thread checks the message queue                                                                        |
| maxQueueSizeBytes     | No       | 1000000       | Maximum capacity (in bytes) of the message queue
| flushAllBeforeStopping| No       | false         | Flush all messages before stopping regardless of flushingAccuracyMs

### TLS 1.2 Requirement

Sumo Logic only accepts connections from clients using TLS version 1.2 or greater. To utilize the content of this repo, ensure that it's running in an execution environment that is configured to use TLS 1.2 or greater.

## Development

To compile the plugin:
- Run "mvn clean package" on the pom.xml in the main level of this project.
- To test running a locally built JAR file, you may need to manually add the following dependencies to your project:
```
    <dependencies>
        <dependency>
            <groupId>com.sumologic.plugins.http</groupId>
            <artifactId>sumologic-http-core</artifactId>
            <version>1.0-SNAPSHOT</version>
            <scope>system</scope>
            <systemPath>/path/to/file/sumologic-http-core-1.0-SNAPSHOT.jar</systemPath>
        </dependency>
    </dependencies>
```

## License

The Sumo Logic Java HTTP Core library is published under the Apache Software License, Version 2.0. Please visit http://www.apache.org/licenses/LICENSE-2.0.txt for details.
