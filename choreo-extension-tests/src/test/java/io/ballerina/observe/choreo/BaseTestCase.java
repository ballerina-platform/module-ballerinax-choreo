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

import com.google.gson.Gson;
import io.ballerina.observe.choreo.client.internal.ClientUtils;
import io.ballerina.observe.choreo.client.internal.secret.AnonymousAppSecretHandler;
import io.ballerina.observe.choreo.recording.PublishAstCall;
import io.ballerina.observe.choreo.recording.PublishMetricsCall;
import io.ballerina.observe.choreo.recording.PublishTracesCall;
import io.ballerina.observe.choreo.recording.RecordedTest;
import io.ballerina.observe.choreo.recording.RegisterCall;
import org.apache.commons.lang3.ArrayUtils;
import org.ballerinalang.test.context.BServerInstance;
import org.ballerinalang.test.context.BalServer;
import org.ballerinalang.test.context.BallerinaTestException;
import org.ballerinalang.test.context.LogLeecher;
import org.ballerinalang.test.context.Utils;
import org.ballerinalang.test.util.HttpClientRequest;
import org.ballerinalang.test.util.HttpResponse;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Parent test class for all extension integration tests cases. This will provide basic
 * functionality for integration tests. This will initialize a single ballerina instance which will be used
 * by all the test cases throughout.
 */
public class BaseTestCase {
    private static final Logger LOGGER = Logger.getLogger(BaseTestCase.class.getName());

    protected static final File RESOURCES_DIR = Paths.get("src", "test", "resources", "bal").toFile();
    protected static final String PERISCOPE_CALLS_SERVICE = "http://localhost:10091";
    protected static final String TEST_RESOURCE_URL = "http://localhost:9091/test/sum";

    protected static final String CHOREO_EXTENSION_LOG_PREFIX =
            "ballerina: initializing connection with observability backend ";
    protected static final String CHOREO_EXTENSION_METRICS_ENABLED_LOG =
            "ballerina: started publishing metrics to Choreo";
    protected static final String CHOREO_EXTENSION_TRACES_ENABLED_LOG =
            "ballerina: started publishing traces to Choreo";

    private BServerInstance periscopeBackendServerInstance;
    protected Path tempFilesDir;
    static BalServer balServer;
    protected BServerInstance serverInstance;

    private boolean isNodeIdBackupRequired = false;
    private static final String NODE_ID_FILE_NAME = "nodeId";
    private static final String NODE_ID_BACKUP_FILE = "nodeId.back";

    @BeforeSuite(alwaysRun = true)
    public void initializeSuite() throws BallerinaTestException, IOException {
        balServer = new BalServer();

        tempFilesDir = Files.createTempDirectory("choreo-test-temp-files-");

        Path nodeIdFile = getNodeIdFilePath();
        isNodeIdBackupRequired = Files.exists(nodeIdFile);
        if (isNodeIdBackupRequired) {
            // Backing up existing node ID files
            Path storedNodeIdFile = tempFilesDir.resolve(NODE_ID_BACKUP_FILE);
            Files.copy(nodeIdFile, storedNodeIdFile);
            Files.deleteIfExists(nodeIdFile);
        }

        // Cleaning up Dependencies.toml to avoid dependency issues in dependency updates
        final String projectDir = Paths.get(RESOURCES_DIR.getAbsolutePath(), "choreo_periscope_backend").toFile()
                .getAbsolutePath();
        Files.deleteIfExists(Paths.get(projectDir, "Dependencies.toml"));

        periscopeBackendServerInstance = new BServerInstance(balServer);
        int[] requiredPorts = {10090};
        periscopeBackendServerInstance.startServer(projectDir, "choreo_periscope_backend", new String[]{"--offline"},
                null, requiredPorts);
        Utils.waitForPortsToOpen(requiredPorts, 1000 * 60, false, "localhost");
    }

