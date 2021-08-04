module io.ballerina.observe.trace.extension.choreo {
    requires jaeger.core;
    requires io.opentelemetry.api;
    requires io.opentelemetry.context;
    requires io.opentelemetry.sdk.common;
    requires io.opentelemetry.sdk.trace;
    requires io.opentelemetry.extension.trace.propagation;
    requires io.ballerina.runtime;
    requires grpc.api;
    requires com.google.common;
    requires grpc.stub;
    requires grpc.protobuf;
    requires com.google.protobuf;
    requires io.opentelemetry.semconv;

    provides io.ballerina.runtime.observability.tracer.spi.TracerProvider
            with io.ballerina.observe.choreo.ChoreoTracerProvider;

    exports io.ballerina.observe.choreo.client;
    exports io.ballerina.observe.choreo.client.error;
    exports io.ballerina.observe.choreo.client.model;
}
