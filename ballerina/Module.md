## Overview
This package provides an implementation to validate if a given Ballerina is constrained to the transformer package type.

### Transformer Code Validator and Generator
#### Transformer Package Validator
A transformer package should adhere to certain constraints mentioned below, the Transformer code validator would validate the package based on the below defined rules.
- Should have one or more expression bodied function with a public and isolated qualifier
- There should not be any entry points ie. services or main
- Can have other functions to be used as utilities
- Can have any number of records
- Should not have listeners, classes
- Only expression bodied functions can be public
- Annotations cannot be allowed

#### Transformer Service Generator
Transformer service generator would generate services for the transformer functions. This generation has to happen once the validation is successful.
