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
package org.ballerinalang.observe.trace.extension.choreo;

import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.observability.metrics.spi.MetricReporterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;

import static org.ballerinalang.observe.trace.extension.choreo.Constants.CHOREO_EXTENSION_NAME;
import static org.ballerinalang.observe.trace.extension.choreo.Constants.EXTENSION_PROPERTIES_FILE;
import static org.ballerinalang.observe.trace.extension.choreo.Constants.PACKAGE_NAME;
import static org.ballerinalang.observe.trace.extension.choreo.Constants.PACKAGE_ORG;
import static org.ballerinalang.observe.trace.extension.choreo.Constants.PACKAGE_VERSION_PROPERTY_KEY;

/**
 * This is the metrics reporter extension for the Choreo.
 *
 * @since 2.0.0
 */
public class ChoreoMetricReporterFactory implements MetricReporterFactory {
    private static final PrintStream consoleError = System.err;

    @Override
    public String getName() {
        return CHOREO_EXTENSION_NAME;
    }

    @Override
    public BObject getReporterBObject() {
        String prometheusModuleVersion;
        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(EXTENSION_PROPERTIES_FILE);
            Properties reporterProperties = new Properties();
            reporterProperties.load(stream);
            prometheusModuleVersion = (String) reporterProperties.get(PACKAGE_VERSION_PROPERTY_KEY);
        } catch (IOException | ClassCastException e) {
            consoleError.println("error: unexpected failure in detecting Prometheus extension version");
            return null;
        }
        Module prometheusModule = new Module(PACKAGE_ORG, PACKAGE_NAME, prometheusModuleVersion);
        return ValueCreator.createObjectValue(prometheusModule, "MetricReporter");
    }
}
