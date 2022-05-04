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
import io.ballerina.observe.choreo.recording.PublishAstCall;
import io.ballerina.observe.choreo.recording.PublishMetricsCall;
import io.ballerina.observe.choreo.recording.PublishTracesCall;
import io.ballerina.observe.choreo.recording.RecordedTest;
import io.ballerina.observe.choreo.recording.RegisterCall;
import io.ballerina.observe.choreo.recording.Tag;
import org.ballerinalang.test.context.BServerInstance;
import org.ballerinalang.test.context.BallerinaTestException;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Parent test case for all tests which had started up successfully.
 */
public class SuccessfulStartBaseTestCase extends BaseTestCase {
    private static final List<String> EXPECTED_METRICS_NAMES = Arrays.asList("up", "requests_total",
            "response_time_seconds", "response_time_seconds_mean", "response_time_seconds_max",
            "response_time_seconds_min", "response_time_seconds_stdDev", "response_time_seconds_percentile",
            "inprogress_requests", "response_time_nanoseconds_total", "choreo_steps_total");

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

    protected void validateRecordedTest(RecordedTest recordedTest) throws IOException {
        validateRecordedRegisterCall(recordedTest);
        validateCreatedFiles(recordedTest);
        validateRecordedPublishAstCall(recordedTest);
        validateRecordedPublishTracesCall(recordedTest);
        validateRecordedPublishMetricsCall(recordedTest);
    }

    protected void validateRecordedRegisterCall(RecordedTest recordedTest) {
        // Validate recorded register call
        Assert.assertEquals(recordedTest.getRegisterCalls().size(), 1);
        RegisterCall registerCall = recordedTest.getRegisterCalls().get(0);
        String obsId = registerCall.getResponse().getObsId();
        String obsVersion = registerCall.getResponse().getVersion();
        Assert.assertEquals(registerCall.getResponse().getObsUrl(),
                "http://choreo.dev/obs/" + obsId + "/" + obsVersion);
        Assert.assertNull(registerCall.getResponseErrorMessage());
    }

    protected void validateCreatedFiles(RecordedTest recordedTest) throws IOException {
        RegisterCall registerCall = recordedTest.getRegisterCalls().get(0);
        String obsId = registerCall.getResponse().getObsId();

        // Validate created files
        Assert.assertEquals(getProjectObsIdFromFileSystem(serverInstance.getServerHome()), obsId);
        Assert.assertEquals(registerCall.getRequest().getProjectSecret(), getProjectSecretFromFileSystem(obsId));
    }

    protected void validateRecordedPublishAstCall(RecordedTest recordedTest) {
        RegisterCall registerCall = recordedTest.getRegisterCalls().get(0);
        String projectSecret = registerCall.getRequest().getProjectSecret();
        String obsId = registerCall.getResponse().getObsId();

        // Validate recorded publish AST call
        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), 1);
        PublishAstCall publishAstCall = recordedTest.getPublishAstCalls().get(0);
        Assert.assertEquals(publishAstCall.getRequest().getObsId(), obsId);
        Assert.assertEquals(publishAstCall.getRequest().getProjectSecret(), projectSecret);
        Assert.assertNull(publishAstCall.getResponseErrorMessage());
    }

    protected void validateRecordedPublishTracesCall(RecordedTest recordedTest) {
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 1);
        PublishTracesCall publishTracesCall = recordedTest.getPublishTracesCalls().get(0);
        validateRecordedPublishTracesCall(recordedTest, publishTracesCall);
    }

    protected void validateRecordedPublishTracesCall(RecordedTest recordedTest, PublishTracesCall publishTracesCall) {
        RegisterCall registerCall = recordedTest.getRegisterCalls().get(0);
        String nodeId = registerCall.getRequest().getNodeId();
        String projectSecret = registerCall.getRequest().getProjectSecret();
        String obsId = registerCall.getResponse().getObsId();
        String obsVersion = registerCall.getResponse().getVersion();
        List<Tag> periscopeTags = registerCall.getResponse().getTags();

        // Validate recorded publish traces call IDs
        Assert.assertEquals(publishTracesCall.getRequest().getObservabilityId(), obsId);
        Assert.assertEquals(publishTracesCall.getRequest().getVersion(), obsVersion);
        Assert.assertEquals(publishTracesCall.getRequest().getNodeId(), nodeId);
        Assert.assertEquals(publishTracesCall.getRequest().getProjectSecret(), projectSecret);
        Assert.assertNull(publishTracesCall.getResponseErrorMessage());

        // Validate recorded publish traces call span common information
        List<PublishTracesCall.Request.TraceSpan> traceSpans = publishTracesCall.getRequest().getSpans();
        Assert.assertEquals(traceSpans.size(), 2);
        traceSpans.forEach(span -> Assert.assertTrue(span.getTags().containsAll(periscopeTags)));
        traceSpans.forEach(span -> {
            Assert.assertTrue(span.getTimestamp() > recordedTest.getStartTimestamp());
            Assert.assertTrue(span.getTimestamp() < recordedTest.getEndTimestamp());
            Assert.assertTrue(span.getDuration() > 0);
            Assert.assertEquals(span.getReferences().size(), 1);
            Assert.assertEquals(span.getReferences().get(0).getRefType(),
                    PublishTracesCall.Request.TraceSpan.Reference.ReferenceType.CHILD_OF);
        });

        // Validate published span linking
        PublishTracesCall.Request.TraceSpan rootSpan;
        PublishTracesCall.Request.TraceSpan childSpan;
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
        Assert.assertEquals(childSpan.getTags().size(), periscopeTags.size() + 10);
        Assert.assertEquals(childSpan.getCheckpoints().size(), 0);
    }

    protected void validateRecordedPublishMetricsCall(RecordedTest recordedTest) {
        RegisterCall registerCall = recordedTest.getRegisterCalls().get(0);
        String nodeId = registerCall.getRequest().getNodeId();
        String projectSecret = registerCall.getRequest().getProjectSecret();
        String obsId = registerCall.getResponse().getObsId();
        String obsVersion = registerCall.getResponse().getVersion();
        List<Tag> periscopeTags = registerCall.getResponse().getTags();

        // Validate recorded publish metrics call
        Assert.assertTrue(recordedTest.getPublishMetricsCalls().size() > 0);
        recordedTest.getPublishMetricsCalls().forEach(publishMetricsCall -> {
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
                Assert.assertEquals(publishMetricsCall.getRequest().getMetrics().size(), 76);
                publishMetricsCall.getRequest().getMetrics().forEach(metric -> {
                    Assert.assertTrue(metric.getTags().containsAll(periscopeTags));
                    if ("up".equals(metric.getName())) {
                        Assert.assertEquals(metric.getValue(), 1f);
                        Assert.assertEquals(metric.getTags(), periscopeTags);
                    } else {
                        Assert.assertTrue(metric.getValue() >= 0f,
                                "Metric " + metric.getName() + " has a negative value: " + metric.getValue());
                    }
                    Assert.assertTrue(EXPECTED_METRICS_NAMES.contains(metric.getName()),
                            "Unknown metric: " + metric.getName());
                });
            }
        });
    }
}
