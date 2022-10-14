## Overview
This package provides an implementation to validate if a given Ballerina is constrained to the transformer package type and generate the services for the transformer functions defined. The extension should generate diagnostics(errors/warnings), based on the below constraints,

- Should have one or more expression bodied function with a public and isolated qualifier
- There should not be any entry points ie. services or main
- Can have other functions to be used as utilities
- Can have any number of records
- Should not have listeners, classes
- Only expression bodied functions can be public
- Annotations cannot be allowed

### Report issues

To report bugs, request new features, start new discussions, view project boards, etc., go to the [Ballerina standard library parent repository](https://github.com/ballerina-platform/ballerina-standard-library).
