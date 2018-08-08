package com.iota.iri.service.dto;

import java.util.List;
import java.net.InetSocketAddress;

public class GetNeighborsResponse extends AbstractResponse {

    private Neighbor[] neighbors;

    public Neighbor[] getNeighbors() {
        return neighbors;
    }

    static class Neighbor {

        private String address, ip;
        public long numberOfAllTransactions, numberOfRandomTransactionRequests, numberOfNewTransactions, numberOfInvalidTransactions, numberOfSentTransactions;
        public String connectionType;

        public String getAddress() {
            return address;
        }

        public long getNumberOfAllTransactions() {
            return numberOfAllTransactions;
        }

        public long getNumberOfNewTransactions() {
            return numberOfNewTransactions;
        }

        public long getNumberOfInvalidTransactions() {
            return numberOfInvalidTransactions;
        }
        
        public long getNumberOfSentTransactions() {
            return numberOfSentTransactions;
        }

        public String getConnectionType() {
            return connectionType;
        }

        public static Neighbor createFrom(com.iota.iri.network.Neighbor n) {
            Neighbor ne = new Neighbor();
            InetSocketAddress socketAddress = n.getAddress();
            int port = n.getPort();
            ne.address = socketAddress.getHostString() + ":" + port;
            ne.ip = socketAddress.getAddress().getHostAddress();
            ne.numberOfAllTransactions = n.getNumberOfAllTransactions();
            ne.numberOfInvalidTransactions = n.getNumberOfInvalidTransactions();
            ne.numberOfNewTransactions = n.getNumberOfNewTransactions();
            ne.numberOfRandomTransactionRequests = n.getNumberOfRandomTransactionRequests();
            ne.numberOfSentTransactions = n.getNumberOfSentTransactions();
            ne.connectionType = n.connectionType();
            return ne;
        }
    }

    public static AbstractResponse create(final List<com.iota.iri.network.Neighbor> elements) {
        GetNeighborsResponse res = new GetNeighborsResponse();
        res.neighbors = new Neighbor[elements.size()];
        int i = 0;
        for (com.iota.iri.network.Neighbor n : elements) {
            res.neighbors[i++] = Neighbor.createFrom(n);
        }
        return res;
    }

}
