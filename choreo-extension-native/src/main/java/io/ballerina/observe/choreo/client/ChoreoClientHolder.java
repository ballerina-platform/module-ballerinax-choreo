/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ballerina.observe.choreo.client;

import io.ballerina.observe.choreo.client.error.ChoreoClientException;
import io.ballerina.observe.choreo.client.internal.ClientUtils;
import io.ballerina.observe.choreo.client.internal.secret.AnonymousAppSecretHandler;
import io.ballerina.observe.choreo.client.internal.secret.AppSecretHandler;
import io.ballerina.observe.choreo.client.internal.secret.LinkedAppSecretHandler;
import io.ballerina.observe.choreo.logging.LogFactory;
import io.ballerina.observe.choreo.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the Choreo Client used to communicate with the Choreo cloud.
 *
 * @since 2.0.0
 */
public class ChoreoClientHolder {
    private ChoreoClientHolder() {  // Prevent initialization
    }

    private static final Logger LOGGER = LogFactory.getLogger();

    private static ChoreoClient choreoClient;
    private static final Set<AutoCloseable> choreoClientDependents = new HashSet<>();

    private static final String CGROUP_FILE_PATH = "/proc/self/cgroup";
    private static final String NODE_ID_ENV_VAR = "CHOREO_EXT_NODE_ID";
    private static final String CONTAINERIZED_MODE_ENV_VAR = "CHOREO_EXT_CONTAINERIZED_MODE";

    /**
     * Initialize the Choreo client.
     *
     * @return ChoreoClient
     */
    public static synchronized ChoreoClient initChoreoClient(MetadataReader metadataReader, String reporterHostname,
                                                             int reporterPort, boolean reporterUseSSL,
                                                             String applicationSecret)
            throws ChoreoClientException {
        if (choreoClient == null) {
            AppSecretHandler appSecretHandler;
            try {
                appSecretHandler = getAppSecretHandler(applicationSecret);
                LOGGER.debug("Using App Secret Handler " + appSecretHandler.getName());
            } catch (IOException e) {
                LOGGER.error("Failed to initialize Choreo client. " + e.getMessage());
                return null;
            }

            final ChoreoClient newChoreoClient = new ChoreoClient(reporterHostname, reporterPort, reporterUseSSL,
                    appSecretHandler.getAppSecret());

            String nodeId = getNodeId();
            ChoreoClient.RegisterResponse registerResponse = newChoreoClient.register(metadataReader, nodeId);
            try {
                appSecretHandler.associate(registerResponse.getObsId());
            } catch (IOException e) {
                LOGGER.error("Error occurred while associating observability ID with secret. " + e.getMessage());
                return null;
            }
            LOGGER.info("visit " + registerResponse.getObsUrl().replaceAll("%", "%%")
                    + " to access observability data");

            createShutdownHook();
            choreoClient = newChoreoClient;
        }
        return choreoClient;
    }

    /**
     * Get the client that can be used to communicate with Choreo cloud.
     *
     * @return ChoreoClient
     */
    public static ChoreoClient getChoreoClient() {
        return choreoClient;
    }

    private static void createShutdownHook() {
        Thread shutdownHook = new Thread(() -> {
            try {
                choreoClientDependents.forEach(dependent -> {
                    try {
                        dependent.close();
                    } catch (Exception e) {
                        LOGGER.debug("failed to close dependent object" + e.getMessage());
                    }
                });
                choreoClient.close();
            } catch (Exception e) {
                LOGGER.error("failed to close link with Choreo cloud");
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private static AppSecretHandler getAppSecretHandler(String applicationSecretOverride) throws IOException,
            ChoreoClientException {
        if (applicationSecretOverride == null || applicationSecretOverride.isEmpty()) {
            return new AnonymousAppSecretHandler();
        } else {
            return new LinkedAppSecretHandler(applicationSecretOverride);
        }
    }

    /**
     * Get the client that can be used to communicate with Choreo cloud. When the Choreo client is
     * closed the passed dependent object will also be closed.
     *
     * @param dependentObj Object to be closed when the Choreo client is closed
     * @return ChoreoClient
     */
    public static synchronized ChoreoClient getChoreoClient(AutoCloseable dependentObj) {
        final ChoreoClient client = getChoreoClient();
        choreoClientDependents.add(dependentObj);
        return client;
    }

    private static String getNodeId() {
        // Reading directly from the provided enevironment variable
        if (System.getenv().containsKey(NODE_ID_ENV_VAR)) {
            String nodeId = System.getenv(NODE_ID_ENV_VAR);
            LOGGER.debug("Read node ID \"" + nodeId + "\" from environment variable \"" + NODE_ID_ENV_VAR + "\"");
            return nodeId;
        }

        // Reading from /proc/self/cgroup file automatically
        File cgroupFile = new File(CGROUP_FILE_PATH);
        if ("true".equalsIgnoreCase(System.getenv(CONTAINERIZED_MODE_ENV_VAR))) {
            if (cgroupFile.exists()) {
                try {
                    Optional<String> memoryLine = Files.readAllLines(cgroupFile.toPath(), StandardCharsets.UTF_8)
                        .stream()
                        .filter(line -> line.startsWith("memory:/"))
                        .findAny();
                    if (memoryLine.isPresent()) {
                        String[] memoryLineSplit = memoryLine.get().split("/");
                        String nodeId = memoryLineSplit[memoryLineSplit.length - 1];
                        LOGGER.debug("Read node ID " + nodeId + " from file " + cgroupFile);
                        return nodeId;
                    } else {
                        LOGGER.debug("Skipping reading container node Id automatically from file " + cgroupFile +
                            " due to missing memory line");
                    }
                } catch (IOException e) {
                    LOGGER.error("Failed to read container ID from " + cgroupFile.getAbsolutePath() + " due to "
                        + e.getMessage());
                }
            } else {
                LOGGER.debug("Skipping reading container node Id automatically since " + cgroupFile +
                    " file is missing");
            }
        }

        // Reading from an existing ~/.config/choreo/nodeId file
        Path nodeIdConfigFilePath = ClientUtils.getGlobalChoreoConfigDir().resolve("nodeId");
        if (Files.exists(nodeIdConfigFilePath)) {
            try {
                String nodeId = Files.readString(nodeIdConfigFilePath);
                LOGGER.debug("Read node ID " + nodeId + " from existing file " + nodeIdConfigFilePath.toAbsolutePath());
                return nodeId;
            } catch (IOException e) {
                LOGGER.error("Could not read from " + nodeIdConfigFilePath + " due to " + e.getMessage());
            }
        }

        // Generating new random node ID and setting it into ~/.config/choreo/nodeId file
        String nodeId = UUID.randomUUID().toString();
        try {
            Path nodeIdConfigFileParentPath = nodeIdConfigFilePath.getParent();
            if (nodeIdConfigFileParentPath == null || nodeIdConfigFileParentPath.toFile().exists()
                    || nodeIdConfigFileParentPath.toFile().mkdirs()) {
                Files.write(nodeIdConfigFilePath, nodeId.getBytes(StandardCharsets.UTF_8));
                LOGGER.debug("Wrote new node ID " + nodeId + " to file " + nodeIdConfigFilePath.toAbsolutePath());
            } else {
                LOGGER.error("Failed to create " + nodeIdConfigFileParentPath.toAbsolutePath() + " directory");
            }
        } catch (IOException e) {
            LOGGER.error("Could not write to " + nodeIdConfigFilePath + " due to " + e.getMessage());
        }
        return nodeId;
    }
}
