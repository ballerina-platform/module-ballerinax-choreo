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
import io.ballerina.observe.choreo.recording.PublishAstCall;
import io.ballerina.observe.choreo.recording.PublishMetricsCall;
import io.ballerina.observe.choreo.recording.PublishTracesCall;
import io.ballerina.observe.choreo.recording.PublishTracesCall.Request.TraceSpan;
import io.ballerina.observe.choreo.recording.PublishTracesCall.Request.TraceSpan.Reference.ReferenceType;
import io.ballerina.observe.choreo.recording.RecordedTest;
import io.ballerina.observe.choreo.recording.RegisterCall;
import io.ballerina.observe.choreo.recording.Tag;
import org.ballerinalang.test.context.BServerInstance;
import org.ballerinalang.test.context.BallerinaTestException;
import org.ballerinalang.test.context.LogLeecher;
import org.ballerinalang.test.util.HttpClientRequest;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

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
public class GeneralExtensionTestCase extends BaseTestCase {
    private static final String RESTART_TEST_PROJECT_FILE = "restart_test_project_file";

    @BeforeMethod
    public void initializeTest() throws IOException, BallerinaTestException {
        if (serverInstance != null) {
            Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
            Files.deleteIfExists(projectFile);
        }
        serverInstance = new BServerInstance(balServer);
    }

    @AfterMethod
    public void cleanUpTest() throws Exception {
        serverInstance.shutdownServer();
    }

