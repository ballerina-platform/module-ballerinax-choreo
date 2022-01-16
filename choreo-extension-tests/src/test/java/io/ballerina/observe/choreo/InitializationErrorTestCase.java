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
import io.ballerina.observe.choreo.recording.RecordedTest;
import org.ballerinalang.test.context.BServerInstance;
import org.ballerinalang.test.context.BallerinaTestException;
import org.ballerinalang.test.context.LogLeecher;
import org.ballerinalang.test.context.Utils;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests covering initialization errors for Choreo extension.
 */
public class InitializationErrorTestCase extends BaseTestCase {
    private static final String CHOREO_CLIENT_NOT_INITIALIZED_ERROR =
            "error: Choreo client is not initialized. Please check Config.toml";

    @BeforeMethod
    public void initializeTest() throws IOException, BallerinaTestException {
        if (serverInstance != null) {
            Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
            Files.deleteIfExists(projectFile);
        }
        serverInstance = new BServerInstance(balServer);
    }

    @Test
    public void testUnavailablePeriscope() throws BallerinaTestException, IOException {
        LogLeecher choreoExtLogLeecher = new LogLeecher(CHOREO_EXTENSION_LOG_PREFIX + "localhost:20090");
        serverInstance.addLogLeecher(choreoExtLogLeecher);
        LogLeecher clientNotInitializedErrorLogLeecher = new LogLeecher(CHOREO_CLIENT_NOT_INITIALIZED_ERROR);
        serverInstance.addErrorLogLeecher(clientNotInitializedErrorLogLeecher);
        LogLeecher servicesUnavailableErrorLogLeecher = new LogLeecher(
                "cause: UNAVAILABLE: Choreo services are not accessible.");
        serverInstance.addErrorLogLeecher(servicesUnavailableErrorLogLeecher);

        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();

        testExtension("Config.invalidperiscope.toml");
        choreoExtLogLeecher.waitForText(10000);
        clientNotInitializedErrorLogLeecher.waitForText(5000);
        servicesUnavailableErrorLogLeecher.waitForText(2000);
        Utils.checkPortsAvailability(new int[]{9091});

        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        Assert.assertEquals(recordedTest.getRegisterCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishMetricsCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 0);
    }

