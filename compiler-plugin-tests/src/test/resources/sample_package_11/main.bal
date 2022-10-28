import ballerinax/transformer as _;

public function helloWorld1(table<map<int>> lastName) => ();

public function helloWorld2(string... names) returns string => "Hello World";

type Annot record {
    string val;
};
