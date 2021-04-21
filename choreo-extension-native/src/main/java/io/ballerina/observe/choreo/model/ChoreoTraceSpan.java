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

package io.ballerina.observe.choreo.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a Trace Span published to Choreo.
 *
 * @since 2.0.0
 */
public class ChoreoTraceSpan {
    private final String traceId;
    private final String spanId;
    private final String serviceName;
    private final String operationName;
    private final long timestamp;
    private final long duration;
    private final Map<String, String> tags;
    private final List<Reference> references;
    private final List<SpanEvent> events;

    public ChoreoTraceSpan(String traceId, String spanId, String serviceName, String operationName,
                           long timestamp, long duration, Map<String, String> tags, List<Reference> references,
                           List<SpanEvent> events) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.serviceName = serviceName;
        this.operationName = operationName;
        this.timestamp = timestamp;
        this.duration = duration;
        this.tags = tags;
        this.references = references;
        this.events = events;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getOperationName() {
        return operationName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDuration() {
        return duration;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public List<Reference> getReferences() {
        return references;
    }

    public List<SpanEvent> getEvents() {
        return events;
    }

    /**
     * Trace reference.
     */
    public static class Reference {
        private final String traceId;
        private final String spanId;
        private final Type refType;

        public Reference(String traceId, String spanId, Type refType) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.refType = refType;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public Type getRefType() {
            return refType;
        }

        /**
         * Type of reference.
         */
        public enum Type {
            CHILD_OF,
            FOLLOWS_FROM
        }
    }
}
