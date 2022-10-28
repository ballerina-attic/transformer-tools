import ballerinax/transformer as _;

public isolated function helloWorld(string firstName, Annot annot, StrType str) returns Test => [1, 2];

public isolated function helloWorld1(table<map<int>> lastName, [string, string] tuple) => ();

public isolated function helloWorld2(string... names) returns string => "Hello World";

public isolated function helloWorld3(string firstName, string lastName = "Root") returns Annot => {val: "value"};

public type Annot record {
    string val;
};

public type StrType table<map<map<int[]>>>;

public type Test int[];
