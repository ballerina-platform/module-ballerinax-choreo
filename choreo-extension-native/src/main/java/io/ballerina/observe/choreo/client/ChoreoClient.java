/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
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

package io.ballerina.observe.choreo.client;

import io.ballerina.observe.choreo.client.error.ChoreoClientException;
import io.ballerina.observe.choreo.client.error.ChoreoErrors;
import io.ballerina.observe.choreo.client.model.ChoreoMetric;
import io.ballerina.observe.choreo.client.model.ChoreoTraceSpan;
import io.ballerina.observe.choreo.client.model.SpanEvent;
import io.ballerina.observe.choreo.gen.HandshakeGrpc;
import io.ballerina.observe.choreo.gen.HandshakeOuterClass;
import io.ballerina.observe.choreo.gen.HandshakeOuterClass.PublishAstRequest;
import io.ballerina.observe.choreo.gen.HandshakeOuterClass.RegisterRequest;
import io.ballerina.observe.choreo.gen.TelemetryGrpc;
import io.ballerina.observe.choreo.gen.TelemetryOuterClass;
import io.ballerina.observe.choreo.logging.LogFactory;
import io.ballerina.observe.choreo.logging.Logger;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Manages the communication with Choreo cloud.
 *
 * @since 2.0.0
 */
public class ChoreoClient implements AutoCloseable {
    private static final Logger LOGGER = LogFactory.getLogger();

    private static final int MESSAGE_SIZE_BUFFER_BYTES = 200 * 1024; // Buffer for the rest of the content
    private static final int SERVER_MAX_FRAME_SIZE_BYTES = 4 * 1024 * 1024 - MESSAGE_SIZE_BUFFER_BYTES;
    private static final int MAX_RETRY_COUNT = 4;

    private String id;      // ID received from the handshake
    private String nodeId;
    private String version;
    private final String projectSecret;
    private Map<String, String> additionalTags;

    private final ManagedChannel channel;
    private final HandshakeGrpc.HandshakeBlockingStub registrationClient;
    private final TelemetryGrpc.TelemetryBlockingStub telemetryClient;
    private Thread uploadingThread;
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    public ChoreoClient(String hostname, int port, boolean useSSL, String projectSecret) {
        LOGGER.info("initializing connection with observability backend " + hostname + ":" + port);

        Map<String, ?> periscopeServiceConfig = Map.of("methodConfig", List.of(
                Map.of(
                        "name", List.of(
                                Map.of("service", "v0_1_2.Handshake", "method", "register"),
                                Map.of("service", "v0_2_0.Telemetry", "method", "publishMetrics"),
                                Map.of("service", "v0_2_0.Telemetry", "method", "publishTraces")
                        ),
                        "retryPolicy", Map.of(
                                "maxAttempts", 3.0,
                                "initialBackoff", "0.25s",
                                "maxBackoff", "10s",
                                "backoffMultiplier", 2.0,
                                "retryableStatusCodes", List.of("UNAVAILABLE")
                        )
                ),
                Map.of(
                        "name", List.of(
                                Map.of("service", "v0_1_2.Handshake", "method", "publishAst")
                        ),
                        "retryPolicy", Map.of(
                                "maxAttempts", 3.0,
                                "initialBackoff", "0.5s",
                                "maxBackoff", "30s",
                                "backoffMultiplier", 2.0,
                                "retryableStatusCodes", List.of("UNAVAILABLE")
                        )
                )
        ));

        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(hostname, port)
                .defaultServiceConfig(periscopeServiceConfig)
                .enableRetry();
        if (!useSSL) {
            channelBuilder.usePlaintext();
        }
        channel = channelBuilder.build();
        registrationClient = HandshakeGrpc.newBlockingStub(channel);
        telemetryClient = TelemetryGrpc.newBlockingStub(channel);
        this.projectSecret = projectSecret;
        this.additionalTags = Collections.emptyMap();
    }

