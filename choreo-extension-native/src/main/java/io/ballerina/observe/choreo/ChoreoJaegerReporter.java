/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.observe.choreo;

import io.ballerina.observe.choreo.client.ChoreoClient;
import io.ballerina.observe.choreo.client.ChoreoClientHolder;
import io.ballerina.observe.choreo.logging.LogFactory;
import io.ballerina.observe.choreo.logging.Logger;
import io.ballerina.observe.choreo.model.ChoreoTraceSpan;
import io.ballerina.observe.choreo.model.SpanEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.ballerina.runtime.observability.ObservabilityConstants.CHECKPOINT_EVENT_NAME;
import static io.ballerina.runtime.observability.ObservabilityConstants.TAG_KEY_SRC_MODULE;
import static io.ballerina.runtime.observability.ObservabilityConstants.TAG_KEY_SRC_POSITION;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

/**
 * Custom Jaeger tracing reporter for publishing stats to Choreo cloud.
 *
 * @since 2.0.0
 */
public class ChoreoJaegerReporter implements SpanExporter {
    private static final int PUBLISH_INTERVAL_SECS = 10;
    private static final int SPAN_LIST_BOUND = 50000;
    private static final int SPANS_TO_REMOVE = 5000; // 10% of the SPAN_LIST_BOUND
    private static final Logger LOGGER = LogFactory.getLogger();

    private final ScheduledExecutorService executorService;
    private final Task task;

    public ChoreoJaegerReporter() {
        ChoreoClient choreoClient = ChoreoClientHolder.getChoreoClient(this);
        if (Objects.isNull(choreoClient)) {
            throw new IllegalStateException("Choreo client is not initialized");
        }

        executorService = new ScheduledThreadPoolExecutor(1);
        task = new Task(choreoClient);
        executorService.scheduleAtFixedRate(task, PUBLISH_INTERVAL_SECS, PUBLISH_INTERVAL_SECS, TimeUnit.SECONDS);
    }

    /**
     * Called to export sampled {@code Span}s. Note that export operations can be performed
     * simultaneously depending on the type of span processor being used. However, the {@link
     * BatchSpanProcessor} will ensure that only one export can occur at a time.
     *
     * @param spans the collection of sampled Spans to be exported.
     * @return the result of the export, which is often an asynchronous operation.
     */
    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        task.append(spans);
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Exports the collection of sampled {@code Span}s that have not yet been exported. Note that
     * export operations can be performed simultaneously depending on the type of span processor being
     * used. However, the {@link BatchSpanProcessor} will ensure that only one export can occur at a
     * time.
     *
     * @return the result of the flush, which is often an asynchronous operation.
     */
    @Override
    public CompletableResultCode flush() {
        executorService.execute(task);
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Called when {@link SdkTracerProvider#shutdown()} is called, if this {@code SpanExporter} is
     * registered to a {@link SdkTracerProvider} object.
     *
     * @return a {@link CompletableResultCode} which is completed when shutdown completes.
     */
    @Override
    public CompletableResultCode shutdown() {
        LOGGER.info("sending all remaining traces to Choreo");
        executorService.execute(task);
        executorService.shutdown();
        try {
            boolean terminated = executorService.awaitTermination(10, TimeUnit.SECONDS);
            if (!terminated) {
                LOGGER.info("Waiting for trace reporter shutdown timed out");
            }
        } catch (InterruptedException e) {
            LOGGER.error("failed to wait for publishing traces to complete due to " + e.getMessage());
            return CompletableResultCode.ofFailure();
        }
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Worker which handles periodically publishing metrics to Choreo.
     */
    private static class Task implements Runnable {
        private final ChoreoClient choreoClient;
        private List<ChoreoTraceSpan> traceSpans;

        private Task(ChoreoClient choreoClient) {
            this.choreoClient = choreoClient;
            this.traceSpans = new ArrayList<>();
        }

        void append(Collection<SpanData> spans) {
            for (SpanData spanData : spans) {
                Map<String, String> tags = new HashMap<>();
                spanData.getAttributes().forEach((attributeKey, o) -> tags.put(attributeKey.getKey(), (String) o));

                List<ChoreoTraceSpan.Reference> references = new ArrayList<>();
                if (spanData.getParentSpanContext() != null) {
                    ChoreoTraceSpan.Reference reference = new ChoreoTraceSpan.Reference(
                            spanData.getParentSpanContext().getTraceId(),
                            spanData.getParentSpanContext().getSpanId(),
                            ChoreoTraceSpan.Reference.Type.CHILD_OF
                    );
                    references.add(reference);
                }

                List<SpanEvent> events = new ArrayList<>();
                for (EventData eventData : spanData.getEvents()) {
                    if (eventData.getName().equals(CHECKPOINT_EVENT_NAME)) {
                        SpanEvent event = new SpanEvent(
                                eventData.getEpochNanos(),
                                eventData.getAttributes().get(AttributeKey.stringKey(TAG_KEY_SRC_MODULE)),
                                eventData.getAttributes().get(AttributeKey.stringKey(TAG_KEY_SRC_POSITION))
                        );
                        events.add(event);
                    }
                }

                long timestamp = spanData.getStartEpochNanos() / 1000000; // in millis
                long duration = (spanData.getEndEpochNanos() - spanData.getStartEpochNanos()) / 1000000; // in millis
                ChoreoTraceSpan traceSpan = new ChoreoTraceSpan(spanData.getTraceId(), spanData.getSpanId(),
                        spanData.getResource().getAttributes().get(SERVICE_NAME), spanData.getName(),
                        timestamp, duration, tags, references,
                        events);
                synchronized (this) {
                    traceSpans.add(traceSpan);
                }
            }
        }

        @Override
        public void run() {
            List<ChoreoTraceSpan> swappedTraceSpans;
            synchronized (this) {
                if (traceSpans.size() > 0) {
                    swappedTraceSpans = traceSpans;
                    traceSpans = new ArrayList<>();
                } else {
                    swappedTraceSpans = Collections.emptyList();
                }
            }

            if (swappedTraceSpans.size() > 0) {
                if (!Objects.isNull(choreoClient)) {
                    try {
                        choreoClient.publishTraceSpans(swappedTraceSpans);
                    } catch (Throwable t) {
                        synchronized (this) {
                            if (swappedTraceSpans.size() > SPAN_LIST_BOUND) {
                                int spanCount = 0;
                                Random random = new Random();
                                // Remove 10% of the SPAN_LIST_BOUND
                                while (spanCount < SPANS_TO_REMOVE) {
                                    if (swappedTraceSpans.size() > 0) {
                                        int randomSpanPos = random.nextInt(swappedTraceSpans.size());
                                        String traceID = swappedTraceSpans.get(randomSpanPos).getTraceId();
                                        for (int j = 0; j < swappedTraceSpans.size(); j++) {
                                            if (swappedTraceSpans.get(j).getTraceId().equals(traceID)) {
                                                swappedTraceSpans.remove(j);
                                                // Reduce the count as well since the size of the arrayList shrink
                                                j--;
                                                spanCount++;
                                            }
                                        }
                                    } else {
                                        break;
                                    }
                                }
                                LOGGER.info("span buffer is full : " + "dropped " + spanCount + " spans");
                            }
                            traceSpans.addAll(swappedTraceSpans);
                        }
                        LOGGER.error("failed to publish traces to Choreo due to " + t.getMessage());
                    }
                }
            }
        }
    }
}
