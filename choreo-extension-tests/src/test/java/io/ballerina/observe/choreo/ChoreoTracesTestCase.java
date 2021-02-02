/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.observe.choreo;

import org.ballerinalang.test.context.BServerInstance;
import org.ballerinalang.test.context.LogLeecher;
import org.ballerinalang.test.context.Utils;
import org.ballerinalang.test.util.HttpClientRequest;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration test for Choreo extension.
 */
public class ChoreoTracesTestCase extends BaseTestCase {
    private BServerInstance serverInstance;

    private static final File RESOURCES_DIR = Paths.get("src", "test", "resources", "bal").toFile();
    private static final String TEST_RESOURCE_URL = "http://localhost:9091/test/sum";

    private static final String CHOREO_EXTENSION_LOG_PREFIX = "ballerina: initializing connection with observability " +
            "backend ";
    private static final String CHOREO_EXTENSION_METRICS_ENABLED_LOG =
            "ballerina: started publishing metrics to Choreo";
    private static final String CHOREO_EXTENSION_TRACES_ENABLED_LOG = "ballerina: started publishing traces to Choreo";
    private static final String CHOREO_EXTENSION_URL_LOG_PREFIX = "ballerina: visit ";
    private static final String SAMPLE_SERVER_LOG = "[ballerina/http] started HTTP/WS listener 0.0.0.0:9091";

    @BeforeMethod
    public void setup() throws Exception {
        serverInstance = new BServerInstance(balServer);
    }

    @AfterMethod
    public void cleanUpServer() throws Exception {
        serverInstance.shutdownServer();
    }

    @Test
    public void testPublishDataToChoreo() throws Exception {
        LogLeecher choreoExtLogLeecher = new LogLeecher(CHOREO_EXTENSION_LOG_PREFIX + "periscope.choreo.dev:443");
        serverInstance.addLogLeecher(choreoExtLogLeecher);
        LogLeecher choreoObservabilityUrlLogLeecher = new LogLeecher(CHOREO_EXTENSION_URL_LOG_PREFIX);
        serverInstance.addLogLeecher(choreoObservabilityUrlLogLeecher);
        LogLeecher choreoExtMetricsEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_METRICS_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtMetricsEnabledLogLeecher);
        LogLeecher choreoExtTracesEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_TRACES_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtTracesEnabledLogLeecher);
        LogLeecher sampleServerLogLeecher = new LogLeecher(SAMPLE_SERVER_LOG);
        serverInstance.addLogLeecher(sampleServerLogLeecher);
        LogLeecher errorLogLeecher = new LogLeecher("error");
        serverInstance.addErrorLogLeecher(errorLogLeecher);
        LogLeecher exceptionLogLeecher = new LogLeecher("Exception");
        serverInstance.addErrorLogLeecher(exceptionLogLeecher);

        String configFile = Paths.get("src", "test", "resources", "bal", "choreo_ext_test", "Config.toml")
                .toFile().getAbsolutePath();
        Map<String, String> env = new HashMap<>();
        env.put("BALCONFIGFILE", configFile);

        final String projectDir = Paths.get(RESOURCES_DIR.getAbsolutePath(), "choreo_ext_test").toFile()
                .getAbsolutePath();
        int[] requiredPorts = {9091};
        serverInstance.startServer(projectDir, "choreo_ext_test", null, null, env, requiredPorts);
        Utils.waitForPortsToOpen(requiredPorts, 1000 * 60, false, "localhost");
        choreoExtLogLeecher.waitForText(10000);
        choreoObservabilityUrlLogLeecher.waitForText(10000);
        choreoExtMetricsEnabledLogLeecher.waitForText(1000);
        sampleServerLogLeecher.waitForText(1000);

        // Send requests to generate metrics
        String responseData = HttpClientRequest.doGet(TEST_RESOURCE_URL).getData();
        Assert.assertEquals(responseData, "Sum: 53");
        Thread.sleep(3000);

        // The tracing log is printed on first start of a span
        choreoExtTracesEnabledLogLeecher.waitForText(1000);

        Assert.assertFalse(errorLogLeecher.isTextFound(), "Unexpected error log found");
        Assert.assertFalse(exceptionLogLeecher.isTextFound(), "Unexpected exception log found");
    }

    @Test
    public void testChoreoDisabled() throws Exception {
        LogLeecher sampleServerLogLeecher = new LogLeecher(SAMPLE_SERVER_LOG);
        serverInstance.addLogLeecher(sampleServerLogLeecher);
        LogLeecher choreoExtLogLeecher = new LogLeecher(CHOREO_EXTENSION_LOG_PREFIX);
        serverInstance.addLogLeecher(choreoExtLogLeecher);
        LogLeecher choreoExtMetricsEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_METRICS_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtMetricsEnabledLogLeecher);
        LogLeecher choreoExtTracesEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_TRACES_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtTracesEnabledLogLeecher);
        LogLeecher errorLogLeecher = new LogLeecher("error");
        serverInstance.addErrorLogLeecher(errorLogLeecher);
        LogLeecher exceptionLogLeecher = new LogLeecher("Exception");
        serverInstance.addErrorLogLeecher(exceptionLogLeecher);

        final String projectDir = Paths.get(RESOURCES_DIR.getAbsolutePath(), "choreo_ext_test").toFile()
                .getAbsolutePath();
        int[] requiredPorts = {9091};
        serverInstance.startServer(projectDir, "choreo_ext_test", null, null, requiredPorts);
        Utils.waitForPortsToOpen(requiredPorts, 1000 * 60, false, "localhost");
        sampleServerLogLeecher.waitForText(10000);

        String responseData = HttpClientRequest.doGet(TEST_RESOURCE_URL).getData();
        Assert.assertEquals(responseData, "Sum: 53");

        Assert.assertFalse(choreoExtLogLeecher.isTextFound(), "Choreo extension not expected to enable");
        Assert.assertFalse(choreoExtMetricsEnabledLogLeecher.isTextFound(),
                "Choreo extension metrics not expected to enable");
        Assert.assertFalse(choreoExtTracesEnabledLogLeecher.isTextFound(),
                "Choreo extension traces not expected to enable");
        Assert.assertFalse(errorLogLeecher.isTextFound(), "Unexpected error log found");
        Assert.assertFalse(exceptionLogLeecher.isTextFound(), "Unexpected exception log found");
    }
}
