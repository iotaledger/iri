package com.iota.iri.network.neighbor;

import com.iota.iri.network.protocol.Handshake;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link Neighbor} is a peer to/from which messages are sent/read from.
 */
public interface Neighbor {

    /**
     * Defines not knowing yet what server socket port a neighbor is using.
     */
    int UNKNOWN_REMOTE_SERVER_SOCKET_PORT = -1;

    /**
     * Instructs the {@link Neighbor} to read from its source channel.
     * 
     * @return the amount of bytes read
     * @throws IOException thrown when reading from the source channel fails
     */
    int read() throws IOException;

    /**
     * Instructs the {@link Neighbor} to write to its destination channel.
     * 
     * @return the amount of bytes written
     * @throws IOException thrown when writing to the destination channel fails
     */
    int write() throws IOException;

    /**
     * Instructs the {@link Neighbor} to read from its source channel a {@link Handshake} packet.
     * 
     * @return the {@link Handshake} object defining the state of the handshaking
     * @throws IOException thrown when reading from the source channels fails
     */
    Handshake handshake() throws IOException;

    /**
     * Instructs the {@link Neighbor} to send the given {@link ByteBuffer} to its destination channel.
     *
     * @param buf the {@link ByteBuffer} containing the message to send
     */
    void send(ByteBuffer buf);

    /**
     * Gets the host address.
     * 
     * @return the host address of the neighbor (always the IP address, never a domain name)
     */
    String getHostAddress();

    /**
     * Sets the domain name.
     * 
     * @param domain the domain to set
     */
    void setDomain(String domain);

    /**
     * Gets the domain name or if not available, the IP address.
     * 
     * @return the domain name or IP address
     */
    String getDomain();

    /**
     * Gets the server socket port.
     * 
     * @return the server socket port
     */
    int getRemoteServerSocketPort();

    /**
     * Sets the server socket port.
     * 
     * @param port the port number to set
     */
    void setRemoteServerSocketPort(int port);

    /**
     * Gets the host and port which also defines the identity of the {@link Neighbor}.
     * 
     * @return the host and port
     */
    String getHostAddressAndPort();

    /**
     * Gets the current state of the {@link Neighbor}.
     * 
     * @return the state
     */
    NeighborState getState();

    /**
     * Sets the state of the {@link Neighbor}.
     * 
     * @param state
     */
    void setState(NeighborState state);

    /**
     * Gets the metrics of the {@link Neighbor}.
     * 
     * @return the metrics
     */
    NeighborMetrics getMetrics();

    /**
     * Sets the protocol version to use to communicate with this {@link Neighbor}.
     * 
     * @param version the protocol version to use
     */
    void setProtocolVersion(int version);

    /**
     * The protocol version used to communicate with the {@link Neighbor}.
     * 
     * @return the protocol version
     */
    int getProtocolVersion();

}
