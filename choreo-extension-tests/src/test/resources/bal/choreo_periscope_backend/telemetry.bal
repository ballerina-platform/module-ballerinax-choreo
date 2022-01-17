// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import ballerina/grpc;
import ballerina/http;
import ballerina/log;
import ballerina_test/choreo_periscope_backend.telemetry;

const string PUBLISH_METRICS_ERROR_PROJECT_SECRET_PREFIX = "xxxxxxxxxxxxxx-publish-metrics-error-";
const string PUBLISH_TRACES_ERROR_PROJECT_SECRET_PREFIX = "xxxxxxxxxxxxxxx-publish-traces-error-";
const string PUBLISH_TRACES_ERROR_RETRY_PROJECT_SECRET_PREFIX = "xxxxxxxxx-publish-traces-error-retry-";

type PublishMetricsCall record {|
    telemetry:MetricsPublishRequest request;
    string? responseErrorMessage;
|};

PublishMetricsCall[] recordedPublishMetricsCall = [];

type PublishTracesCall record {|
    telemetry:TracesPublishRequest request;
    string? responseErrorMessage;
|};

PublishTracesCall[] recordedPublishTracesCall = [];

@grpc:ServiceDescriptor {
    descriptor: telemetry:DESCRIPTOR,
    descMap: telemetry:descriptorMap()
}
service "Telemetry" on periscopeEndpoint {
    # Mock publish metrics remote endpoint.
    #
    # + request - gRPC publish metrics request
    # + return - error if publishing the metrics fails
    remote function publishMetrics(telemetry:MetricsPublishRequest request) returns error? {
        log:printInfo("Received Telemetry/publishMetrics call", obsId = request.observabilityId,
            obsVersion = request.'version);
        error? response = ();
        if (request.observabilityId.startsWith(PUBLISH_METRICS_ERROR_PROJECT_SECRET_PREFIX)) {
            response = error grpc:AbortedError("test error for publish metrics using obs ID " + request.observabilityId);
        }
        recordedPublishMetricsCall.push({
            request: request,
            responseErrorMessage: response is error ? response.toString() : ()
        });
        return response;
    }

    # Mock publish traces remote endpoint.
    #
    # + request - gRPC publish traces request
    # + return - error if publishing the traces fails
    remote function publishTraces(telemetry:TracesPublishRequest request) returns error? {
        log:printInfo("Received Telemetry/publishTraces call", obsId = request.observabilityId,
            obsVersion = request.'version);
        error? response = ();
        if (request.observabilityId.startsWith(PUBLISH_TRACES_ERROR_PROJECT_SECRET_PREFIX)) {
            response = error grpc:AbortedError("test error for publish traces using obs ID " + request.observabilityId);
        } else if (request.observabilityId.startsWith(PUBLISH_TRACES_ERROR_RETRY_PROJECT_SECRET_PREFIX)) {
            boolean alreadyFailed = false;
            foreach PublishTracesCall publishCall in recordedPublishTracesCall {
                if (publishCall.request.observabilityId == request.observabilityId) {
                    alreadyFailed = true;
                }
            }
            if (!alreadyFailed) {
                response = error grpc:AbortedError("test error for retry for publish traces using obs ID " +
                    request.observabilityId);
            }
        }
        recordedPublishTracesCall.push({
            request: request,
            responseErrorMessage: response is error ? response.toString() : ()
        });
        return response;
    }
}

service "Telemetry" on periscopeCallsEndpoint {
    resource function get publishMetrics/calls() returns PublishMetricsCall[] {
        return recordedPublishMetricsCall;
    }

    resource function post publishMetrics/calls(@http:Payload PublishMetricsCall[] newCalls) returns PublishMetricsCall[] {
        log:printInfo("Updated Telemetry/publishMetrics calls", newCallsCount = newCalls.length(),
            previousCallsCount = recordedPublishMetricsCall.length());
        PublishMetricsCall[] previousCalls = recordedPublishMetricsCall;
        recordedPublishMetricsCall = newCalls;
        return previousCalls;
    }

    resource function get publishTraces/calls() returns PublishTracesCall[] {
        return recordedPublishTracesCall;
    }

    resource function post publishTraces/calls(@http:Payload PublishTracesCall[] newCalls) returns PublishTracesCall[] {
        log:printInfo("Updated Telemetry/publishTraces calls", newCallsCount = newCalls.length(),
            previousCallsCount = recordedPublishTracesCall.length());
        PublishTracesCall[] previousCalls = recordedPublishTracesCall;
        recordedPublishTracesCall = newCalls;
        return previousCalls;
    }
}
