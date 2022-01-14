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
package io.ballerina.observe.choreo.model;

import com.google.gson.reflect.TypeToken;

import java.util.List;

/**
 * Model class for holding the recorded publish metrics calls.
 */
public class PublishMetricsCall {
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
     * Model class for holding the request of the recorded publish metrics calls.
     */
    public static class Request {
        private List<Metric> metrics;
        private String observabilityId;
        private String version;
        private String nodeId;
        private String projectSecret;

        public List<Metric> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<Metric> metrics) {
            this.metrics = metrics;
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
         * Model class for holding the metrics of the request of the recorded publish metrics calls.
         */
        public static class Metric {
            private long timestamp;
            private String name;
            private float value;
            private List<Tag> tags;

            public long getTimestamp() {
                return timestamp;
            }

            public void setTimestamp(long timestamp) {
                this.timestamp = timestamp;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public float getValue() {
                return value;
            }

            public void setValue(float value) {
                this.value = value;
            }

            public List<Tag> getTags() {
                return tags;
            }

            public void setTags(List<Tag> tags) {
                this.tags = tags;
            }
        }
    }

    /**
     * Gson type token to be used for parsing calls.
     */
    public static class CallsTypeToken extends TypeToken<List<PublishMetricsCall>> {
    }
}
