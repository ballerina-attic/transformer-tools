import ballerinax/transformer as _;

public isolated function helloWorld(string firstName) returns string => firstName;

public function helloWorld1(table<map<int>> lastName) => ();

public function helloWorld2(string... names) returns string => "Hello World";

public isolated function helloWorld3(string firstName, string lastName = "Root") returns string => "Hello World";

type Annot record {
    string val;
};
