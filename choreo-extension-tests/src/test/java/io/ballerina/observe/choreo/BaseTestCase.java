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

import io.ballerina.observe.choreo.client.ChoreoConfigHelper;
import org.ballerinalang.test.context.BalServer;
import org.ballerinalang.test.context.BallerinaTestException;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.logging.Logger;

/**
 * Parent test class for all extension integration tests cases. This will provide basic
 * functionality for integration tests. This will initialize a single ballerina instance which will be used
 * by all the test cases throughout.
 */
public class BaseTestCase {
    private static final Logger LOGGER = Logger.getLogger(BaseTestCase.class.getName());
    static final File RESOURCES_DIR = Paths.get("src", "test", "resources", "bal").toFile();

//    private BServerInstance periscopeBackendServerInstance;
    protected Path tempFilesDir;
    static BalServer balServer;

    private boolean isNodeIdBackupRequired = false;
    private static final String NODE_ID_FILE_NAME = "nodeId";
    private static final String NODE_ID_BACKUP_FILE = "nodeId.back";

    @BeforeSuite(alwaysRun = true)
    public void initialize() throws BallerinaTestException, IOException {
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

        // TODO: Uncomment once the mock backend based tests are implemented
//        periscopeBackendServerInstance = new BServerInstance(balServer);
//        final String projectDir = Paths.get(RESOURCES_DIR.getAbsolutePath(), "choreo_periscope_backend").toFile()
//                .getAbsolutePath();
//        int[] requiredPorts = {10090};
//        periscopeBackendServerInstance.startServer(projectDir, "choreo_periscope_backend", null, null,
//                requiredPorts);
//        Utils.waitForPortsToOpen(requiredPorts, 1000 * 60, false, "localhost");
    }

    @AfterSuite(alwaysRun = true)
    public void destroy() throws IOException, BallerinaTestException {
//        periscopeBackendServerInstance.shutdownServer();

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

    protected Path getNodeIdFilePath() {
        return ChoreoConfigHelper.getGlobalChoreoConfigDir().resolve(NODE_ID_FILE_NAME);
    }
}
