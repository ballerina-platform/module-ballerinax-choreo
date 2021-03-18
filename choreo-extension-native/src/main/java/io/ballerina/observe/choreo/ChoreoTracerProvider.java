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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

/**
 * This is the open tracing extension class for {@link TracerProvider}.
 *
 * @since 2.0.0
 */
public class ChoreoTracerProvider implements TracerProvider {
    private static final Logger LOGGER = LogFactory.getLogger();
    private static final String CHOREO_EXTENSION_NAME = "choreo";
    private volatile SpanExporter reporterInstance;

    @Override
    public String getName() {
        return CHOREO_EXTENSION_NAME;
    }

    @Override
    public void init() {    // Do nothing
    }

    @Override
    public Tracer getTracer(String serviceName) {
        synchronized (this) {
            if (reporterInstance == null) {
                if (InitUtils.isChoreoClientInitialized()) {
                    reporterInstance = new ChoreoJaegerReporter();
                    LOGGER.info("started publishing traces to Choreo");
                } else {
                    throw ErrorCreator.createError(StringUtils.fromString(
                            "Unable to start publishing traces as Choreo Client is not initialized"));
                }
            }
        }

        SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor
                        .builder(reporterInstance)
                        .build());

        return tracerProviderBuilder.setResource(
                Resource.create(Attributes.of(SERVICE_NAME, serviceName)))
                .build().get("choreo");
    }

    @Override
    public ContextPropagators getPropagators() {
        return ContextPropagators.create(B3Propagator.injectingMultiHeaders());
    }
}
