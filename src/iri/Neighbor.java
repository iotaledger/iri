package iri;

import java.net.*;

public class Neighbor {

    final SocketAddress address;
    int numberOfAllTransactions, numberOfNewTransactions, numberOfInvalidTransactions;

    Neighbor(final SocketAddress address) {

        this.address = address;
    }

    void send(final DatagramPacket packet) {

        try {

            packet.setSocketAddress(address);
            Node.socket.send(packet);

        } catch (final Exception e) {
        }
    }

    @Override
    public boolean equals(final Object obj) {

        return address.equals(((Neighbor)obj).address);
    }

    @Override
    public int hashCode() {

        return address.hashCode();
    }

    @Override
    public String toString() {

        return "\"address\": \"" + address + "\""
                + ", \"numberOfAllTransactions\": " + numberOfAllTransactions
                + ", \"numberOfNewTransactions\": " + numberOfNewTransactions
                + ", \"numberOfInvalidTransactions\": " + numberOfInvalidTransactions;
    }
}
