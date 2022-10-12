import ballerina/transformer as _;
import ballerina/tcp;

listener tcpListener = check new tcp:Listener(3000);
service on new tcp:Listener(3000) {

    remote function onConnect(tcp:Caller caller)
                              returns tcp:ConnectionService? {
        return echoService();
    }
}

public isolated function echoService() returns tcp:ConnectionService? {
    return null;
}
