import ballerina/transformer as _;

public isolated function helloWorld(string firstName) returns string => firstName;

type Annot record {
    string val;
};
