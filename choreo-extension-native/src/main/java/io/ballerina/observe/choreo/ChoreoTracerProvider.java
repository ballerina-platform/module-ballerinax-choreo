/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ballerina.observe.choreo;

import io.ballerina.observe.choreo.logging.LogFactory;
import io.ballerina.observe.choreo.logging.Logger;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.observability.tracer.spi.TracerProvider;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.samplers.RateLimitingSampler;
import io.jaegertracing.spi.Reporter;
import io.opentracing.Tracer;

import static io.ballerina.observe.choreo.Constants.CHOREO_EXTENSION_NAME;

/**
 * This is the open tracing extension class for {@link TracerProvider}.
 *
 * @since 2.0.0
 */
public class ChoreoTracerProvider implements TracerProvider {
    private static final Logger LOGGER = LogFactory.getLogger();
    private Reporter reporterInstance;

    @Override
    public String getName() {
        return CHOREO_EXTENSION_NAME;
    }

    @Override
    public void init() {
        if (InitUtils.isChoreoClientInitialized()) {
            reporterInstance = new ChoreoJaegerReporter();
            LOGGER.info("started publishing traces to Choreo");
        } else {
            throw ErrorCreator.createError(StringUtils.fromString(
                    "Unable to start publishing traces as Choreo Client is not initialized"));
        }
    }

    @Override
    public Tracer getTracer(String serviceName) {
        return new JaegerTracer.Builder(serviceName)
                .withSampler(new RateLimitingSampler(2))
                .withReporter(reporterInstance)
                .build();
    }
}
