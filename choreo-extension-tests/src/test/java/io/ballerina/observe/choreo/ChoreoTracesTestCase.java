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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration test for Choreo extension.
 */
public class ChoreoTracesTestCase extends BaseTestCase {
    private BServerInstance serverInstance;

    private static final String TEST_RESOURCE_URL = "http://localhost:9091/test/sum";
    private static final String CHOREO_PROJECT_FILE_NAME = ".choreoproject";
    private static final String RESTART_TEST_PROJECT_FILE = "restart_test_project_file";

    private static final String CHOREO_EXTENSION_LOG_PREFIX = "ballerina: initializing connection with observability " +
            "backend ";
    private static final String CHOREO_EXTENSION_METRICS_ENABLED_LOG =
            "ballerina: started publishing metrics to Choreo";
    private static final String CHOREO_EXTENSION_TRACES_ENABLED_LOG = "ballerina: started publishing traces to Choreo";
    private static final String CHOREO_EXTENSION_URL_LOG_PREFIX = "ballerina: visit ";

    @BeforeMethod
    public void setupTest() throws Exception {
        serverInstance = new BServerInstance(balServer);
    }

    @AfterMethod
    public void cleanUpTest() throws Exception {
        serverInstance.shutdownServer();
    }

    @Test
    public void testPublishDataToChoreo() throws Exception {
        Path projectFile = Paths.get(serverInstance.getServerHome(), CHOREO_PROJECT_FILE_NAME);
        Path storedProjectFile = tempFilesDir.resolve(RESTART_TEST_PROJECT_FILE);

        Files.deleteIfExists(projectFile);  // To test fresh project
        testExtensionWithLocalPeriscope(Collections.emptyMap());

        // Storing the choreo project file for dependent test testRestartBallerinaService
        Files.copy(projectFile, storedProjectFile);
    }

    @Test(dependsOnMethods = "testPublishDataToChoreo")
    public void testRestartBallerinaService() throws Exception {
        // Validating if dependant test testRestartBallerinaService choreo project file is present
        Path projectFile = Paths.get(serverInstance.getServerHome(), CHOREO_PROJECT_FILE_NAME);
        Path storedProjectFile = tempFilesDir.resolve(RESTART_TEST_PROJECT_FILE);
        Assert.assertTrue(Files.exists(storedProjectFile), "Dependent test run Choreo Project file not present");

        Files.deleteIfExists(projectFile);
        Files.copy(storedProjectFile, projectFile);     // To test existing project
        testExtensionWithLocalPeriscope(Collections.emptyMap());

        // Validate final choreo project file with previous test choreo project file
        Assert.assertEquals(Files.readString(projectFile), Files.readString(storedProjectFile),
                "Unexpected changes in Choreo Project file");
    }

