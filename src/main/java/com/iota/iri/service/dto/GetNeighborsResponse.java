package com.iota.iri.service.dto;

import java.util.List;

/**
 * This class represents the core API request 'getNeighbors'.
 * Returns the set of neighbors you are connected with, as well as their activity count. 
 * The activity counter is reset after restarting IRI.
 *
 * Return Values
 * <code>address</code>: address of your peer
 * <code>numberOfAllTransactions</code>: Number of all transactions sent (invalid, valid, already-seen)
 * <code>numberOfInvalidTransactions</code>: Invalid transactions your peer has sent you. These are transactions with invalid signatures or overall schema.
 * <code>numberOfNewTransactions</code>: New transactions which were transmitted.
 **/
public class GetNeighborsResponse extends AbstractResponse {

    private Neighbor[] neighbors;

    public Neighbor[] getNeighbors() {
        return neighbors;
    }

    static class Neighbor {

        private String address;
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
