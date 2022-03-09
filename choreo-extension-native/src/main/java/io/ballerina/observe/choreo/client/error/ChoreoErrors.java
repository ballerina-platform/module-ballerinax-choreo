/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.observe.choreo.client.error;

import io.grpc.StatusRuntimeException;

/**
 * Collection of helper methods to create Choreo specific exceptions.
 *
 * @since 2.0.0
 */
public class ChoreoErrors {
    private ChoreoErrors() {    // Prevent initialization
    }

    public static ChoreoClientException getChoreoClientError(StatusRuntimeException e) {
        switch (e.getStatus().getCode()) {
            case UNAVAILABLE:
                return ChoreoErrors.getUnavailableError(e);
            case UNKNOWN:
                return ChoreoErrors.getIncompatibleServiceError(e);
            default:
                return new ChoreoClientException(new ChoreoError(ChoreoError.Code.INTERNAL, e.getMessage(), e));
        }
    }

    public static ChoreoClientException getUnavailableError(Throwable throwable) {
        return new ChoreoClientException(
                new ChoreoError(ChoreoError.Code.UNAVAILABLE, "Choreo services are not accessible.", throwable)
        );
    }

    public static ChoreoClientException getIncompatibleServiceError(Throwable throwable) {
        return new ChoreoClientException(
                new ChoreoError(ChoreoError.Code.UNAVAILABLE, "Choreo backend is not compatible.", throwable)
        );
    }

    public static ChoreoClientException createValidationError(String message) {
        return new ChoreoClientException(new ChoreoError(ChoreoError.Code.VALIDATION_ERROR, message, null));
    }
}
