// Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

type ObservabilityGroup record {|
    string id;
    string projectSecret;
    ObservabilityVersion[] versions;
|};

type ObservabilityVersion record {|
    string id;
    string astHash;
|};

ObservabilityGroup[] obsGroups = [
    {
        id: "xxxxxxxxxx-pre-linked-project-secret-01",
        projectSecret: "xxxxxxxxxx-pre-linked-project-secret",
        versions: [
            {
                id: "xxxxxxxxxx-pre-linked-project-secret-02",
                astHash: "xxxxxxxxxx-pre-linked-project-secret-st-hash"
            }
        ]
    },
    {
        id: "xxxxxxxxxxxxxxxxxx-publish-ast-error-1",
        projectSecret: "xxxxxxxxxxxxxxxxxx-publish-ast-error",
        versions: [
            {
                id: "xxxxxxxxxxxxxxxxxx-publish-ast-error-2",
                astHash: "xxxxxxxxxxxxxxxxxx-publish-ast-error-st-hash"
            }
        ]
    },
    {
        id: "xxxxxxxxxxxxxx-publish-metrics-error-3",
        projectSecret: "xxxxxxxxxxxxxx-publish-metrics-error",
        versions: [
            {
                id: "xxxxxxxxxxxxxx-publish-metrics-error-4",
                astHash: "xxxxxxxxxxxxxx-publish-metrics-error-st-hash"
            }
        ]
    },
    {
        id: "xxxxxxxxxxxxxxx-publish-traces-error-5",
        projectSecret: "xxxxxxxxxxxxxxx-publish-traces-error",
        versions: [
            {
                id: "xxxxxxxxxxxxxxx-publish-traces-error-6",
                astHash: "xxxxxxxxxxxxxxx-publish-traces-error-st-hash"
            }
        ]
    },
    {
        id: "xxxxxxxxx-publish-traces-error-retry-7",
        projectSecret: "xxxxxxxxx-publish-traces-error-retry",
        versions: [
            {
                id: "xxxxxxxxx-publish-traces-error-retry-8",
                astHash: "xxxxxxxxx-publish-traces-error-retry-st-hash"
            }
        ]
    },
    {
        id: "xx-publish-traces-error-buffer-clean-9",
        projectSecret: "xx-publish-traces-error-buffer-clean",
        versions: [
            {
                id: "xx-publish-traces-error-buffer-clean-10",
                astHash: "xx-publish-traces-error-buffer-clean-st-hash"
            }
        ]
    }
];

type VersionInfo record {|
    string obsId;
    string obsVersion;
    boolean isNewSyntaxTree;
|};

function getVersionInformation(string projectSecret, string astHash) returns VersionInfo {
    ObservabilityGroup? pickedObsGroup = ();
    foreach ObservabilityGroup obsGroup in obsGroups {
        if (obsGroup.projectSecret == projectSecret) {
            pickedObsGroup = obsGroup;
        }
    }
    ObservabilityGroup obsGroup;
    if (pickedObsGroup is ()) {
        ObservabilityGroup newObsGroup = {
            id: generateId(projectSecret),
            projectSecret: projectSecret,
            versions: []
        };
        obsGroups.push(newObsGroup);
        obsGroup = newObsGroup;
    } else {
        obsGroup = pickedObsGroup;
    }

    ObservabilityVersion? pickedVersion = ();
    if (obsGroup.versions.length() > 0) {
        ObservabilityVersion lastObsVersion = obsGroup.versions[obsGroup.versions.length() - 1];
        if (lastObsVersion.astHash == astHash) {
            pickedVersion = lastObsVersion;
        }
    }
    ObservabilityVersion obsVersion;
    if (pickedVersion is ()) {
        ObservabilityVersion newObsVersion = {
            id: generateId(projectSecret),
            astHash: astHash
        };
        obsGroup.versions.push(newObsVersion);
        obsVersion = newObsVersion;
    } else {
        obsVersion = pickedVersion;
    }
    return {
        obsId: obsGroup.id,
        obsVersion: obsVersion.id,
        isNewSyntaxTree: (pickedVersion is ())
    };
}

int idCounter = obsGroups.length();

function generateId(string projectSecret) returns string {
    idCounter += 1;
    return projectSecret + "-" + idCounter.toString();
}
