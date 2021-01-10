// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/java;
import ballerina/io;
import ballerina/config;

const EXTENSION_NAME = "choreo";

const string OBSERVABILITY_METRICS_ENABLED_CONFIG = "b7a.observability.metrics.enabled";
final boolean OBSERVABILITY_METRICS_ENABLED = config:getAsBoolean(OBSERVABILITY_METRICS_ENABLED_CONFIG, false);

const string OBSERVABILITY_METRICS_REPORTER_NAME_CONFIG = "b7a.observability.metrics.reporter";
final string OBSERVABILITY_METRICS_REPORTER_NAME = config:getAsString(OBSERVABILITY_METRICS_REPORTER_NAME_CONFIG, "prometheus");

function init() {
    if (OBSERVABILITY_METRICS_ENABLED && OBSERVABILITY_METRICS_REPORTER_NAME == EXTENSION_NAME) {
        error? err = externInitializeMetricReporter();
        if (err is error) {
            io:println(err);
        }
    }
}

function externInitializeMetricReporter() returns error? = @java:Method {
    'class: "io.ballerina.observe.choreo.InitUtils",
    name: "initializeMetricReporter"
} external;
