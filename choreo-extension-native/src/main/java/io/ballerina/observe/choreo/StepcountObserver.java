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

import io.ballerina.runtime.observability.BallerinaObserver;
import io.ballerina.runtime.observability.ObserverContext;

import java.io.PrintStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import io.ballerina.runtime.observability.metrics.MetricRegistry;
import io.ballerina.runtime.observability.metrics.Tag;
import io.ballerina.runtime.observability.metrics.DefaultMetricRegistry;
import io.ballerina.runtime.observability.metrics.MetricId;

/**
 * Observe the runtime and collect measurements.
 */
public class StepcountObserver implements BallerinaObserver {
    private static final String PROPERTY_START_TIME = "_observation_start_time_";
    private static final PrintStream consoleError = System.err;
    private static final MetricRegistry metricRegistry = DefaultMetricRegistry.getInstance();
    
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
        observerContext.addProperty(PROPERTY_START_TIME, System.nanoTime());
    }
    
    private void stopObservation(ObserverContext observerContext) {
        Set<Tag> tags = new HashSet<>();
        try {
            Long startTime = (Long) observerContext.getProperty(PROPERTY_START_TIME);
            long duration = System.nanoTime() - startTime;
            long steps= Math.round(Math.ceil((double)(duration-500)/500));
            //System.out.println(steps);
            metricRegistry.counter(new MetricId("steps_total",
                    "Total no of steps", tags)).increment(steps);
        } catch (RuntimeException e) {
            handleError("multiple metrics", tags, e);
        }
    }
    
    private void handleError(String metricName, Set<Tag> tags, RuntimeException e) {
        // Metric Provider may throw exceptions if there is a mismatch in tags.
        consoleError.println("error: error collecting metrics for " + metricName + " with tags " + tags +
                ": " + e.getMessage());
    }
}