    @Test
    public void testPublishDataToChoreo() throws Exception {
        Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Path storedProjectFile = tempFilesDir.resolve(RESTART_TEST_PROJECT_FILE);
        Files.deleteIfExists(projectFile);  // To test fresh project

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedTest(recordedTest, true);
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
        validateRecordedTest(recordedTest, false);
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
        validateRecordedTest(recordedTest, true);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeIdFromFileSystem());
    }

    @Test
    public void testProvidedNodeId() throws Exception {
        String providedNodeId = "ext-test-node-id-1";
        Path nodeIdFile = getNodeIdFilePath();
        Files.writeString(nodeIdFile, providedNodeId);

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedTest(recordedTest, true);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), providedNodeId);
    }

    @Test
    public void testProvidedNodeIdEnvVar() throws Exception {
        String providedNodeId = "ext-test-node-id-2";
        Map<String, String> envVars = new HashMap<>();
        envVars.put("CHOREO_EXT_NODE_ID", providedNodeId);

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(envVars);
        validateRecordedTest(recordedTest, true);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), providedNodeId);
    }

    @Test
    public void testMissingNodeId() throws Exception {
        Path nodeIdFile = getNodeIdFilePath();
        Files.deleteIfExists(nodeIdFile);

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedTest(recordedTest, true);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeIdFromFileSystem());
    }

    @Test(retryAnalyzer = RetryFailedTestRetryAnalyzer.class)
    public void testExtensionWithChoreoCloud() throws Exception {
        Path nodeIdFile = getNodeIdFilePath();
        Files.deleteIfExists(nodeIdFile);
        testExtension(Collections.emptyMap(), "periscope.choreo.dev:443", "Config.cloud.toml");
    }

    private void validateRecordedTest(RecordedTest recordedTest, boolean expectPublishAst) throws IOException {
        // Validate recorded register call
        Assert.assertEquals(recordedTest.getRegisterCalls().size(), 1);
        RegisterCall registerCall = recordedTest.getRegisterCalls().get(0);
        String nodeId = registerCall.getRequest().getNodeId();
        String projectSecret = registerCall.getRequest().getProjectSecret();
        String obsId = registerCall.getResponse().getObsId();
        String obsVersion = registerCall.getResponse().getVersion();
        List<Tag> periscopeTags = registerCall.getResponse().getTags();
        Assert.assertEquals(registerCall.getResponse().getObsUrl(),
                "http://choreo.dev/obs/" + obsId + "/" + obsVersion);
        Assert.assertNull(registerCall.getResponseErrorMessage());

        // Validate saved files
        Assert.assertEquals(getProjectObsIdFromFileSystem(serverInstance.getServerHome()), obsId);
        Assert.assertEquals(registerCall.getRequest().getProjectSecret(), getProjectSecretFromFileSystem(obsId));

        // Validate recorded publish AST call
        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), expectPublishAst ? 1 : 0);
        if (expectPublishAst) {
            PublishAstCall publishAstCall = recordedTest.getPublishAstCalls().get(0);
            Assert.assertEquals(publishAstCall.getRequest().getObsId(), obsId);
            Assert.assertEquals(publishAstCall.getRequest().getProjectSecret(), projectSecret);
            Assert.assertNull(publishAstCall.getResponseErrorMessage());
        }

        // Validate recorded publish traces call IDs
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 1);
        PublishTracesCall publishTracesCall = recordedTest.getPublishTracesCalls().get(0);
        Assert.assertEquals(publishTracesCall.getRequest().getObservabilityId(), obsId);
        Assert.assertEquals(publishTracesCall.getRequest().getVersion(), obsVersion);
        Assert.assertEquals(publishTracesCall.getRequest().getNodeId(), nodeId);
        Assert.assertEquals(publishTracesCall.getRequest().getProjectSecret(), projectSecret);
        Assert.assertNull(publishTracesCall.getResponseErrorMessage());

        // Validate recorded publish traces call span common information
        List<TraceSpan> traceSpans = publishTracesCall.getRequest().getSpans();
        Assert.assertEquals(traceSpans.size(), 2);
        traceSpans.forEach(span -> periscopeTags.forEach(
                tag -> Assert.assertEquals(findTag(span.getTags(), tag).size(), 1)));
        traceSpans.forEach(span -> {
            Assert.assertTrue(span.getTimestamp() > recordedTest.getStartTimestamp());
            Assert.assertTrue(span.getTimestamp() < recordedTest.getEndTimestamp());
            Assert.assertTrue(span.getDuration() > 0);
            Assert.assertEquals(span.getReferences().size(), 1);
            Assert.assertEquals(span.getReferences().get(0).getRefType(), ReferenceType.CHILD_OF);
        });

        // Validate published span linking
        TraceSpan rootSpan;
        TraceSpan childSpan;
        if ("00000000000000000000000000000000".equals(traceSpans.get(0).getReferences().get(0).getTraceId())) {
            rootSpan = traceSpans.get(0);
            childSpan = traceSpans.get(1);
        } else {
            rootSpan = traceSpans.get(1);
            childSpan = traceSpans.get(0);
        }
        Assert.assertEquals(rootSpan.getReferences().get(0).getSpanId(), "0000000000000000");
        Assert.assertEquals(rootSpan.getTraceId(), childSpan.getTraceId());
        Assert.assertEquals(rootSpan.getTraceId(), childSpan.getReferences().get(0).getTraceId());
        Assert.assertEquals(rootSpan.getSpanId(), childSpan.getReferences().get(0).getSpanId());

        // Validate published root span
        Assert.assertEquals(rootSpan.getServiceName(), "/test");
        Assert.assertEquals(rootSpan.getOperationName(), "get /sum");
        Assert.assertEquals(rootSpan.getTags().size(), periscopeTags.size() + 16);
        Assert.assertEquals(rootSpan.getCheckpoints().size(), 6);

        // Validate published child span
        Assert.assertEquals(childSpan.getServiceName(), "/test");
        Assert.assertEquals(childSpan.getOperationName(), "ballerina_test/choreo_ext_test/ObservableAdder:getSum");
        Assert.assertEquals(childSpan.getTags().size(), periscopeTags.size() + 9);
        Assert.assertEquals(childSpan.getCheckpoints().size(), 0);

        // Validate recorded publish metrics call
        Assert.assertTrue(recordedTest.getPublishMetricsCalls().size() > 0);
        recordedTest.getPublishMetricsCalls().forEach(publishMetricsCall -> {
            Assert.assertEquals(publishMetricsCall.getRequest().getObservabilityId(), obsId);
            Assert.assertEquals(publishMetricsCall.getRequest().getVersion(), obsVersion);
            Assert.assertEquals(publishMetricsCall.getRequest().getNodeId(), nodeId);
            Assert.assertEquals(publishMetricsCall.getRequest().getProjectSecret(), projectSecret);
            publishMetricsCall.getRequest().getMetrics().forEach(metric -> periscopeTags.forEach(
                    tag -> Assert.assertEquals(findTag(metric.getTags(), tag).size(), 1)));
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
                Assert.assertEquals(publishMetricsCall.getRequest().getMetrics().size(), 75);
            }
        });
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
