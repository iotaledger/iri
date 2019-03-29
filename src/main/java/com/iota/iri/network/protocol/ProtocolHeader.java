package com.iota.iri.network.protocol;

public class ProtocolHeader {

    private byte version;
    private Protocol.MessageType messageType;
    private short messageSize;

    public ProtocolHeader(byte version, Protocol.MessageType messageType, short messageSize) {
        this.version = version;
        this.messageType = messageType;
        this.messageSize = messageSize;
    }

    public int getVersion() {
        return version;
    }

    public Protocol.MessageType getMessageType() {
        return messageType;
    }

    public short getMessageSize() {
        return messageSize;
    }
}
