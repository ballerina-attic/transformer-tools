# Overview

A transformer is a general purpose interface to apply a function to a set of data points. The output may contain data points generated by adding, removing or altering some. Ballerina-Transformer is also a such tool that provides the following capabilities.

1. Validate if a package is a transformer package
2. Generate the Ballerina service code for the transformer functions

## Validator and Generator
### Transformer Package Validator
Ballerina transformer tools can validate a Ballerina package if that comply with the constraints of a Ballerina transformer package. A Ballerina package to be treated as a transformer package it should comply for the below-mentioned constraints.
1. The package should not contain any entry-points _(ie: main function or services)_
2. The package should have one or more expression-bodied functions (transformer-function) with public and isolated qualifier.
3. The package can contain other functions which can be used at util functions (cannot be public).
4. The package can contain records
5. The package in not allowed to have class definitions or listener definitions
6. Expression bodied functions in the package is only allowed to have public qualifier
7. The package is not allowed to have annotations.
8. The public isolated expression bodied functions (transformer-functions) are allowed to have serializable types as parameter type and return type

To activate the transformer-tools in a Ballerina package to validate the package, the tools has to be imported. Once, it is imported, when we build the Ballerina transformer package, it would validate against the constraints.
```ballerina
import ballerinax/transformer as _;
```

### Ballerina Service Generator
Once a Ballerina package is validated, the transformer-tools will generate a service where it would allow the transformer-functions to be utilized through http.
The parameters of the transformer-function has to be passed as a JSON payload.

The service for the Ballerina package would only get generated if there are no any validation errors, and the parameter types and return types of transformer function are serializable. Serializable Ballerina types can be found [here](https://github.com/ballerina-platform/module-ballerina-http/blob/master/docs/spec/spec.md#2344-payload-parameter).

#### Example:
```ballerina
import ballerinax/transformer as _;

public type Person record {
    string firstName;
    string lastName;
};

public type Student record {
    string fullName;
    string school;
};

public isolated function transform(Person person, string school) returns Student => {
    fullName: person.firstName + " " + person.lastName,
    school: school
};
```

Once the above code is validated, a service would get generated for the transformer function which transforms a **Person** into a **Student**.
#### Request

`POST /transform/`

```
curl -X POST http://localhost:7000/transform
    -H 'Content-Type: application/json'
    -d '{"person": {"firstName": "Joe", "lastName": "Root"}, "school": "Kingswood"}'
```

#### Response
```
HTTP/1.1 201 Created
Date: Thu, 24 Feb 2011 12:36:30 GMT
Status: 201 Created
Connection: close
Content-Type: application/json
Location: /thing/1
Content-Length: 36

{"fullName": "Joe Root", "school": "Kingswood"}
```

For the transformer function above, it would create a Ballerina record to capture all parameters and that record would be passed as the Payload of the service's resource function.
```ballerina
type transformPayload record {
    Person person;
    string school;
};
```

## Useful links

* For example demonstrations of the usage, go to [Ballerina By Examples](https://ballerina.io/learn/by-example/).
* Chat live with us via our [Slack channel](https://ballerina.io/community/slack/).
* Post all technical questions on Stack Overflow with the [#ballerina](https://stackoverflow.com/questions/tagged/ballerina) tag.