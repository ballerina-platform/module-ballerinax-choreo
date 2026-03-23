## Overview

The Choreo Observability Extension provides an implementation for publishing traces and metrics to [Choreo](https://wso2.com/choreo/).

### Key Features

- Publish traces and metrics to the Choreo observability platform
- Simple configuration via import and Config.toml
- Seamless integration with Choreo's monitoring and analytics
- Support for OpenTelemetry-based tracing

## Enabling Choreo Extension

To package the Choreo extension into the Jar, follow the following steps.
1. Add the following import to your program.
```ballerina
import ballerinax/choreo as _;
```

2. Add the following to the `Ballerina.toml` when building your program.
```toml
[package]
org = "my_org"
name = "my_package"
version = "1.0.0"

[build-options]
observabilityIncluded=true
```

To enable the extension and connect to Choreo, add the following to the `Config.toml` when running your program.
```toml
[ballerina.observe]
enabled=true
provider="choreo"
```
