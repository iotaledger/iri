package com.iota.iri.service.dto;

import java.util.List;

public class GetNeighborsResponse extends AbstractResponse {

    private Neighbor[] neighbors;

    @SuppressWarnings("unused") // used in the API
    public Neighbor[] getNeighbors() {
        return neighbors;
    }

    static class Neighbor {

        private String address;
        public long numberOfAllTransactions, numberOfRandomTransactionRequests, numberOfNewTransactions, numberOfInvalidTransactions, numberOfSentTransactions;
        public String connectionType;

        @SuppressWarnings("unused") // used in the API
        public String getAddress() {
            return address;
        }

        @SuppressWarnings("unused") // used in the API
        public long getNumberOfAllTransactions() {
            return numberOfAllTransactions;
        }

        @SuppressWarnings("unused") // used in the API
        public long getNumberOfNewTransactions() {
            return numberOfNewTransactions;
        }

        @SuppressWarnings("unused") // used in the API
        public long getNumberOfInvalidTransactions() {
            return numberOfInvalidTransactions;
        }

        @SuppressWarnings("unused") // used in the API
        public long getNumberOfSentTransactions() {
            return numberOfSentTransactions;
        }

        @SuppressWarnings("unused") // used in the API
        public String getConnectionType() {
            return connectionType;
        }

        public static Neighbor createFrom(com.iota.iri.network.Neighbor n) {
            Neighbor ne = new Neighbor();
            int port = n.getPort();
            ne.address = n.getAddress().getHostString() + ":" + port;
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
