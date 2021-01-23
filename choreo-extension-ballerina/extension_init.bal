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

import ballerina/jballerina.java;
import ballerina/io;
import ballerina/observe;

const EXTENSION_NAME = "choreo";

final configurable string reporterHostname = "periscope.choreo.dev";
final configurable int reporterPort = 443;
final configurable boolean reporterUseSSL = true;
final configurable string applicationSecret = "";

function init() {
    if ((observe:isTracingEnabled() && observe:getTracingProvider() == EXTENSION_NAME)
            || (observe:isMetricsEnabled() && observe:getMetricsReporter() == EXTENSION_NAME)) {
        error? err = externInitializeChoreoExtension(reporterHostname, reporterPort, reporterUseSSL, applicationSecret);
        if (err is error) {
            io:println(err);
        }
    }
    if (observe:isMetricsEnabled() && observe:getMetricsReporter() == EXTENSION_NAME) {
        error? err = externInitializeMetricReporter();
        if (err is error) {
            io:println(err);
        }
    }
}

function externInitializeChoreoExtension(string reporterHostname, int reporterPort, boolean reporterUseSSL,
        string applicationSecret) returns error? = @java:Method {
    'class: "io.ballerina.observe.choreo.InitUtils",
    name: "initializeChoreoExtension"
} external;

function externInitializeMetricReporter() returns error? = @java:Method {
    'class: "io.ballerina.observe.choreo.InitUtils",
    name: "initializeMetricReporter"
} external;
