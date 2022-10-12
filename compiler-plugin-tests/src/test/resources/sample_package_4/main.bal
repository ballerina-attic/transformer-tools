import ballerinax/transformer as _;
import ballerina/tcp;

service on new tcp:Listener(3000) {

    remote function onConnect(tcp:Caller caller)
                              returns tcp:ConnectionService? {
        return null;
    }
}