    @Test
    public void testRegisterCallError() throws Exception {
        String obsId = "xxxxxxxxxxxxxxxxxxxxx-register-error-1";
        String projectSecret = "xxxxxxxxxxxxxxx-abort-register-error";
        LogLeecher choreoExtLogLeecher = new LogLeecher(CHOREO_EXTENSION_LOG_PREFIX + "localhost:10090");
        serverInstance.addLogLeecher(choreoExtLogLeecher);
        LogLeecher periscopeErrorLogLeecher = new LogLeecher(
                "ABORTED: test error for register using project secret " + projectSecret);
        serverInstance.addErrorLogLeecher(periscopeErrorLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        setProjectSecretIntoFileSystem(obsId, projectSecret);
        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();

        testExtension("Config.toml");
        choreoExtLogLeecher.waitForText(10000);
        periscopeErrorLogLeecher.waitForText(5000);
        Utils.checkPortsAvailability(new int[]{9091});

        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishMetricsCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 0);

        Assert.assertEquals(recordedTest.getRegisterCalls().size(), 1);
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getRequest().getProjectSecret(), projectSecret);
        Assert.assertNull(recordedTest.getRegisterCalls().get(0).getResponse());
        Assert.assertEquals(recordedTest.getRegisterCalls().get(0).getResponseErrorMessage(),
                "error AbortedError (\"test error for register using project secret " + projectSecret + "\")");
    }

    @Test
    public void testMissingProjectSecretFile() throws IOException, BallerinaTestException {
        String obsId = "xxxxxxxxxxxxxxx-missing-secret-obsid";
        LogLeecher clientNotInitializedErrorLogLeecher = new LogLeecher(CHOREO_CLIENT_NOT_INITIALIZED_ERROR);
        serverInstance.addErrorLogLeecher(clientNotInitializedErrorLogLeecher);
        LogLeecher validationErrorLogLeecher = new LogLeecher("cause: VALIDATION_ERROR: ");
        serverInstance.addErrorLogLeecher(validationErrorLogLeecher);
        LogLeecher fileMissingErrorLogLeecher = new LogLeecher(" is missing");
        serverInstance.addErrorLogLeecher(fileMissingErrorLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        removeProjectDirFromUserHome(obsId);
        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();

        testExtension("Config.toml");
        clientNotInitializedErrorLogLeecher.waitForText(10000);
        validationErrorLogLeecher.waitForText(2000);
        fileMissingErrorLogLeecher.waitForText(2000);
        Utils.checkPortsAvailability(new int[]{9091});

        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishMetricsCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 0);
        Assert.assertEquals(recordedTest.getRegisterCalls().size(), 0);
    }

    @Test
    public void testWithEmptyProjectSecret() throws IOException, BallerinaTestException {
        String obsId = "xxxxxxxxxxxxxxxxx-empty-secret-obsid";
        LogLeecher clientNotInitializedErrorLogLeecher = new LogLeecher(CHOREO_CLIENT_NOT_INITIALIZED_ERROR);
        serverInstance.addErrorLogLeecher(clientNotInitializedErrorLogLeecher);
        LogLeecher validationErrorLogLeecher = new LogLeecher("cause: VALIDATION_ERROR: Read project secret is empty");
        serverInstance.addErrorLogLeecher(validationErrorLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        setProjectSecretIntoFileSystem(obsId, "");
        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();

        testExtension("Config.toml");
        clientNotInitializedErrorLogLeecher.waitForText(10000);
        validationErrorLogLeecher.waitForText(2000);
        Utils.checkPortsAvailability(new int[]{9091});

        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishMetricsCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 0);
        Assert.assertEquals(recordedTest.getRegisterCalls().size(), 0);
    }

    @Test
    public void testInvalidProjectSecret() throws IOException, BallerinaTestException {
        String obsId = "xxxxxxxx-invalid-length-secret-obsid";
        String projectSecret = "invalid-length-secret";
        LogLeecher clientNotInitializedErrorLogLeecher = new LogLeecher(CHOREO_CLIENT_NOT_INITIALIZED_ERROR);
        serverInstance.addErrorLogLeecher(clientNotInitializedErrorLogLeecher);
        LogLeecher validationErrorLogLeecher = new LogLeecher("cause: VALIDATION_ERROR: Project secret length(" +
                projectSecret.length() + ") is incorrect");
        serverInstance.addErrorLogLeecher(validationErrorLogLeecher);

        setProjectObsIdIntoFileSystem(serverInstance.getServerHome(), obsId);
        setProjectSecretIntoFileSystem(obsId, projectSecret);
        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();

        testExtension("Config.toml");
        clientNotInitializedErrorLogLeecher.waitForText(10000);
        validationErrorLogLeecher.waitForText(2000);
        Utils.checkPortsAvailability(new int[]{9091});

        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);

        Assert.assertEquals(recordedTest.getPublishAstCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishMetricsCalls().size(), 0);
        Assert.assertEquals(recordedTest.getPublishTracesCalls().size(), 0);
        Assert.assertEquals(recordedTest.getRegisterCalls().size(), 0);
    }

    private void testExtension(String configFileName) throws IOException, BallerinaTestException {
        String configFile = Paths.get("src", "test", "resources", "bal", "choreo_ext_test", configFileName)
                .toFile().getAbsolutePath();
        Map<String, String> env = new HashMap<>();
        env.put("BAL_CONFIG_FILES", configFile);
        env.put("JAVA_OPTS", "-javaagent:" + System.getProperty("choreo.ext.test.agent"));
        startTestService(env, false);
    }
}
