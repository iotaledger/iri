package com.iota.iri.service.dto;

import java.util.List;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code getNeighbors} API call.
 * See {@link API#getNeighborsStatement} for how this response is created.
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
     * @see {@link com.iota.iri.service.dto.GetNeighborsResponse.Neighbor}
     */
    private Neighbor[] neighbors;

    /**
     * 
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
    static class Neighbor {

        private String address;
        public long numberOfAllTransactions,
                numberOfRandomTransactionRequests,
                numberOfNewTransactions,
                numberOfInvalidTransactions,
                numberOfStaleTransactions,
                numberOfSentTransactions;
        public String connectionType;

        /**
         * The address of your neighbor
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
         * Invalid transactions your neighbor has sent you. 
         * These are transactions with invalid signatures or overall schema.
         * 
         * @return the number
         */
        public long getNumberOfInvalidTransactions() {
            return numberOfInvalidTransactions;
        }

        /**
         * Stale transactions your neighbor has sent you.
         * These are transactions with a timestamp older than your latest snapshot.
         *
         * @return the number
         */
        public long getNumberOfStaleTransactions() {
            return numberOfStaleTransactions;
        }

        /**
         * Amount of transactions send through your neighbor
         * 
         * @return the number
         */
        public long getNumberOfSentTransactions() {
            return numberOfSentTransactions;
        }

        /**
         * The method type your neighbor is using to connect (TCP / UDP)
         * 
         * @return the connection type
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
