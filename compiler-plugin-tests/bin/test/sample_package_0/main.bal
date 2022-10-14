import ballerina/transformer as _;
import ballerina/tcp;

type Annot record {
    string val;
};

public annotation Annot RAnnot on function;
public annotation ServiceAnnot on service;

listener tcpListener = check new tcp:Listener(3000);

@ServiceAnnot
service on new tcp:Listener(3000) {

    remote function onConnect(tcp:Caller caller)
                              returns tcp:ConnectionService? {
        return echoService();
    }
}

@RAnnot {val: "anot-val"}
public isolated function echoService() returns tcp:ConnectionService? {
    return null;
}
