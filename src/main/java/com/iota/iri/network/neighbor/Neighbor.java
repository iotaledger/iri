package com.iota.iri.network.neighbor;

import com.iota.iri.network.protocol.Handshake;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Neighbor {

    int UNKNOWN_REMOTE_SERVER_SOCKET_PORT = -1;

    int read() throws IOException;

    int write() throws IOException;

    Handshake handshake() throws IOException;

    void send(ByteBuffer buf);

    String getHostAddress();

    int getRemoteServerSocketPort();

    void setRemoteServerSocketPort(int port);

    String getHostAddressAndPort();

    NeighborState getState();

    void setState(NeighborState state);

    NeighborMetrics getMetrics();

}
