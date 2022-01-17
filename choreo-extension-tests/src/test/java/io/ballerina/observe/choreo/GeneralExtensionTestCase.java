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

import io.ballerina.observe.choreo.client.internal.secret.AnonymousAppSecretHandler;
import io.ballerina.observe.choreo.recording.RecordedTest;
import org.ballerinalang.test.context.LogLeecher;
import org.ballerinalang.test.util.HttpClientRequest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests for Choreo extension.
 */
public class GeneralExtensionTestCase extends SuccessfulStartBaseTestCase {
    private static final String RESTART_TEST_PROJECT_FILE = "restart_test_project_file";

    @Test
    public void testPublishDataToChoreo() throws Exception {
        Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Path storedProjectFile = tempFilesDir.resolve(RESTART_TEST_PROJECT_FILE);
        Files.deleteIfExists(projectFile);  // To test fresh project

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedTest(recordedTest);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeIdFromFileSystem());

        // Storing the choreo project file for dependent test testRestartBallerinaService
        Files.copy(projectFile, storedProjectFile);
    }

    @Test(dependsOnMethods = "testPublishDataToChoreo")
    public void testRestartBallerinaService() throws Exception {
        // Validating if dependant test testRestartBallerinaService choreo project file is present
        Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Path storedProjectFile = tempFilesDir.resolve(RESTART_TEST_PROJECT_FILE);
        Assert.assertTrue(Files.exists(storedProjectFile), "Dependent test run Choreo Project file not present");

        Files.deleteIfExists(projectFile);
        Files.copy(storedProjectFile, projectFile);     // To test existing project
        String previousObsId = getProjectObsIdFromFileSystem(serverInstance.getServerHome());
        String previousNodeId = getNodeIdFromFileSystem();

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedRegisterCall(recordedTest);
        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), 0);
        validateRecordedPublishTracesCall(recordedTest);
        validateRecordedPublishMetricsCall(recordedTest);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeIdFromFileSystem());

        // Validate final choreo project file with previous test choreo project file
        Assert.assertEquals(Files.readString(projectFile), Files.readString(storedProjectFile),
                "Unexpected changes in Choreo Project file");
        Assert.assertEquals(getProjectObsIdFromFileSystem(serverInstance.getServerHome()), previousObsId);
        Assert.assertEquals(getNodeIdFromFileSystem(), previousNodeId);
    }

    @Test
    public void testDebugLogsEnabled() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("CHOREO_EXT_LOG_LEVEL", "DEBUG");

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(envVars);
        validateRecordedTest(recordedTest);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeIdFromFileSystem());
    }

    @Test
    public void testProvidedNodeId() throws Exception {
        String providedNodeId = "ext-test-node-id-1";
        Path nodeIdFile = getNodeIdFilePath();
        Files.writeString(nodeIdFile, providedNodeId);

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedTest(recordedTest);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), providedNodeId);
    }

    @Test
    public void testProvidedNodeIdEnvVar() throws Exception {
        String providedNodeId = "ext-test-node-id-2";
        Map<String, String> envVars = new HashMap<>();
        envVars.put("CHOREO_EXT_NODE_ID", providedNodeId);

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(envVars);
        validateRecordedTest(recordedTest);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), providedNodeId);
    }

    @Test
    public void testMissingNodeId() throws Exception {
        Path nodeIdFile = getNodeIdFilePath();
        Files.deleteIfExists(nodeIdFile);

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedTest(recordedTest);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeIdFromFileSystem());
    }

    protected RecordedTest testExtensionWithLocalPeriscope(Map<String, String> envVars) throws Exception {
        LogLeecher errorLogLeecher = new LogLeecher("error");
        serverInstance.addErrorLogLeecher(errorLogLeecher);
        LogLeecher exceptionLogLeecher = new LogLeecher("Exception");
        serverInstance.addErrorLogLeecher(exceptionLogLeecher);

        RecordedTest recordedTest = super.testExtensionWithLocalPeriscope(envVars);
        Assert.assertFalse(errorLogLeecher.isTextFound(), "Unexpected error log found");
        Assert.assertFalse(exceptionLogLeecher.isTextFound(), "Unexpected exception log found");
        return recordedTest;
    }

    @Test(retryAnalyzer = RetryFailedTestRetryAnalyzer.class)
    public void testExtensionWithChoreoCloud() throws Exception {
        Path nodeIdFile = getNodeIdFilePath();
        Files.deleteIfExists(nodeIdFile);
        testExtension(Collections.emptyMap(), "periscope.choreo.dev:443", "Config.cloud.toml");
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

        startTestService(new HashMap<>(), true);
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