    @Test
    public void testDebugLogsEnabled() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("CHOREO_EXT_LOG_LEVEL", "DEBUG");
        testExtensionWithLocalPeriscope(envVars);
    }

    @Test
    public void testProvidedNodeId() throws Exception {
        Path nodeIdFile = getNodeIdFilePath();
        Files.writeString(nodeIdFile, "ext-test-node-id-1");
        testExtensionWithLocalPeriscope(Collections.emptyMap());
    }

    @Test
    public void testProvidedNodeIdEnvVar() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("CHOREO_EXT_NODE_ID", "ext-test-node-id-2");
        testExtensionWithLocalPeriscope(envVars);
    }

    @Test
    public void testMissingNodeId() throws Exception {
        Path nodeIdFile = getNodeIdFilePath();
        Files.deleteIfExists(nodeIdFile);
        testExtensionWithLocalPeriscope(Collections.emptyMap());
    }

    @Test(retryAnalyzer = RetryFailedTestRetryAnalyzer.class)
    public void testExtensionWithChoreoCloud() throws Exception {
        Path nodeIdFile = getNodeIdFilePath();
        Files.deleteIfExists(nodeIdFile);
        testExtension(Collections.emptyMap(), "periscope.choreo.dev:443", "Config.cloud.toml");
    }

    public void testExtensionWithLocalPeriscope(Map<String, String> additionalEnvVars) throws Exception {
        testExtension(additionalEnvVars, "localhost:10090", "Config.toml");
    }

    public void testExtension(Map<String, String> additionalEnvVars, String reporterHost, String configFileName)
            throws Exception {
        LogLeecher choreoExtLogLeecher = new LogLeecher(CHOREO_EXTENSION_LOG_PREFIX + reporterHost);
        serverInstance.addLogLeecher(choreoExtLogLeecher);
        LogLeecher choreoObservabilityUrlLogLeecher = new LogLeecher(CHOREO_EXTENSION_URL_LOG_PREFIX);
        serverInstance.addLogLeecher(choreoObservabilityUrlLogLeecher);
        LogLeecher choreoExtMetricsEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_METRICS_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtMetricsEnabledLogLeecher);
        LogLeecher choreoExtTracesEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_TRACES_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtTracesEnabledLogLeecher);
        LogLeecher errorLogLeecher = new LogLeecher("error");
        serverInstance.addErrorLogLeecher(errorLogLeecher);
        LogLeecher exceptionLogLeecher = new LogLeecher("Exception");
        serverInstance.addErrorLogLeecher(exceptionLogLeecher);

        String configFile = Paths.get("src", "test", "resources", "bal", "choreo_ext_test", configFileName)
                .toFile().getAbsolutePath();
        Map<String, String> env = new HashMap<>(additionalEnvVars);
        env.put("BAL_CONFIG_FILES", configFile);

        // Cleaning up Dependencies.toml to avoid dependency issues in dependency updates
        final String projectDir = Paths.get(RESOURCES_DIR.getAbsolutePath(), "choreo_ext_test").toFile()
                .getAbsolutePath();
        Files.deleteIfExists(Paths.get(projectDir, "Dependencies.toml"));

        int[] requiredPorts = {9091};
        serverInstance.startServer(projectDir, "choreo_ext_test", new String[]{"--offline"}, null, env, requiredPorts);
        Utils.waitForPortsToOpen(requiredPorts, 1000 * 60, false, "localhost");
        choreoExtLogLeecher.waitForText(10000);
        choreoObservabilityUrlLogLeecher.waitForText(10000);
        choreoExtMetricsEnabledLogLeecher.waitForText(1000);

        // Send requests to generate metrics & traces
        String responseData = HttpClientRequest.doGet(TEST_RESOURCE_URL).getData();
        Assert.assertEquals(responseData, "Sum: 53");
        Thread.sleep(11000);    // Data is published every 10 seconds

        // The tracing log is printed on first start of a span
        choreoExtTracesEnabledLogLeecher.waitForText(1000);

        Assert.assertFalse(errorLogLeecher.isTextFound(), "Unexpected error log found");
        Assert.assertFalse(exceptionLogLeecher.isTextFound(), "Unexpected exception log found");

        // Validating generated project files
        Path projectFile = Paths.get(serverInstance.getServerHome(), CHOREO_PROJECT_FILE_NAME);
        Assert.assertTrue(Files.exists(projectFile), "Choreo Project file not generated");
    }

    @Test
    public void testChoreoDisabled() throws Exception {
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

        // Cleaning up Dependencies.toml to avoid dependency issues in dependency updates
        final String projectDir = Paths.get(RESOURCES_DIR.getAbsolutePath(), "choreo_ext_test").toFile()
                .getAbsolutePath();
        Files.deleteIfExists(Paths.get(projectDir, "Dependencies.toml"));

        int[] requiredPorts = {9091};
        serverInstance.startServer(projectDir, "choreo_ext_test", new String[]{"--offline"}, null, requiredPorts);
        Utils.waitForPortsToOpen(requiredPorts, 1000 * 60, false, "localhost");

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