    @AfterSuite(alwaysRun = true)
    public void cleanupSuite() throws IOException, BallerinaTestException {
        periscopeBackendServerInstance.shutdownServer();

        if (isNodeIdBackupRequired) {
            // Restoring backed up node ID file
            Path nodeIdFile = getNodeIdFilePath();
            Path storedNodeIdFile = tempFilesDir.resolve(NODE_ID_BACKUP_FILE);
            Files.deleteIfExists(nodeIdFile);
            Files.copy(storedNodeIdFile, nodeIdFile);
        }

        Files.walk(tempFilesDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        Files.deleteIfExists(tempFilesDir);

        Path ballerinaInternalLog = Paths.get(balServer.getServerHome(), "ballerina-internal.log");
        if (Files.exists(ballerinaInternalLog)) {
            LOGGER.severe("=== Ballerina Internal Log Start ===");
            Files.lines(ballerinaInternalLog).forEach(LOGGER::severe);
            LOGGER.severe("=== Ballerina Internal Log End ===");
        }
        balServer.cleanup();
    }

    @BeforeMethod
    public void initializeTestInBaseTestCase() throws IOException {
        List<String> calls = Arrays.asList("Handshake/register", "Handshake/publishAst", "Telemetry/publishMetrics",
                "Telemetry/publishTraces");
        for (String call : calls) {
            HttpResponse response = HttpClientRequest.doPost(PERISCOPE_CALLS_SERVICE + "/" + call + "/calls", "[]",
                    Collections.singletonMap("Content-Type", "application/json"));
            Assert.assertEquals(response.getResponseCode(), 200);
        }
    }

    protected Path getNodeIdFilePath() {
        return ClientUtils.getGlobalChoreoConfigDir().resolve(NODE_ID_FILE_NAME);
    }

    protected String getNodeIdFromFileSystem() throws IOException {
        return Files.readString(ClientUtils.getGlobalChoreoConfigDir().resolve(NODE_ID_FILE_NAME));
    }

    protected String getProjectObsIdFromFileSystem(String workingDir) throws IOException {
        Path projectFile = Paths.get(workingDir, AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Properties projectProperties = new Properties();
        try (InputStream inputStream = new FileInputStream(projectFile.toFile())) {
            projectProperties.load(inputStream);
            return (String) projectProperties.get(AnonymousAppSecretHandler.PROJECT_OBSERVABILITY_ID_CONFIG_KEY);
        }
    }

    protected String getProjectSecretFromFileSystem(String obsId) throws IOException {
        Path projectSecretPath = ClientUtils.getGlobalChoreoConfigDir()
                .resolve(obsId)
                .resolve(AnonymousAppSecretHandler.PROJECT_SECRET_FILE_NAME);
        return Files.readString(projectSecretPath).trim();
    }

    protected void setProjectObsIdIntoFileSystem(String workingDir, String obsId) throws IOException {
        Path projectFile = Paths.get(workingDir, AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Files.deleteIfExists(projectFile);
        Properties projectProperties = new Properties();
        projectProperties.setProperty(AnonymousAppSecretHandler.PROJECT_OBSERVABILITY_ID_CONFIG_KEY, obsId);
        try (OutputStream outputStream = new FileOutputStream(projectFile.toFile())) {
            projectProperties.store(outputStream, "Created for integration tests");
        }
    }

    protected void setProjectSecretIntoFileSystem(String obsId, String projectSecret) throws IOException {
        removeProjectDirFromUserHome(obsId);
        Path projectSecretParentPath = ClientUtils.getGlobalChoreoConfigDir().resolve(obsId);
        if (projectSecretParentPath.toFile().mkdirs()) {
            LOGGER.info("Created project dir in home: " + projectSecretParentPath);
        }
        Path projectSecretPath = projectSecretParentPath.resolve(AnonymousAppSecretHandler.PROJECT_SECRET_FILE_NAME);
        Files.createFile(projectSecretPath);
        Files.writeString(projectSecretPath, projectSecret, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
    }

    protected void removeProjectDirFromUserHome(String obsId) throws IOException {
        Path projectSecretParentPath = ClientUtils.getGlobalChoreoConfigDir().resolve(obsId);
        if (projectSecretParentPath.toFile().exists()) {
            Files.walk(projectSecretParentPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            Files.deleteIfExists(projectSecretParentPath);
        }
    }

    protected RecordedTest testExtensionWithLocalPeriscope(Map<String, String> additionalEnvVars) throws Exception {
        RecordedTest recordedTest = new RecordedTest();
        recordedTest.recordStart();
        testExtension(additionalEnvVars, "localhost:10090", "Config.toml");
        recordedTest.recordEnd();
        populateWithRecordedCalls(recordedTest);
        return recordedTest;
    }

    protected void populateWithRecordedCalls(RecordedTest recordedTest) throws IOException {
        recordedTest.setRegisterCalls(getRegisterCalls());
        recordedTest.setPublishAstCalls(getPublishAstCalls());
        recordedTest.setPublishMetricsCalls(getPublishMetricsCalls());
        recordedTest.setPublishTracesCalls(getPublishTracesCalls());
    }

    protected void testExtension(Map<String, String> additionalEnvVars, String reporterHost,
                                 String configFileName) throws Exception {
        LogLeecher choreoExtLogLeecher = new LogLeecher(CHOREO_EXTENSION_LOG_PREFIX + reporterHost);
        serverInstance.addLogLeecher(choreoExtLogLeecher);
        LogLeecher choreoExtMetricsEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_METRICS_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtMetricsEnabledLogLeecher);
        LogLeecher choreoExtTracesEnabledLogLeecher = new LogLeecher(CHOREO_EXTENSION_TRACES_ENABLED_LOG);
        serverInstance.addLogLeecher(choreoExtTracesEnabledLogLeecher);

        startTestService(additionalEnvVars, new String[0], true, configFileName);
        choreoExtLogLeecher.waitForText(10000);
        choreoExtMetricsEnabledLogLeecher.waitForText(1000);

        // Send requests to generate metrics & traces
        String responseData = HttpClientRequest.doGet(TEST_RESOURCE_URL).getData();
        Assert.assertEquals(responseData, "Sum: 53");
        Thread.sleep(11000);    // Data is published every 10 seconds

        // The tracing log is printed on first start of a span
        choreoExtTracesEnabledLogLeecher.waitForText(1000);

        // Validating generated project files
        Path projectFile = Paths.get(serverInstance.getServerHome(), AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Assert.assertTrue(Files.exists(projectFile), "Choreo Project file not generated");
    }

    protected void startTestService(Map<String, String> additionalEnvVars, String[] additionalBuildArgs,
                                    boolean expectSuccessfulStart, String configFileName)
            throws BallerinaTestException, IOException {
        // Cleaning up Dependencies.toml to avoid dependency issues in dependency updates
        final String projectDir = Paths.get(RESOURCES_DIR.getAbsolutePath(), "choreo_ext_test").toFile()
                .getAbsolutePath();
        Files.deleteIfExists(Paths.get(projectDir, "Dependencies.toml"));

        String configFile = Paths.get("src", "test", "resources", "bal", "choreo_ext_test", configFileName)
                .toFile().getAbsolutePath();
        Map<String, String> env = new HashMap<>(additionalEnvVars);
        env.put("BAL_CONFIG_FILES", configFile);

        String[] buildArgs = ArrayUtils.addAll(additionalBuildArgs, "--offline");
        int[] requiredPorts = expectSuccessfulStart ? new int[]{9091} : new int[0];
        serverInstance.startServer(projectDir, "choreo_ext_test", buildArgs, null, env,
                requiredPorts);
        if (expectSuccessfulStart) {
            Utils.waitForPortsToOpen(requiredPorts, 1000 * 60, false, "localhost");
        }
    }

    protected List<RegisterCall> getRegisterCalls() throws IOException {
        HttpResponse response = HttpClientRequest.doGet(PERISCOPE_CALLS_SERVICE + "/Handshake/register/calls");
        return new Gson().fromJson(response.getData(), new RegisterCall.CallsTypeToken().getType());
    }

    protected List<PublishAstCall> getPublishAstCalls() throws IOException {
        HttpResponse response = HttpClientRequest.doGet(PERISCOPE_CALLS_SERVICE + "/Handshake/publishAst/calls");
        return new Gson().fromJson(response.getData(), new PublishAstCall.CallsTypeToken().getType());
    }

    protected List<PublishTracesCall> getPublishTracesCalls() throws IOException {
        HttpResponse response = HttpClientRequest.doGet(PERISCOPE_CALLS_SERVICE + "/Telemetry/publishTraces/calls");
        return new Gson().fromJson(response.getData(), new PublishTracesCall.CallsTypeToken().getType());
    }

    protected List<PublishMetricsCall> getPublishMetricsCalls() throws IOException {
        HttpResponse response = HttpClientRequest.doGet(PERISCOPE_CALLS_SERVICE + "/Telemetry/publishMetrics/calls");
        return new Gson().fromJson(response.getData(), new PublishMetricsCall.CallsTypeToken().getType());
    }
}
