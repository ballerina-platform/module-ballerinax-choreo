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
import io.ballerina.observe.choreo.client.secret.AnonymousAppSecretHandler;
import io.ballerina.observe.choreo.client.secret.AppSecretHandler;
import io.ballerina.observe.choreo.client.secret.LinkedAppSecretHandler;
import io.ballerina.observe.choreo.logging.LogFactory;
import io.ballerina.observe.choreo.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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

    /**
     * Initialize the Choreo client.
     *
     * @return ChoreoClient
     */
    public static synchronized ChoreoClient initChoreoClient(String reporterHostname, int reporterPort,
                                                             boolean reporterUseSSL, String applicationSecret)
            throws ChoreoClientException {
        if (choreoClient == null) {
            MetadataReader metadataReader;
            try {
                metadataReader = new MetadataReader();
                LOGGER.debug("Successfully read sequence diagram symbols");
            } catch (IOException e) {
                LOGGER.error("Failed to initialize Choreo client. " + e.getMessage());
                return null;
            }

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
        Path instanceIdConfigFilePath = ChoreoConfigHelper.getGlobalChoreoConfigDir().resolve("nodeId");

        String instanceId;
        if (!Files.exists(instanceIdConfigFilePath)) {
            instanceIdConfigFilePath.getParent().toFile().mkdirs();
            instanceId = UUID.randomUUID().toString();
            try {
                Files.write(instanceIdConfigFilePath, instanceId.getBytes(StandardCharsets.UTF_8));
                LOGGER.debug("Written new node ID to file " + instanceIdConfigFilePath.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("could not write to " + instanceIdConfigFilePath);
            }
        } else {
            try {
                instanceId = Files.readString(instanceIdConfigFilePath);
                LOGGER.debug("Read node ID from existing file " + instanceIdConfigFilePath.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("could not read from " + instanceIdConfigFilePath);
                instanceId = UUID.randomUUID().toString();
            }
        }

        return instanceId;
    }
}
