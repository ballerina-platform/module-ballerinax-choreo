/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import io.ballerina.observe.choreo.recording.PublishTracesCall;
import io.ballerina.observe.choreo.recording.RecordedTest;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Integration tests covering periscope side errors for Choreo extension.
 */
public class PeriscopeErrorTestCase extends SuccessfulStartBaseTestCase {
    private static final Logger LOGGER = Logger.getLogger(PeriscopeErrorTestCase.class.getName());

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
    public void testPublishAstCallFailure() throws Exception {
        String obsId = "xxxxxxxxxxxxxxxxxx-publish-ast-error-1";
        String projectSecret = "xxxxxxxxxxxxxxxxxx-publish-ast-error";
        LogLeecher periscopeErrorLogLeecher = new LogLeecher(
                "ABORTED: test error for publish ast using obs ID " + projectSecret);
        serverInstance.addErrorLogLeecher(periscopeErrorLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        setProjectSecretIntoFileSystem(obsId, projectSecret);
        RecordedTest recordedTest = testExtensionWithLocalPeriscope(new HashMap<>());
        periscopeErrorLogLeecher.waitForText(5000);

        validateRecordedRegisterCall(recordedTest);
        validateRecordedPublishTracesCall(recordedTest);
        validateRecordedPublishMetricsCall(recordedTest);

        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), 1);
        Assert.assertEquals(recordedTest.getPublishAstCalls().get(0).getRequest().getProjectSecret(), projectSecret);
        Assert.assertEquals(recordedTest.getPublishAstCalls().get(0).getResponseErrorMessage(),
                "error AbortedError (\"test error for publish ast using obs ID " + obsId + "\")");
    }

    @Test
    public void testPublishMetricsCallFailure() throws Exception {
        String obsId = "xxxxxxxxxxxxxx-publish-metrics-error-3";
        String projectSecret = "xxxxxxxxxxxxxx-publish-metrics-error";
        LogLeecher periscopeErrorLogLeecher = new LogLeecher(
                "ABORTED: test error for publish metrics using obs ID " + projectSecret);
        serverInstance.addErrorLogLeecher(periscopeErrorLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        setProjectSecretIntoFileSystem(obsId, projectSecret);
        RecordedTest recordedTest = testExtensionWithLocalPeriscope(new HashMap<>());
        periscopeErrorLogLeecher.waitForText(5000);

        validateRecordedRegisterCall(recordedTest);
        validateRecordedPublishAstCall(recordedTest);
        validateRecordedPublishTracesCall(recordedTest);

        Assert.assertTrue(recordedTest.getPublishMetricsCalls().size() > 0);
        recordedTest.getPublishMetricsCalls().forEach(publishMetricsCall -> {
            Assert.assertEquals(publishMetricsCall.getRequest().getProjectSecret(), projectSecret);
            Assert.assertEquals(publishMetricsCall.getResponseErrorMessage(),
                    "error AbortedError (\"test error for publish metrics using obs ID " + obsId + "\")");
        });
    }

    @Test
    public void testPublishTracesCallFailure() throws Exception {
        String obsId = "xxxxxxxxxxxxxxx-publish-traces-error-5";
        String projectSecret = "xxxxxxxxxxxxxxx-publish-traces-error";
        LogLeecher periscopeErrorLogLeecher = new LogLeecher(
                "ABORTED: test error for publish traces using obs ID " + projectSecret);
        serverInstance.addErrorLogLeecher(periscopeErrorLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        setProjectSecretIntoFileSystem(obsId, projectSecret);
        RecordedTest recordedTest = testExtensionWithLocalPeriscope(new HashMap<>());
        periscopeErrorLogLeecher.waitForText(5000);

        validateRecordedRegisterCall(recordedTest);
        validateRecordedPublishAstCall(recordedTest);
        validateRecordedPublishMetricsCall(recordedTest);

        Assert.assertTrue(recordedTest.getPublishTracesCalls().size() > 0);
        recordedTest.getPublishTracesCalls().forEach(publishTracesCall -> {
            Assert.assertEquals(publishTracesCall.getRequest().getProjectSecret(), projectSecret);
            Assert.assertEquals(publishTracesCall.getResponseErrorMessage(),
                    "error AbortedError (\"test error for publish traces using obs ID " + obsId + "\")");
        });
    }

    @Test
    public void testPublishTracesCallFailureRetry() throws Exception {
        String obsId = "xx-publish-traces-error-buffer-clean-9";
        String projectSecret = "xx-publish-traces-error-buffer-clean";
        LogLeecher periscopeErrorLogLeecher = new LogLeecher(
                "ABORTED: test error for retry for publish traces using obs ID " + projectSecret);
        serverInstance.addErrorLogLeecher(periscopeErrorLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        setProjectSecretIntoFileSystem(obsId, projectSecret);

        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();
        testExtension(new HashMap<>(), "localhost:10090", "Config.toml");
        periscopeErrorLogLeecher.waitForText(5000);
        LOGGER.info("Waiting for extension to retry traces publish failure");
        Thread.sleep(23000);    // Wait for the second retry in 10 seconds
        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        validateRecordedRegisterCall(recordedTest);
        validateRecordedPublishAstCall(recordedTest);
        validateRecordedPublishMetricsCall(recordedTest);

        // Validating first call failure
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 3);
        for (int i = 0; i < recordedTest.getPublishTracesCalls().size(); i++) {
            PublishTracesCall publishTracesCall = recordedTest.getPublishTracesCalls().get(i);
            if (i == 2) {
                // Validating second call (retry of first) success
                validateRecordedPublishTracesCall(recordedTest, publishTracesCall);
            } else {
                Assert.assertEquals(publishTracesCall.getRequest().getProjectSecret(), projectSecret);
                Assert.assertEquals(publishTracesCall.getResponseErrorMessage(),
                        "error AbortedError (\"test error for retry for publish traces using obs ID " + obsId + "\")");
            }
        }
    }

    @Test
    public void testExtensionDataPublishFailureBufferCleanup() throws Exception {
        String obsId = "xxxxxxxxx-publish-traces-error-retry-7";
        String projectSecret = "xxxxxxxxx-publish-traces-error-retry";

        LogLeecher choreoExtLogLeecher = new LogLeecher(CHOREO_EXTENSION_LOG_PREFIX + "localhost:10090");
        serverInstance.addLogLeecher(choreoExtLogLeecher);
        LogLeecher choreoExtensionConnectedLogLeecher = new LogLeecher(CHOREO_EXTENSION_CONNECTED_LOG_PREFIX);
        serverInstance.addLogLeecher(choreoExtensionConnectedLogLeecher);
        LogLeecher choreoExtMetricsEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_METRICS_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtMetricsEnabledLogLeecher);
        LogLeecher choreoExtTracesEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_TRACES_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtTracesEnabledLogLeecher);
        LogLeecher periscopeErrorLogLeecher = new LogLeecher(
                "ABORTED: test error for retry for publish traces using obs ID " + projectSecret);
        serverInstance.addErrorLogLeecher(periscopeErrorLogLeecher);
        LogLeecher spansDroppingLog = new LogLeecher("ballerina: span buffer is full : dropped 5002 spans");
        serverInstance.addLogLeecher(spansDroppingLog);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        setProjectSecretIntoFileSystem(obsId, projectSecret);

        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();

        startTestService(Collections.emptyMap(), new String[0], true, "Config.toml");
        choreoExtLogLeecher.waitForText(10000);
        choreoExtensionConnectedLogLeecher.waitForText(10000);
        choreoExtMetricsEnabledLogLeecher.waitForText(1000);

        // Send requests to generate traces
        int traceCount = 20;
        for (int i = 0; i < traceCount; i++) {
            String responseData = HttpClientRequest.doGet("http://localhost:9091/test/loopWithLargeSpanCount")
                    .getData();
            Assert.assertEquals(responseData, "Sum: 135000");
            Thread.sleep(500); // To Avoid sampler dropping spans
        }

        choreoExtTracesEnabledLogLeecher.waitForText(1000); // Tracing log is printed on first start of a span
        LOGGER.info("Waiting for extension to retry traces publish failure");
        Thread.sleep(23000);    // Wait for retry in 10 seconds
        periscopeErrorLogLeecher.waitForText(5000);
        spansDroppingLog.waitForText(2000);

        // Validating generated project files
        Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Assert.assertTrue(Files.exists(projectFile), "Choreo Project file not generated");

        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        validateRecordedRegisterCall(recordedTest);
        validateRecordedPublishAstCall(recordedTest);
        validateRecordedPublishMetricsCall(recordedTest);

        // Validating first call failure
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 8);
        int totalPublishedSpans = 0;
        Map<String, List<PublishTracesCall.Request.TraceSpan>> traceIdToSpansMap = new HashMap<>();
        for (int i = 0; i < recordedTest.getPublishTracesCalls().size(); i++) {
            PublishTracesCall publishTracesCall = recordedTest.getPublishTracesCalls().get(i);
            if (i >= 2) {
                totalPublishedSpans += publishTracesCall.getRequest().getSpans().size();
                publishTracesCall.getRequest().getSpans().forEach(traceSpan -> {
                    List<PublishTracesCall.Request.TraceSpan> traceSpans =
                            traceIdToSpansMap.computeIfAbsent(traceSpan.getTraceId(), s -> new ArrayList<>());
                    traceSpans.add(traceSpan);
                });
            } else {
                Assert.assertEquals(publishTracesCall.getRequest().getProjectSecret(), projectSecret);
                Assert.assertEquals(publishTracesCall.getResponseErrorMessage(),
                        "error AbortedError (\"test error for retry for publish traces using obs ID " + obsId + "\")");
            }
        }
        Assert.assertEquals(traceIdToSpansMap.size(), 18);
        Assert.assertEquals(totalPublishedSpans, 18 * 2501);
    }
}
