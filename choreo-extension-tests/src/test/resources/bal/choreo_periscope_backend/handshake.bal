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
import ballerina_test/choreo_periscope_backend.handshake;

const string REGISTER_ERROR_PROJECT_SECRET_PREFIX = "register-error-";
const string PUBLISH_AST_ERROR_PROJECT_SECRET_PREFIX = "publish-ast-error-";
const string PUBLISH_METRICS_ERROR_PROJECT_SECRET_PREFIX = "publish-metrics-error-";
const string PUBLISH_TRACES_ERROR_PROJECT_SECRET_PREFIX = "publish-traces-error-";

type RegisterCall record {|
    handshake:RegisterRequest request;
    handshake:RegisterResponse? response;
    string? responseErrorMessage;
|};

RegisterCall[] recordedRegisterCalls = [];

type PublishAstCall record {|
    handshake:PublishAstRequest request;
    string? responseErrorMessage;
|};

PublishAstCall[] recordedPublishAstCalls = [];

@grpc:ServiceDescriptor {
    descriptor: handshake:DESCRIPTOR,
    descMap: handshake:descriptorMap()
}
service "Handshake" on periscopeEndpoint {
    # Mock register remote endpoint.
    #
    # + request - gRPC register request
    # + return - error if register fails or register response
    remote function register(handshake:RegisterRequest request) returns handshake:RegisterResponse|error {
        if (request.projectSecret == REGISTER_ERROR_PROJECT_SECRET_PREFIX) {
            return error("test error for register using project secret " + request.projectSecret);
        } else {
            // Finding repeated register calls
            RegisterCall? repeatedCall = ();
            foreach RegisterCall recordedCall in recordedRegisterCalls {
                handshake:RegisterRequest recordedRequest = recordedCall.request;
                handshake:RegisterResponse? recordedResponse = recordedCall.response;
    
                if (recordedRequest.projectSecret == request.projectSecret && recordedResponse is handshake:RegisterResponse
                        && (repeatedCall is null || recordedRequest.astHash == request.astHash)) {
                    // repeatedCall is null -> First occurance of the project secret
                    // recordedRequest.astHash == request.astHash -> A restart of a program
                    repeatedCall = recordedCall;
                }
            }

            handshake:RegisterResponse|error response;
            if (repeatedCall is ()) {   // Unknown project secret
                string obsId = generateId(request.projectSecret);
                string obsVersion = generateId(request.projectSecret);
                response = {
                    obsId: obsId,
                    'version: obsVersion,
                    obsUrl: generateObsUrl(obsId, obsVersion),
                    sendAst: true,
                    tags: [
                        { key: "test-key", value: "test-value" },
                        { key: "response-type", value: "new-obs-id-and-version" }
                    ]
                };
            } else {
                handshake:RegisterResponse? repeatedResponse = repeatedCall.response;
                string? repeatedResponseErrorMessage = repeatedCall.responseErrorMessage;
                if (repeatedResponseErrorMessage is string) {
                    response = error(repeatedResponseErrorMessage);
                } else if (repeatedResponse is handshake:RegisterResponse) {
                    handshake:RegisterRequest repeatedRequest = repeatedCall.request;
                    boolean isSameAst = repeatedRequest.astHash == request.astHash;
                    string obsId = repeatedResponse.obsId;
                    string obsVersion = isSameAst ? (repeatedResponse.'version) : generateId(request.projectSecret);

                    response = {
                        obsId: obsId,
                        'version: obsVersion,
                        obsUrl: generateObsUrl(obsId, obsVersion),
                        sendAst: !isSameAst,
                        tags: [
                            { key: "test-key", value: "test-value" },
                            { key: "response-type", value: isSameAst ? "repeated-obs-id-only" : "repeated-obs-version" }
                        ]
                    };
                } else {
                    return error("Unexpected error as recorded response is invalid");
                }
            }
            recordedRegisterCalls.push({
                request: request,
                response: response is handshake:RegisterResponse ? response : (),
                responseErrorMessage: response is error ? response.toString() : ()
            });
            return response;
        }
    }

    # Mock publish AST remote endpoint.
    #
    # + request - gRPC publish AST request
    # + return - error if publishing the AST fails
    remote function publishAst(handshake:PublishAstRequest request) returns error? {
        error? response = ();
        if (request.obsId.startsWith(PUBLISH_AST_ERROR_PROJECT_SECRET_PREFIX)) {
            response = error("test error for publish ast using obs ID " + request.obsId);
        }
        recordedPublishAstCalls.push({
            request: request,
            responseErrorMessage: response is error ? response.toString() : response
        });
        return response;
    }
}

float idCounter = 0;

function generateId(string projectSecret) returns string {
    idCounter += 1f;
    int? lastDashIndex = projectSecret.lastIndexOf("-");
    string prefix = lastDashIndex is () ? "" : projectSecret.substring(lastDashIndex, projectSecret.length());
    return prefix + "-" + idCounter.toString();
}

function generateObsUrl(string obsId, string obsVersion) returns string {
    return "http://choreo.dev/obs/" + obsId + "/" + obsVersion;
}

service "Handshake" on periscopeCallsEndpoint {
    resource function get register/calls() returns RegisterCall[] {
        return recordedRegisterCalls;
    }

    resource function post register/calls(@http:Payload RegisterCall[] newCalls) returns RegisterCall[] {
        var previousCalls = recordedRegisterCalls;
        recordedRegisterCalls = newCalls;
        return previousCalls;
    }

    resource function get publishAst/calls() returns PublishAstCall[] {
        return recordedPublishAstCalls;
    }

    resource function post publishAst/calls(@http:Payload PublishAstCall[] newCalls) returns PublishAstCall[] {
        var previousCalls = recordedPublishAstCalls;
        recordedPublishAstCalls = newCalls;
        return previousCalls;
    }
}
