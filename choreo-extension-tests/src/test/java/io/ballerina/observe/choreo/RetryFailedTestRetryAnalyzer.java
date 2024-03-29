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

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * TestNG retry analyzer for retrying tests upon failure.
 */
public class RetryFailedTestRetryAnalyzer implements IRetryAnalyzer {
    private int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 3;

    @Override
    public boolean retry(ITestResult testResult) {
        if (!testResult.isSuccess()) {
            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++;
                testResult.setStatus(ITestResult.FAILURE);
                return true;
            } else {
                testResult.setStatus(ITestResult.FAILURE);
            }
        } else {
            testResult.setStatus(ITestResult.SUCCESS);
        }
        return false;
    }
}
