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

import java.util.List;

/**
 * Model class for holding all the recorded calls.
 */
public class RecordedCalls {
    private List<RegisterCall> registerCalls;
    private List<PublishAstCall> publishAstCalls;
    private List<PublishMetricsCall> publishMetricsCalls;
    private List<PublishTracesCall> publishTracesCalls;

    public List<RegisterCall> getRegisterCalls() {
        return registerCalls;
    }

    public void setRegisterCalls(List<RegisterCall> registerCalls) {
        this.registerCalls = registerCalls;
    }

    public List<PublishAstCall> getPublishAstCalls() {
        return publishAstCalls;
    }

    public void setPublishAstCalls(List<PublishAstCall> publishAstCalls) {
        this.publishAstCalls = publishAstCalls;
    }

    public List<PublishMetricsCall> getPublishMetricsCalls() {
        return publishMetricsCalls;
    }

    public void setPublishMetricsCalls(List<PublishMetricsCall> publishMetricsCalls) {
        this.publishMetricsCalls = publishMetricsCalls;
    }

    public List<PublishTracesCall> getPublishTracesCalls() {
        return publishTracesCalls;
    }

    public void setPublishTracesCalls(List<PublishTracesCall> publishTracesCalls) {
        this.publishTracesCalls = publishTracesCalls;
    }
}
