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
import ballerina_test/choreo_periscope_backend.handshake;

const string REGISTER_ERROR_PROJECT_SECRET_PREFIX = "register-error-";
const string PUBLISH_AST_ERROR_PROJECT_SECRET_PREFIX = "publish-ast-error-";
const string PUBLISH_METRICS_ERROR_PROJECT_SECRET_PREFIX = "publish-metrics-error-";
const string PUBLISH_TRACES_ERROR_PROJECT_SECRET_PREFIX = "publish-traces-error-";

type RegisterCall record {|
    handshake:RegisterRequest request;
    handshake:RegisterResponse|error response;
|};

RegisterCall[] recordedRegisterCalls = [];

type PublishAstCall record {|
    handshake:PublishAstRequest request;
    error? response;
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
                handshake:RegisterResponse|error recordedResponse = recordedCall.response;
    
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
                handshake:RegisterResponse|error repeastedResponse = repeatedCall.response;
                if (repeastedResponse is error) {
                    response = repeastedResponse;
                } else {
                    handshake:RegisterRequest repeastedRequqest = repeatedCall.request;
                    boolean isSameAst = repeastedRequqest.astHash == request.astHash;
                    string obsId = repeastedResponse.obsId;
                    string obsVersion = isSameAst ? (repeastedResponse.'version) : generateId(request.projectSecret);

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
                }
            }
            recordedRegisterCalls.push({
                request: request,
                response: response
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
            response: response
        });
        return response;
    }
}

int idCounter = 0;

function generateId(string projectSecret) returns string {
    idCounter += 1;
    int? lastDashIndex = projectSecret.lastIndexOf("-");
    string prefix = lastDashIndex is () ? "" : projectSecret.substring(lastDashIndex, projectSecret.length());
    return prefix + "-" + idCounter.toString();
}

function generateObsUrl(string obsId, string obsVersion) returns string {
    return "http://choreo.dev/obs/" + obsId + "/" + obsVersion;
}
