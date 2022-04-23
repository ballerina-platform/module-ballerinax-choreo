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

package io.ballerina.observe.choreo;

import io.ballerina.observe.choreo.client.ChoreoClient;
import io.ballerina.observe.choreo.client.ChoreoClientHolder;
import io.ballerina.observe.choreo.client.MetadataReader;
import io.ballerina.observe.choreo.client.error.ChoreoClientException;
import io.ballerina.observe.choreo.logging.LogFactory;
import io.ballerina.observe.choreo.logging.Logger;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.observability.ObserveUtils;
import io.ballerina.runtime.observability.BallerinaObserver;


import java.io.IOException;
import java.util.Objects;





/**
 * Native functions used by the Choreo extension objects.
 */
public class InitUtils {
    private InitUtils() {   // Prevent initialization
    }

    private static final String APPLICATION_SECRET_ENV_VAR = "CHOREO_EXT_APPLICATION_SECRET";
    private static final String REPORTER_HOSTNAME_ENV_VAR = "CHOREO_EXT_REPORTER_HOSTNAME";

    private static final Logger LOGGER = LogFactory.getLogger();
    private static final BallerinaObserver observer = new StepcountObserver();
    /**
     * Initialize the Choreo extension.
     *
     * @return error if initialization failed
     */
    public static Object initializeChoreoExtension(BString reporterHostname, int reporterPort,
                                                   boolean reporterUseSSL, BString applicationSecret) {
        ObserveUtils.addObserver(observer);
        MetadataReader metadataReader;
        try {
            metadataReader = new BallerinaMetadataReader();
            LOGGER.debug("Successfully read sequence diagram symbols");
        } catch (IOException e) {
            throw ErrorCreator.createError(StringUtils.fromString("Failed to initialize Choreo client. Please check " +
                "Ballerina.toml"), e);
        }

        String applicationToken = System.getenv(APPLICATION_SECRET_ENV_VAR);
        if (applicationToken == null) {
            applicationToken = applicationSecret.getValue();
        }
        String reporterHost = System.getenv(REPORTER_HOSTNAME_ENV_VAR);
        if (reporterHost == null) {
            reporterHost = reporterHostname.getValue();
        }

        ChoreoClient choreoClient;
        try {
            choreoClient = ChoreoClientHolder.initChoreoClient(metadataReader, reporterHost,
                    reporterPort, reporterUseSSL, applicationToken);
        } catch (ChoreoClientException e) {
            throw ErrorCreator.createError(StringUtils.fromString("Choreo client is not initialized. " +
                    "Please check Config.toml"), e);
        }
        if (Objects.isNull(choreoClient)) {
            throw ErrorCreator.createError(StringUtils.fromString("Choreo client is not initialized. " +
                    "Please check Config.toml"));
        }
        return null;
    }

    /**
     * Initialize Choreo Metrics Reporter.
     * This is called by the MetricReporter ballerina object.
     *
     * @return Error if initializing metrics reporter fails
     */
    public static Object initializeMetricReporter() {
        if (isChoreoClientInitialized()) {
            MetricsReporter metricsExtension = new MetricsReporter();
            metricsExtension.init();
            return null;
        } else {
            throw ErrorCreator.createError(StringUtils.fromString(
                    "Unable to start publishing metrics as Choreo Client is not initialized"));
        }
    }

    /**
     * Check if the Choreo client is initialized.
     *
     * @return true if choreo client is initialized
     */
    public static boolean isChoreoClientInitialized() {
        return !Objects.isNull(ChoreoClientHolder.getChoreoClient());
    }
}





