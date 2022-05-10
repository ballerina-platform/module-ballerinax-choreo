/*
 * Copyright (c) 2022, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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
import io.ballerina.runtime.observability.BallerinaObserver;
import io.ballerina.runtime.observability.ObserverContext;
import io.ballerina.runtime.observability.metrics.DefaultMetricRegistry;
import io.ballerina.runtime.observability.metrics.MetricId;
import io.ballerina.runtime.observability.metrics.MetricRegistry;
import io.ballerina.runtime.observability.metrics.Tag;

import java.util.Set;

/**
 * Observe the runtime and collect measurements.
 */
public class StepCountObserver implements BallerinaObserver {
    private static final String PROPERTY_START_TIME = "_choreo_observation_start_time_";

    private static final Logger LOGGER = LogFactory.getLogger();

    private static final long stepTime;

    static {
        String propertysteptime = System.getenv("CHOREO_PER_STEP_TIME");
        if (propertysteptime == null) { 
            propertysteptime = "500";
        }
        stepTime = Long.parseLong(propertysteptime);
    }

    @Override
    public void startServerObservation(ObserverContext observerContext) {
        startObservation(observerContext);
    }

    @Override
    public void startClientObservation(ObserverContext observerContext) {
        startObservation(observerContext);
    }

    @Override
    public void stopServerObservation(ObserverContext observerContext) {
        if (!observerContext.isStarted()) {
            // Do not collect metrics if the observation hasn't started
            return;
        }
        stopObservation(observerContext);
    
    }

    @Override
    public void stopClientObservation(ObserverContext observerContext) {
        if (!observerContext.isStarted()) {
            // Do not collect metrics if the observation hasn't started
            return;
        }
        stopObservation(observerContext);
    }
    
    private void startObservation(ObserverContext observerContext) {
        observerContext.addProperty(PROPERTY_START_TIME, System.currentTimeMillis());
    }
    
    private void stopObservation(ObserverContext observerContext) {
        Set<Tag> tags = observerContext.getAllTags();
        try {
            Long startTime = (Long) observerContext.getProperty(PROPERTY_START_TIME);
            long duration = System.currentTimeMillis() - startTime;
            if (duration > stepTime) {
                long steps = Math.round(Math.ceil((double) (duration - stepTime) / stepTime));
                final MetricRegistry metricRegistry = DefaultMetricRegistry.getInstance();
                metricRegistry.counter(new MetricId("choreo_steps_total", "Total no of steps", tags)).increment(steps);
            }

        } catch (RuntimeException e) {
            LOGGER.error("Error collecting metrics for choreo_steps_total metric with tags " 
             + tags, e);
        }
    }
}
