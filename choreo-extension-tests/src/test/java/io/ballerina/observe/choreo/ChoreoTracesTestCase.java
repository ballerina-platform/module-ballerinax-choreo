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
import org.ballerinalang.test.context.LogLeecher;
import org.ballerinalang.test.context.Utils;
import org.ballerinalang.test.util.HttpClientRequest;
import org.testng.Assert;
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
 * Integration test for Choreo extension.
 */
public class ChoreoTracesTestCase extends BaseTestCase {
    private static final String TEST_RESOURCE_URL = "http://localhost:9091/test/sum";
    private static final String RESTART_TEST_PROJECT_FILE = "restart_test_project_file";

    private static final String CHOREO_EXTENSION_LOG_PREFIX =
            "ballerina: initializing connection with observability backend ";
    private static final String CHOREO_EXTENSION_METRICS_ENABLED_LOG =
            "ballerina: started publishing metrics to Choreo";
    private static final String CHOREO_EXTENSION_TRACES_ENABLED_LOG = "ballerina: started publishing traces to Choreo";
    private static final String CHOREO_EXTENSION_URL_LOG_PREFIX = "ballerina: visit ";

    @Test
    public void testPublishDataToChoreo() throws Exception {
        Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Path storedProjectFile = tempFilesDir.resolve(RESTART_TEST_PROJECT_FILE);
        Files.deleteIfExists(projectFile);  // To test fresh project

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedTest(recordedTest, true);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeId());

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
        String previousObsId = getProjectObsId(serverInstance.getServerHome());
        String previousNodeId = getNodeId();

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(Collections.emptyMap());
        validateRecordedTest(recordedTest, false);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeId());

        // Validate final choreo project file with previous test choreo project file
        Assert.assertEquals(Files.readString(projectFile), Files.readString(storedProjectFile),
                "Unexpected changes in Choreo Project file");
        Assert.assertEquals(getProjectObsId(serverInstance.getServerHome()), previousObsId);
        Assert.assertEquals(getNodeId(), previousNodeId);
    }

    @Test
    public void testDebugLogsEnabled() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("CHOREO_EXT_LOG_LEVEL", "DEBUG");

        RecordedTest recordedTest = testExtensionWithLocalPeriscope(envVars);
        validateRecordedTest(recordedTest, true);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeId());
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
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getNodeId(), getNodeId());
    }

    @Test(retryAnalyzer = RetryFailedTestRetryAnalyzer.class)
    public void testExtensionWithChoreoCloud() throws Exception {
        Path nodeIdFile = getNodeIdFilePath();
        Files.deleteIfExists(nodeIdFile);
        testExtension(Collections.emptyMap(), "periscope.choreo.dev:443", "Config.cloud.toml");
    }

    private RecordedTest testExtensionWithLocalPeriscope(Map<String, String> additionalEnvVars) throws Exception {
        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();
        testExtension(additionalEnvVars, "localhost:10090", "Config.toml");
        recordedTest.recordEnd();

        recordedTest.setRegisterCalls(getRegisterCalls());
        recordedTest.setPublishAstCalls(getPublishAstCalls());
        recordedTest.setPublishMetricsCalls(getPublishMetricsCalls());
        recordedTest.setPublishTracesCalls(getPublishTracesCalls());
        return recordedTest;
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

        // Validate saved files
        Assert.assertEquals(getProjectObsId(serverInstance.getServerHome()), obsId);
        Assert.assertEquals(registerCall.getRequest().getProjectSecret(), getProjectSecret(obsId));

        // Validate recorded publish AST call
        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), expectPublishAst ? 1 : 0);
        if (expectPublishAst) {
            PublishAstCall publishAstCall = recordedTest.getPublishAstCalls().get(0);
            Assert.assertEquals(publishAstCall.getRequest().getObsId(), obsId);
            Assert.assertEquals(publishAstCall.getRequest().getProjectSecret(), projectSecret);
        }

        // Validate recorded publish traces call IDs
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 1);
        PublishTracesCall publishTracesCall = recordedTest.getPublishTracesCalls().get(0);
        Assert.assertEquals(publishTracesCall.getRequest().getObservabilityId(), obsId);
        Assert.assertEquals(publishTracesCall.getRequest().getVersion(), obsVersion);
        Assert.assertEquals(publishTracesCall.getRequest().getNodeId(), nodeId);
        Assert.assertEquals(publishTracesCall.getRequest().getProjectSecret(), projectSecret);

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

    private void testExtension(Map<String, String> additionalEnvVars, String reporterHost,
                                       String configFileName) throws Exception {
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
        Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
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