    public RegisterResponse register(final MetadataReader metadataReader, String nodeId) throws
            ChoreoClientException {
        RegisterRequest handshakeRequest = RegisterRequest.newBuilder()
                .setAstHash(metadataReader.getAstHash())
                .setProjectSecret(projectSecret)
                .setNodeId(nodeId)
                .build();

        String requestId = UUID.randomUUID().toString();
        CallOptions.Key<String> requestIdHeader = CallOptions.Key.create(REQUEST_ID_HEADER);

        HandshakeOuterClass.RegisterResponse registerResponse;
        try {
            registerResponse = registrationClient.withCompression("gzip").register(handshakeRequest);
            this.id = registerResponse.getObsId();
            this.version = registerResponse.getVersion();
            this.additionalTags = registerResponse.getTagsMap();
            LOGGER.debug("Registered with Periscope with observability ID: " + this.id + ", version: " + this.version
                    + " and node ID: " + nodeId);
        } catch (StatusRuntimeException e) {
            throw ChoreoErrors.getChoreoClientError(e);
        }

        boolean sendProgramJson = registerResponse.getSendAst();
        if (sendProgramJson) {
            uploadingThread = new Thread(() -> {
                try {
                    PublishAstRequest programRequest = PublishAstRequest.newBuilder()
                            .setAst(metadataReader.getAstData())
                            .setObsId(id)
                            .setProjectSecret(projectSecret)
                            .build();
                    callWithRetry((req) -> registrationClient.withCompression("gzip")
                            .withOption(requestIdHeader, requestId)
                            .publishAst(req), programRequest, 2000);
                    uploadingThread = null;
                    LOGGER.debug("Uploading AST completed");
                } catch (StatusRuntimeException e) {
                    switch (e.getStatus().getCode()) {
                        case UNAVAILABLE:
                            LOGGER.error("failed to publish syntax tree as Choreo services are not accessible, " +
                                    "requestId = " + requestId);
                            break;
                        case UNKNOWN:
                            LOGGER.error("Choreo backend is not compatible, requestId = " + requestId);
                            break;
                        default:
                            LOGGER.error("failed to publish syntax tree to Choreo due to " + e.getMessage() +
                                    " , requestId = " + requestId);
                    }
                }
            }, "AST Uploading Thread");
            LOGGER.debug("Starting AST upload with AST hash " + metadataReader.getAstHash() +
                    " ,requestId " + requestId);
            uploadingThread.start();
        }

        this.nodeId = nodeId;
        LOGGER.info("connected to the observability backend with id " + registerResponse.getObsId() + " and version " +
                registerResponse.getVersion());
        return new RegisterResponse(registerResponse.getObsUrl(), this.id);
    }

    /**
     * Data holder for register response call.
     */
    public static class RegisterResponse {
        private final String obsUrl;
        private final String obsId;

        public RegisterResponse(String obsUrl, String obsId) {
            this.obsUrl = obsUrl;
            this.obsId = obsId;
        }

        public String getObsUrl() {
            return obsUrl;
        }

        public String getObsId() {
            return obsId;
        }
    }

    public void publishMetrics(ChoreoMetric[] metrics) throws ChoreoClientException {
        int i = 0;
        while (i < metrics.length) {
            TelemetryOuterClass.MetricsPublishRequest.Builder requestBuilder =
                    TelemetryOuterClass.MetricsPublishRequest.newBuilder();
            String requestId = UUID.randomUUID().toString();
            CallOptions.Key<String> requestIdHeader = CallOptions.Key.create(REQUEST_ID_HEADER);
            int messageSize = 0;
            while (i < metrics.length && messageSize < SERVER_MAX_FRAME_SIZE_BYTES) {
                ChoreoMetric metric = metrics[i];
                TelemetryOuterClass.Metric metricMessage
                        = TelemetryOuterClass.Metric.newBuilder()
                        .setTimestamp(metric.getTimestamp())
                        .setName(metric.getName())
                        .setValue(metric.getValue())
                        .putAllTags(metric.getTags())
                        .putAllTags(additionalTags)
                        .build();

                int currentMessageSize = metricMessage.getSerializedSize();
                if (currentMessageSize >= SERVER_MAX_FRAME_SIZE_BYTES) {
                    LOGGER.error("Dropping metric with size %d larger than gRPC frame limit %d",
                            currentMessageSize, SERVER_MAX_FRAME_SIZE_BYTES, "requestId : " + requestId);
                    i++;
                    continue;
                }
                messageSize += currentMessageSize;
                if (messageSize < SERVER_MAX_FRAME_SIZE_BYTES) {
                    requestBuilder.addMetrics(metricMessage);
                    i++;
                }
            }
            try {
                TelemetryOuterClass.MetricsPublishRequest publishRequest = requestBuilder.setObservabilityId(id)
                        .setNodeId(nodeId)
                        .setVersion(version)
                        .setProjectSecret(projectSecret)
                        .build();
                telemetryClient.withCompression("gzip")
                        .withOption(requestIdHeader, requestId)
                        .publishMetrics(publishRequest);

            } catch (StatusRuntimeException e) {
                throw ChoreoErrors.getChoreoClientError(e);
            }
        }
        LOGGER.debug("Successfully published " + metrics.length + " metrics to Choreo");
    }

