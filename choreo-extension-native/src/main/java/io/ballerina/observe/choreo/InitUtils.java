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
import io.ballerina.observe.choreo.client.error.ChoreoClientException;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Native functions used by the Choreo extension objects.
 */
public class InitUtils {
    private static final PrintStream consoleError = System.err;

    static {
        ChoreoClient choreoClient = null;
        try {
            choreoClient = ChoreoClientHolder.initChoreoClient();
        } catch (ChoreoClientException e) {
            consoleError.println("error: Choreo client is not initialized. Please check Ballerina configurations. " +
                    "reason " + e.getMessage());
        }
        if (Objects.isNull(choreoClient)) {
            consoleError.println("error: Choreo client is not initialized. Please check Ballerina configurations.");
        }
    }

    /**
     * Initialize Choreo Metrics Reporter.
     * This is called by the MetricReporter ballerina object.
     *
     * @return Error if initializing metrics reporter fails
     */
    public static BError initializeMetricReporter() {
        if (isChoreoClientInitialized()) {
            MetricsReporter metricsExtension = new MetricsReporter();
            return metricsExtension.init();
        } else {
            return ErrorCreator.createError(StringUtils.fromString(
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
