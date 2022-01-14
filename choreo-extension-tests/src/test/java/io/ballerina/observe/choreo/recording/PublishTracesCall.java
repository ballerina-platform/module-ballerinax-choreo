/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package io.ballerina.observe.choreo.recording;

import com.google.gson.reflect.TypeToken;

import java.util.List;

/**
 * Model class for holding the recorded publish traces calls.
 */
public class PublishTracesCall {
    private Request request;
    private String responseErrorMessage;

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public String getResponseErrorMessage() {
        return responseErrorMessage;
    }

    public void setResponseErrorMessage(String responseErrorMessage) {
        this.responseErrorMessage = responseErrorMessage;
    }

    /**
     * Model class for holding the request of the recorded publish traces calls.
     */
    public static class Request {
        private List<TraceSpan> spans;
        private String observabilityId;
        private String version;
        private String nodeId;
        private String projectSecret;

        public List<TraceSpan> getSpans() {
            return spans;
        }

        public void setSpans(List<TraceSpan> spans) {
            this.spans = spans;
        }

        public String getObservabilityId() {
            return observabilityId;
        }

        public void setObservabilityId(String observabilityId) {
            this.observabilityId = observabilityId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getProjectSecret() {
            return projectSecret;
        }

        public void setProjectSecret(String projectSecret) {
            this.projectSecret = projectSecret;
        }

        /**
         * Model class for holding the trace spans of the request of the recorded publish traces calls.
         */
        public static class TraceSpan {
            private String traceId;
            private String spanId;
            private String serviceName;
            private String operationName;
            private long timestamp;
            private long duration;
            private List<Reference> references;
            private List<Checkpoint> checkpoints;
            private List<Tag> tags;

            public String getTraceId() {
                return traceId;
            }

            public void setTraceId(String traceId) {
                this.traceId = traceId;
            }

            public String getSpanId() {
                return spanId;
            }

            public void setSpanId(String spanId) {
                this.spanId = spanId;
            }

            public String getServiceName() {
                return serviceName;
            }

            public void setServiceName(String serviceName) {
                this.serviceName = serviceName;
            }

            public String getOperationName() {
                return operationName;
            }

            public void setOperationName(String operationName) {
                this.operationName = operationName;
            }

            public long getTimestamp() {
                return timestamp;
            }

            public void setTimestamp(long timestamp) {
                this.timestamp = timestamp;
            }

            public long getDuration() {
                return duration;
            }

            public void setDuration(long duration) {
                this.duration = duration;
            }

            public List<Reference> getReferences() {
                return references;
            }

            public void setReferences(List<Reference> references) {
                this.references = references;
            }

            public List<Checkpoint> getCheckpoints() {
                return checkpoints;
            }

            public void setCheckpoints(List<Checkpoint> checkpoints) {
                this.checkpoints = checkpoints;
            }

            public List<Tag> getTags() {
                return tags;
            }

            public void setTags(List<Tag> tags) {
                this.tags = tags;
            }

            /**
             * Model class for holding the references of the trace spans of the request of the recorded publish traces
             * calls.
             */
            public static class Reference {
                private String traceId;
                private String spanId;
                private ReferenceType refType;

                public String getTraceId() {
                    return traceId;
                }

                public void setTraceId(String traceId) {
                    this.traceId = traceId;
                }

                public String getSpanId() {
                    return spanId;
                }

                public void setSpanId(String spanId) {
                    this.spanId = spanId;
                }

                public ReferenceType getRefType() {
                    return refType;
                }

                public void setRefType(ReferenceType refType) {
                    this.refType = refType;
                }

                /**
                 * Model class for holding the reference type of the references of the trace spans of the request
                 * of the recorded publish traces calls.
                 */
                public enum ReferenceType {
                    CHILD_OF,
                    FOLLOWS_FROM
                }
            }

            /**
             * Model class for holding the checkpoints of the trace spans of the request of the recorded publish traces
             * calls.
             */
            public static class Checkpoint {
                private long timestamp;
                private String moduleID;
                private String positionID;

                public long getTimestamp() {
                    return timestamp;
                }

                public void setTimestamp(long timestamp) {
                    this.timestamp = timestamp;
                }

                public String getModuleID() {
                    return moduleID;
                }

                public void setModuleID(String moduleID) {
                    this.moduleID = moduleID;
                }

                public String getPositionID() {
                    return positionID;
                }

                public void setPositionID(String positionID) {
                    this.positionID = positionID;
                }
            }
        }
    }

    /**
     * Gson type token to be used for parsing calls.
     */
    public static class CallsTypeToken extends TypeToken<List<PublishTracesCall>> {
    }
}
