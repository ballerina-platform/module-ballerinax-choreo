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
 * Model class for holding the recorded publish ast calls.
 */
public class PublishAstCall {
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
     * Model class for holding the request of the recorded publish ast calls.
     */
    public static class Request {
        private String obsId;
        private String projectSecret;
        private String ast;

        public String getObsId() {
            return obsId;
        }

        public void setObsId(String obsId) {
            this.obsId = obsId;
        }

        public String getProjectSecret() {
            return projectSecret;
        }

        public void setProjectSecret(String projectSecret) {
            this.projectSecret = projectSecret;
        }

        public String getAst() {
            return ast;
        }

        public void setAst(String ast) {
            this.ast = ast;
        }
    }

    /**
     * Gson type token to be used for parsing calls.
     */
    public static class CallsTypeToken extends TypeToken<List<PublishAstCall>> {
    }
}
