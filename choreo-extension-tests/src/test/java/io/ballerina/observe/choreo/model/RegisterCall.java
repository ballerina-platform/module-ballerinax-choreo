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
 * Model class for holding the recorded register calls.
 */
public class RegisterCall {
    private Request request;
    private Response response;
    private String responseErrorMessage;

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public String getResponseErrorMessage() {
        return responseErrorMessage;
    }

    public void setResponseErrorMessage(String responseErrorMessage) {
        this.responseErrorMessage = responseErrorMessage;
    }

    /**
     * Model class for holding the request of the recorded register calls.
     */
    public static class Request {
        private String astHash;
        private String projectSecret;
        private String nodeId;

        public String getAstHash() {
            return astHash;
        }

        public void setAstHash(String astHash) {
            this.astHash = astHash;
        }

        public String getProjectSecret() {
            return projectSecret;
        }

        public void setProjectSecret(String projectSecret) {
            this.projectSecret = projectSecret;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }
    }

    /**
     * Model class for holding the response of the recorded register calls.
     */
    public static class Response {
        private String obsId;
        private String version;
        private String obsUrl;
        private boolean sendAst;
        private List<Tag> tags;

        public String getObsId() {
            return obsId;
        }

        public void setObsId(String obsId) {
            this.obsId = obsId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getObsUrl() {
            return obsUrl;
        }

        public void setObsUrl(String obsUrl) {
            this.obsUrl = obsUrl;
        }

        public boolean isSendAst() {
            return sendAst;
        }

        public void setSendAst(boolean sendAst) {
            this.sendAst = sendAst;
        }

        public List<Tag> getTags() {
            return tags;
        }

        public void setTags(List<Tag> tags) {
            this.tags = tags;
        }
    }

    /**
     * Gson type token to be used for parsing calls.
     */
    public static class CallsTypeToken extends TypeToken<List<RegisterCall>> {
    }
}
