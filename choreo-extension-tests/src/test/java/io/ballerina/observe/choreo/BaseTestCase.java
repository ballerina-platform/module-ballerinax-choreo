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
import io.ballerina.observe.choreo.recording.RegisterCall;
import io.ballerina.observe.choreo.recording.Tag;
import org.ballerinalang.test.context.BServerInstance;
import org.ballerinalang.test.context.BalServer;
import org.ballerinalang.test.context.BallerinaTestException;
import org.ballerinalang.test.context.Utils;
import org.ballerinalang.test.util.HttpClientRequest;
import org.ballerinalang.test.util.HttpResponse;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Parent test class for all extension integration tests cases. This will provide basic
 * functionality for integration tests. This will initialize a single ballerina instance which will be used
 * by all the test cases throughout.
 */
public class BaseTestCase {
    private static final Logger LOGGER = Logger.getLogger(BaseTestCase.class.getName());

    static final File RESOURCES_DIR = Paths.get("src", "test", "resources", "bal").toFile();
    private static final String PERISCOPE_CALLS_SERVICE = "http://localhost:10091";

    private BServerInstance periscopeBackendServerInstance;
    protected Path tempFilesDir;
    private static BalServer balServer;
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
    public void initializeTest() throws IOException, BallerinaTestException {
        List<String> calls = Arrays.asList("Handshake/register", "Handshake/publishAst", "Telemetry/publishMetrics",
                "Telemetry/publishTraces");
        for (String call : calls) {
            HttpResponse response = HttpClientRequest.doPost(PERISCOPE_CALLS_SERVICE + "/" + call + "/calls", "[]",
                    Collections.singletonMap("Content-Type", "application/json"));
            Assert.assertEquals(response.getResponseCode(), 200);
        }
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

    protected Path getNodeIdFilePath() {
        return ClientUtils.getGlobalChoreoConfigDir().resolve(NODE_ID_FILE_NAME);
    }

    protected String getNodeId() throws IOException {
        return Files.readString(ClientUtils.getGlobalChoreoConfigDir().resolve(NODE_ID_FILE_NAME));
    }

    protected String getProjectObsId(String cwd) throws IOException {
        Path projectFile = Paths.get(cwd, AnonymousAppSecretHandler.PROJECT_FILE_NAME);
        Properties projectProperties = new Properties();
        try (InputStream inputStream = new FileInputStream(projectFile.toFile())) {
            projectProperties.load(inputStream);
            return (String) projectProperties.get(AnonymousAppSecretHandler.PROJECT_OBSERVABILITY_ID_CONFIG_KEY);
        }
    }

    protected String getProjectSecret(String obsId) throws IOException {
        return Files.readString(ClientUtils.getGlobalChoreoConfigDir().resolve(obsId)
                .resolve(AnonymousAppSecretHandler.PROJECT_SECRET_FILE_NAME)).trim();
    }

    protected List<Tag> findTag(List<Tag> tagsList, Tag tag) {
        return tagsList.stream()
                .filter(spanTag -> Objects.equals(spanTag, tag))
                .collect(Collectors.toList());
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
