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
import io.ballerina.observe.choreo.recording.PublishMetricsCall;
import io.ballerina.observe.choreo.recording.PublishTracesCall;
import io.ballerina.observe.choreo.recording.RecordedTest;
import io.ballerina.observe.choreo.recording.RegisterCall;
import io.ballerina.observe.choreo.recording.Tag;
import org.ballerinalang.test.context.BallerinaTestException;
import org.ballerinalang.test.context.LogLeecher;
import org.ballerinalang.test.util.HttpClientRequest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    @Test(groups = "linux-only", enabled = false)
    public void testContainerizedMode() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("CHOREO_EXT_CONTAINERIZED_MODE", "true");

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(envVars);
        validateRecordedTest(recordedTest);

        String cgroupFileContent = Files.readString(Paths.get("/proc/self/cgroup"));
        Assert.assertTrue(cgroupFileContent.contains(
                "/" + recordedTest.getRegisterCalls().get(0).getRequest().getNodeId() + "\n"));
    }

    @Test
    public void testLinkedAppSecret() throws Exception {
        LogLeecher errorLogLeecher = new LogLeecher("error");
        serverInstance.addErrorLogLeecher(errorLogLeecher);
        LogLeecher exceptionLogLeecher = new LogLeecher("Exception");
        serverInstance.addErrorLogLeecher(exceptionLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), "ignored-obs-id");
        setProjectSecretIntoFileSystem("ignored-obs-id", "ignored-project-secret");

        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();
        testExtension(Collections.emptyMap(), "localhost:10090", "Config.linkedapp.toml");
        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        validateRecordedRegisterCall(recordedTest);
        validateRecordedPublishAstCall(recordedTest);
        validateRecordedPublishTracesCall(recordedTest);
        validateRecordedPublishMetricsCall(recordedTest);

        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getProjectSecret(),
                "xxxxxxxxxx-pre-linked-project-secret");
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getResponse().getObsId(),
                "xxxxxxxxxx-pre-linked-project-secret-01");

        Assert.assertFalse(errorLogLeecher.isTextFound(), "Unexpected error log found");
        Assert.assertFalse(exceptionLogLeecher.isTextFound(), "Unexpected exception log found");
    }

    @Test
    public void testExtensionDataPublishBatching() throws BallerinaTestException, IOException, InterruptedException {
        LogLeecher choreoExtLogLeecher = new LogLeecher(CHOREO_EXTENSION_LOG_PREFIX + "localhost:10090");
        serverInstance.addLogLeecher(choreoExtLogLeecher);
        LogLeecher choreoExtensionConnectedLogLeecher = new LogLeecher(CHOREO_EXTENSION_CONNECTED_LOG_PREFIX);
        serverInstance.addLogLeecher(choreoExtensionConnectedLogLeecher);
        LogLeecher choreoExtMetricsEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_METRICS_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtMetricsEnabledLogLeecher);
        LogLeecher choreoExtTracesEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_TRACES_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtTracesEnabledLogLeecher);
        LogLeecher errorLogLeecher = new LogLeecher("error");
        serverInstance.addErrorLogLeecher(errorLogLeecher);
        LogLeecher exceptionLogLeecher = new LogLeecher("Exception");
        serverInstance.addErrorLogLeecher(exceptionLogLeecher);

        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();

        startTestService(Collections.emptyMap(), new String[0], true, "Config.toml");
        choreoExtLogLeecher.waitForText(10000);
        choreoExtensionConnectedLogLeecher.waitForText(10000);
        choreoExtMetricsEnabledLogLeecher.waitForText(1000);

        // Send requests to generate metrics & traces
        HttpClientRequest.CheckedFunction<BufferedReader, String> responseBuilder = (bufferedReader) -> {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        };

        try {
            String responseData = HttpClientRequest.doGet("http://localhost:9091/test/loopWithLargeTags",
                    300000, responseBuilder).getData();
            Assert.assertEquals(responseData, "Sum: 38250");
        } catch (IOException e) {
            Assert.fail("Failed to generated requests", e);
        }
        Thread.sleep(11000);    // Data is published every 10 seconds

        // The tracing log is printed on first start of a span
        choreoExtTracesEnabledLogLeecher.waitForText(1000);

        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        Assert.assertFalse(errorLogLeecher.isTextFound(), "Unexpected error log found");
        Assert.assertFalse(exceptionLogLeecher.isTextFound(), "Unexpected exception log found");

        validateRecordedRegisterCall(recordedTest);
        validateRecordedPublishAstCall(recordedTest);

        RegisterCall registerCall = recordedTest.getRegisterCalls().get(0);
        String nodeId = registerCall.getRequest().getNodeId();
        String projectSecret = registerCall.getRequest().getProjectSecret();
        String obsId = registerCall.getResponse().getObsId();
        String obsVersion = registerCall.getResponse().getVersion();
        List<Tag> periscopeTags = registerCall.getResponse().getTags();

        // Validate recorded publish metrics call
        int publishMetricsCalls = recordedTest.getPublishMetricsCalls().size();
        Assert.assertTrue(publishMetricsCalls >= 2,
                "Unexpected number of publish metrics calls: " + publishMetricsCalls);
        int totalNonEmptyMetricsPublishRequests = 0;
        for (PublishMetricsCall publishMetricsCall : recordedTest.getPublishMetricsCalls()) {
            Assert.assertEquals(publishMetricsCall.getRequest().getObservabilityId(), obsId);
            Assert.assertEquals(publishMetricsCall.getRequest().getVersion(), obsVersion);
            Assert.assertEquals(publishMetricsCall.getRequest().getNodeId(), nodeId);
            Assert.assertEquals(publishMetricsCall.getRequest().getProjectSecret(), projectSecret);
            publishMetricsCall.getRequest().getMetrics().forEach(metric ->
                    Assert.assertTrue(metric.getTags().containsAll(periscopeTags)));
            Assert.assertNull(publishMetricsCall.getResponseErrorMessage());

            publishMetricsCall.getRequest().getMetrics().forEach(metric -> {
                Assert.assertTrue(metric.getTimestamp() > recordedTest.getStartTimestamp());
                Assert.assertTrue(metric.getTimestamp() < recordedTest.getEndTimestamp());
            });
            if (publishMetricsCall.getRequest().getMetrics().size() == 1) {
                PublishMetricsCall.Request.Metric metric = publishMetricsCall.getRequest().getMetrics().get(0);
                Assert.assertEquals(metric.getName(), "up");
                Assert.assertEquals(metric.getValue(), 1f);
                Assert.assertEquals(metric.getTags(), periscopeTags);
            } else {
                Assert.assertTrue(publishMetricsCall.getRequest().getMetrics().size() > 1);
                totalNonEmptyMetricsPublishRequests += 1;
            }
        }
        Assert.assertTrue(totalNonEmptyMetricsPublishRequests >= 2 && totalNonEmptyMetricsPublishRequests <= 3,
                "Unexpected number of total non empty metrics publish requests: " +
                        totalNonEmptyMetricsPublishRequests);

        // Validate recorded publish traces call IDs
        int totalPublishTracesCallCount = recordedTest.getPublishTracesCalls().size();
        Assert.assertTrue(totalPublishTracesCallCount >= 2 && totalPublishTracesCallCount <= 3,
                "Unexpected number of publish traces calls: " + totalPublishTracesCallCount);
        int totalSpans = 0;
        for (PublishTracesCall publishTracesCall : recordedTest.getPublishTracesCalls()) {
            Assert.assertEquals(publishTracesCall.getRequest().getObservabilityId(), obsId);
            Assert.assertEquals(publishTracesCall.getRequest().getVersion(), obsVersion);
            Assert.assertEquals(publishTracesCall.getRequest().getNodeId(), nodeId);
            Assert.assertEquals(publishTracesCall.getRequest().getProjectSecret(), projectSecret);
            Assert.assertTrue(publishTracesCall.getRequest().getSpans().size() > 0);
            publishTracesCall.getRequest().getSpans().forEach(span ->
                    Assert.assertTrue(span.getTags().containsAll(periscopeTags)));
            Assert.assertNull(publishTracesCall.getResponseErrorMessage());
            totalSpans += publishTracesCall.getRequest().getSpans().size();
        }
        Assert.assertEquals(totalSpans, 45 * 25 + 1);
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
        LogLeecher errorLogLeecher = new LogLeecher("error");
        serverInstance.addErrorLogLeecher(errorLogLeecher);
        LogLeecher exceptionLogLeecher = new LogLeecher("Exception");
        serverInstance.addErrorLogLeecher(exceptionLogLeecher);

        Path nodeIdFile = getNodeIdFilePath();
        Files.deleteIfExists(nodeIdFile);
        testExtension(Collections.emptyMap(), "periscope.choreo.dev:443", "Config.cloud.toml");

        Assert.assertFalse(errorLogLeecher.isTextFound(), "Unexpected error log found");
        Assert.assertFalse(exceptionLogLeecher.isTextFound(), "Unexpected exception log found");
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

        startTestService(new HashMap<>(), new String[0], true, "Config.disabled.toml");
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
