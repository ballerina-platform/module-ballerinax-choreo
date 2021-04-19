## Package Overview

The Choreo Observability Extension is one of the observability extensions of the<a target="_blank" href="https://ballerina.io/"> Ballerina</a> language.

It provides an implementation for publishing traces & metrics to Choreo.

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
