package com.iota.iri.service.dto;

import java.util.List;

public class GetNeighborsResponse extends AbstractResponse {

    private Neighbor[] neighbors;

    /**
     * The list of neighbors, including the following stats: 
     *  address, connectionType,
     *  numberOfAllTransactions, numberOfRandomTransactionRequests, 
     *  numberOfNewTransactions, numberOfInvalidTransactions, numberOfSentTransactions
     * 
     * @see {@link com.iota.iri.service.dto.GetNeighborsResponse.Neighbor}
     * @return the neighbors
     */
    public Neighbor[] getNeighbors() {
        return neighbors;
    }
    
    static class Neighbor {

        private String address;
        public long numberOfAllTransactions, numberOfRandomTransactionRequests, numberOfNewTransactions, numberOfInvalidTransactions, numberOfSentTransactions;
        public String connectionType;

        /**
         * The address of your peer
         * 
         * @return the address
         */
        public String getAddress() {
            return address;
        }

        /**
         * Number of all transactions sent (invalid, valid, already-seen)
         * 
         * @return the number
         */
        public long getNumberOfAllTransactions() {
            return numberOfAllTransactions;
        }

        /**
         * New transactions which were transmitted.
         * 
         * @return the number
         */
        public long getNumberOfNewTransactions() {
            return numberOfNewTransactions;
        }

        /**
         * Invalid transactions your peer has sent you. 
         * These are transactions with invalid signatures or overall schema.
         * 
         * @return the number
         */
        public long getNumberOfInvalidTransactions() {
            return numberOfInvalidTransactions;
        }
        
        /**
         * Amount of transactions send through your peer
         * 
         * @return the number
         */
        public long getNumberOfSentTransactions() {
            return numberOfSentTransactions;
        }

        /**
         * The method type your peer is using to connect (TCP / UDP)
         * 
         * @return the connection type
         */
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
