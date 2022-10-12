import ballerina/transformer as _;
import ballerina/tcp;

listener tcpListener = check new tcp:Listener(3000);
service on new tcp:Listener(3000) {

    remote function onConnect(tcp:Caller caller)
                              returns tcp:ConnectionService {
        return new EchoService();
    }
}

service class EchoService {
    *tcp:ConnectionService;

    remote function onBytes(tcp:Caller caller, readonly & byte[] data) 
        returns tcp:Error? {
        return caller->writeBytes(data);
    }
}
