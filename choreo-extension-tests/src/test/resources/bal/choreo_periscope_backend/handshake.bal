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
import ballerina_test/choreo_periscope_backend.handshake;

const string REGISTER_ABORT_ERROR_PROJECT_SECRET = "xxxxxxxxxxxxxxx-abort-register-error";
const string PUBLISH_AST_ERROR_PROJECT_SECRET = "xxxxxxxxxxxxxxxxxx-publish-ast-error";

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
        log:printInfo("Received Handshake/register call", projectSecret = request.projectSecret);
        handshake:RegisterResponse|error response;
        if (request.projectSecret == REGISTER_ABORT_ERROR_PROJECT_SECRET) {
            response = error grpc:AbortedError("test error for register using project secret " + request.projectSecret);
        } else {
            VersionInfo versionInfo = getVersionInformation(request.projectSecret, request.astHash);
            response = {
                obsId: versionInfo.obsId,
                'version: versionInfo.obsVersion,
                obsUrl: generateObsUrl(versionInfo.obsId, versionInfo.obsVersion),
                sendAst: versionInfo.isNewSyntaxTree,
                tags: [
                    { key: "test-key", value: "test-value" },
                    { key: "response-type", value: versionInfo.isNewSyntaxTree ? "new-obs-version" : "new-obs-group" }
                ]
            };
        }
        recordedRegisterCalls.push({
            request: request,
            response: response is handshake:RegisterResponse ? response : (),
            responseErrorMessage: response is error ? response.toString() : ()
        });
        return response;
    }

    # Mock publish AST remote endpoint.
    #
    # + request - gRPC publish AST request
    # + return - error if publishing the AST fails
    remote function publishAst(handshake:PublishAstRequest request) returns error? {
        log:printInfo("Received Handshake/publishAst call", obsId = request.obsId);
        error? response = ();
        if (request.obsId.startsWith(PUBLISH_AST_ERROR_PROJECT_SECRET)) {
            response = error grpc:AbortedError("test error for publish ast using obs ID " + request.obsId);
        }
        recordedPublishAstCalls.push({
            request: request,
            responseErrorMessage: response is error ? response.toString() : ()
        });
        return response;
    }
}

function generateObsUrl(string obsId, string obsVersion) returns string {
    return "http://choreo.dev/obs/" + obsId + "/" + obsVersion;
}

service "Handshake" on periscopeCallsEndpoint {
    resource function get register/calls() returns RegisterCall[] {
        return recordedRegisterCalls;
    }

    resource function post register/calls(@http:Payload RegisterCall[] newCalls) returns RegisterCall[] {
        log:printInfo("Updated Handshake/register calls", newCallsCount = newCalls.length(),
            previousCallsCount = recordedRegisterCalls.length());
        var previousCalls = recordedRegisterCalls;
        recordedRegisterCalls = newCalls;
        return previousCalls;
    }

    resource function get publishAst/calls() returns PublishAstCall[] {
        return recordedPublishAstCalls;
    }

    resource function post publishAst/calls(@http:Payload PublishAstCall[] newCalls) returns PublishAstCall[] {
        log:printInfo("Updated Handshake/publishAst calls", newCallsCount = newCalls.length(),
            previousCallsCount = recordedPublishAstCalls.length());
        var previousCalls = recordedPublishAstCalls;
        recordedPublishAstCalls = newCalls;
        return previousCalls;
    }
}
