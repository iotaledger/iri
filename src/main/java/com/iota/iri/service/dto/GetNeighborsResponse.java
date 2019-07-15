package com.iota.iri.service.dto;

import java.util.List;

/**
 * 
 * Contains information about the result of a successful {@code getNeighbors} API call.
 * See {@link GetNeighborsResponse#create(List)} for how this response is created.
 *
 */
public class GetNeighborsResponse extends AbstractResponse {

    /**
     * The neighbors you are connected with, as well as their activity counters.
     * This includes the following statistics:
     * <ol>
     *     <li>address</li>
     *     <li>connectionType</li>
     *     <li>numberOfAllTransactions</li>
     *     <li>numberOfRandomTransactionRequests</li>
     *     <li>numberOfNewTransactions</li>
     *     <li>numberOfInvalidTransactions</li>
     *     <li>numberOfSentTransactions</li>
     *     <li>numberOfStaleTransactions</li>
     * </ol>
     * @see com.iota.iri.service.dto.GetNeighborsResponse.Neighbor
     */
    private Neighbor[] neighbors;

    /**
     * @see com.iota.iri.service.dto.GetNeighborsResponse.Neighbor
     * @return {@link #neighbors}
     */
    public Neighbor[] getNeighbors() {
        return neighbors;
    }
    
    /**
     * Creates a new {@link GetNeighborsResponse}
     * 
     * @param elements {@link com.iota.iri.network.Neighbor}
     * @return an {@link GetNeighborsResponse} filled all neighbors and their activity.
     */
    public static AbstractResponse create(final List<com.iota.iri.network.Neighbor> elements) {
        GetNeighborsResponse res = new GetNeighborsResponse();
        res.neighbors = new Neighbor[elements.size()];
        int i = 0;
        for (com.iota.iri.network.Neighbor n : elements) {
            res.neighbors[i++] = Neighbor.createFrom(n);
        }
        return res;
    }
    
    /**
     * 
     * A plain DTO of an iota neighbor.
     * 
     */
    @SuppressWarnings("unused")
    public static class Neighbor {

        /**
         * The address of your neighbor
         */
        private String address;
        
        /**
         * Number of all transactions sent (invalid, valid, already-seen)
         */
        private long numberOfAllTransactions;
        
        /**
         * Random tip requests which were sent
         */
        private long numberOfRandomTransactionRequests;
        
        /**
         * New transactions which were transmitted.
         */
        private long numberOfNewTransactions;
        
        /**
         * Invalid transactions your neighbor has sent you. 
         * These are transactions with invalid signatures or overall schema.
         */
        private long numberOfInvalidTransactions;
        
        /**
         * Stale transactions your neighbor has sent you.
         * These are transactions with a timestamp older than your latest snapshot.
         */
        private long numberOfStaleTransactions;
        
        /**
         * Amount of transactions send through your neighbor
         */
        private long numberOfSentTransactions;
        
        /**
         * The method type your neighbor is using to connect (TCP / UDP)
         */
        private String connectionType;

        /**
         * 
         * {@link #address}
         */
        public String getAddress() {
            return address;
        }

        /**
         * 
         * {@link #numberOfAllTransactions}
         */
        public long getNumberOfAllTransactions() {
            return numberOfAllTransactions;
        }

        /**
         * 
         * {@link #numberOfNewTransactions}
         */
        public long getNumberOfNewTransactions() {
            return numberOfNewTransactions;
        }

        /**
         * 
         * {@link #numberOfInvalidTransactions}
         */
        public long getNumberOfInvalidTransactions() {
            return numberOfInvalidTransactions;
        }

        /**
         * 
         * {@link #numberOfStaleTransactions}
         */
        public long getNumberOfStaleTransactions() {
            return numberOfStaleTransactions;
        }

        /**
         * 
         * {@link #numberOfSentTransactions}
         */
        public long getNumberOfSentTransactions() {
            return numberOfSentTransactions;
        }

        /**
         * 
         * {@link #numberOfRandomTransactionRequests}
         */
        public long getNumberOfRandomTransactionRequests() {
            return numberOfRandomTransactionRequests;
        }

       /**
         * 
         * {@link #connectionType}
         */
        public String getConnectionType() {
            return connectionType;
        }

        /**
         * Creates a new Neighbor DTO from a Neighbor network instance
         * @param n the neighbor currently connected to this node
         * @return a new instance of {@link GetNeighborsResponse.Neighbor}
         */
        public static Neighbor createFrom(com.iota.iri.network.Neighbor n) {
            Neighbor ne = new Neighbor();
            int port = n.getPort();
            ne.address = n.getAddress().getHostString() + ":" + port;
            ne.numberOfAllTransactions = n.getNumberOfAllTransactions();
            ne.numberOfInvalidTransactions = n.getNumberOfInvalidTransactions();
            ne.numberOfStaleTransactions = n.getNumberOfStaleTransactions();
            ne.numberOfNewTransactions = n.getNumberOfNewTransactions();
            ne.numberOfRandomTransactionRequests = n.getNumberOfRandomTransactionRequests();
            ne.numberOfSentTransactions = n.getNumberOfSentTransactions();
            ne.connectionType = n.connectionType();
            return ne;
        }
    }
}