    public void publishTraceSpans(List<ChoreoTraceSpan> traceSpans) throws ChoreoClientException {
        int i = 0;
        while (i < traceSpans.size()) {
            TelemetryOuterClass.TracesPublishRequest.Builder requestBuilder =
                    TelemetryOuterClass.TracesPublishRequest.newBuilder();
            int messageSize = 0;
            while (i < traceSpans.size() && messageSize < SERVER_MAX_FRAME_SIZE_BYTES) {
                ChoreoTraceSpan traceSpan = traceSpans.get(i);
                TelemetryOuterClass.TraceSpan.Builder traceSpanBuilder
                        = TelemetryOuterClass.TraceSpan.newBuilder()
                        .setTraceId(traceSpan.getTraceId())
                        .setSpanId(traceSpan.getSpanId())
                        .setServiceName(traceSpan.getServiceName())
                        .setOperationName(traceSpan.getOperationName())
                        .setTimestamp(traceSpan.getTimestamp())
                        .setDuration(traceSpan.getDuration())
                        .putAllTags(traceSpan.getTags())
                        .putAllTags(additionalTags);
                for (ChoreoTraceSpan.Reference reference : traceSpan.getReferences()) {
                    traceSpanBuilder.addReferences(TelemetryOuterClass.TraceSpanReference.newBuilder()
                            .setTraceId(reference.getTraceId())
                            .setSpanId(reference.getSpanId())
                            .setRefType(reference.getRefType() == ChoreoTraceSpan.Reference.Type.CHILD_OF
                                    ? TelemetryOuterClass.TraceReferenceType.CHILD_OF
                                    : TelemetryOuterClass.TraceReferenceType.FOLLOWS_FROM));
                }

                if (traceSpan.getEvents() != null) {
                    for (SpanEvent spanEvent : traceSpan.getEvents()) {
                        traceSpanBuilder.addCheckpoints(TelemetryOuterClass.Checkpoint.newBuilder()
                                .setTimestamp(spanEvent.getTime())
                                .setModuleID(spanEvent.getModuleID())
                                .setPositionID(spanEvent.getPositionID()));
                    }
                }

                TelemetryOuterClass.TraceSpan traceSpanMessage = traceSpanBuilder.build();
                int currentMessageSize = traceSpanMessage.getSerializedSize();
                if (currentMessageSize >= SERVER_MAX_FRAME_SIZE_BYTES) {
                    LOGGER.error("Dropping trace span with size %d larger than gRPC frame limit %d",
                            currentMessageSize, SERVER_MAX_FRAME_SIZE_BYTES);
                    i++;
                    continue;
                }
                messageSize += currentMessageSize;
                if (messageSize < SERVER_MAX_FRAME_SIZE_BYTES) {
                    requestBuilder.addSpans(traceSpanMessage);
                    i++;
                }
            }

            String requestId = UUID.randomUUID().toString();
            CallOptions.Key<String> requestIdHeader = CallOptions.Key.create(REQUEST_ID_HEADER);

            try {
                TelemetryOuterClass.TracesPublishRequest publishRequest = requestBuilder.setObservabilityId(id)
                        .setNodeId(nodeId)
                        .setVersion(version)
                        .setProjectSecret(projectSecret)
                        .build();
                telemetryClient.withCompression("gzip")
                        .withOption(requestIdHeader, requestId)
                        .publishTraces(publishRequest);
            } catch (StatusRuntimeException e) {
                throw ChoreoErrors.getChoreoClientError(e);
            }
        }
        LOGGER.debug("Successfully published " + traceSpans.size() + " traces to Choreo");
    }

    private <T, R> R callWithRetry(Function<T, R> function, T request, int initialBackoffMillis) {
        R response;
        int currentBackoff = initialBackoffMillis;
        for (int i = 0; true; i++) {
            try {
                response = function.apply(request);
                break;
            } catch (StatusRuntimeException e) {
                if (i == MAX_RETRY_COUNT - 1 || e.getStatus() != Status.UNAVAILABLE) {
                    throw e;
                }
            }

            try {
                Thread.sleep(currentBackoff);
            } catch (InterruptedException e) {
                LOGGER.debug("retry backoff interrupted due to " + e.getMessage());
            }
            currentBackoff = currentBackoff * 2;
        }
        return response;
    }

    @Override
    public void close() throws Exception {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        if (Objects.nonNull(uploadingThread)) {
            LOGGER.debug("Waiting for AST upload to complete");
            uploadingThread.join(5000);
        }
    }
}
