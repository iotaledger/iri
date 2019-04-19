package com.iota.iri.service.dto;

import com.iota.iri.network.neighbor.NeighborMetrics;
import com.iota.iri.network.neighbor.NeighborState;
import com.iota.iri.service.API;

import java.util.Collection;

/**
 * Contains information about the result of a successful {@code getNeighbors} API call.
 * See {@link API#getNeighborsStatement} for how this response is created.
 */
public class GetNeighborsResponse extends AbstractResponse {

    /**
     * The neighbors you are connected with, as well as their activity counters.
     * This includes the following statistics:
     * <ol>
     * <li>address</li>
     * <li>connectionType</li>
     * <li>numberOfAllTransactions</li>
     * <li>numberOfRandomTransactionRequests</li>
     * <li>numberOfNewTransactions</li>
     * <li>numberOfInvalidTransactions</li>
     * <li>numberOfSentTransactions</li>
     * <li>numberOfStaleTransactions</li>
     * </ol>
     *
     * @see {@link Neighbor}
     */
    private Neighbor[] neighbors;

    /**
     * @return {@link #neighbors}
     * @see Neighbor
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
    public static AbstractResponse create(final Collection<com.iota.iri.network.neighbor.Neighbor> elements) {
        GetNeighborsResponse res = new GetNeighborsResponse();

        res.neighbors = new Neighbor[elements.size()];
        int i = 0;
        for (com.iota.iri.network.neighbor.Neighbor neighbor : elements) {
            res.neighbors[i++] = GetNeighborsResponse.Neighbor.createFrom(neighbor);
        }

        return res;
    }

    /**
     * A plain DTO of an iota neighbor.
     */
    static class Neighbor {

        private String address;
        private String domain;
        public long numberOfAllTransactions,
                numberOfRandomTransactionRequests,
                numberOfNewTransactions,
                numberOfInvalidTransactions,
                numberOfStaleTransactions,
                numberOfSentTransactions;
        public String connectionType;
        public boolean connected;

        /**
         * The address of your neighbor
         *
         * @return the address
         */
        public String getAddress() {
            return address;
        }

        /**
         * The domain of your neighbor
         *
         * @return the domain
         */
        public String getDomain() {
            return domain;
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
         * Whether the neighbor is connected or not
         *
         * @return whether the neighbor is connected or not
         */
        public String isConnected() {
            return connectionType;
        }

        /**
         * Creates a new Neighbor DTO from a Neighbor network instance
         *
         * @param neighbor the neighbor currently connected to this node
         * @return a new instance of {@link Neighbor}
         */
        public static Neighbor createFrom(com.iota.iri.network.neighbor.Neighbor neighbor) {
            Neighbor ne = new Neighbor();
            int port = neighbor.getRemoteServerSocketPort();
            ne.address = neighbor.getHostAddressAndPort();
            ne.domain = neighbor.getDomain();
            NeighborMetrics metrics = neighbor.getMetrics();
            ne.numberOfAllTransactions = metrics.getAllTransactionsCount();
            ne.numberOfInvalidTransactions = metrics.getInvalidTransactionsCount();
            ne.numberOfStaleTransactions = metrics.getStaleTransactionsCount();
            ne.numberOfNewTransactions = metrics.getNewTransactionsCount();
            ne.numberOfRandomTransactionRequests = metrics.getRandomTransactionRequestsCount();
            ne.numberOfSentTransactions = metrics.getSentTransactionsCount();
            ne.connectionType = "tcp";
            ne.connected = neighbor.getState() == NeighborState.READY_FOR_MESSAGES;
            return ne;
        }
    }
}
