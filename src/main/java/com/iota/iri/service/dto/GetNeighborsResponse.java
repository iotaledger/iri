package com.iota.iri.service.dto;

import com.iota.iri.network.neighbor.NeighborMetrics;
import com.iota.iri.network.neighbor.NeighborState;

import java.util.Collection;

/**
 * Contains information about the result of a successful {@code getNeighbors} API call.
 * See {@link GetNeighborsResponse#create(Collection)} for how this response is created.
 *
 */
public class GetNeighborsResponse extends AbstractResponse {

    /**
     * The neighbors you are connected with, as well as their activity counters.
     *
     * @see Neighbor
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
     * @param elements {@link com.iota.iri.network.neighbor.Neighbor}
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
    @SuppressWarnings("unused")
    public static class Neighbor {

        /**
         * The address of your neighbor.
         */
        private String address;

        /**
         * The origin domain or IP address of the given neighbor.
         */
        private String domain;

        /**
         * Number of all transactions sent (invalid, valid, already-seen)
         */
        private long numberOfAllTransactions;

        /**
         * Random tip requests which were sent.
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
         * Amount of transactions send to your neighbor.
         */
        private long numberOfSentTransactions;

        /**
         * Amount of packets dropped from the neighbor's send queue as it was full.
         */
        private long numberOfDroppedSentPackets;

        /**
         * The transport protocol used to the neighbor.
         */
        private String connectionType;

        /**
         * Whether the node is currently connected.
         */
        public boolean connected;

        /**
         * Creates a new Neighbor DTO from a Neighbor network instance.
         *
         * @param neighbor the neighbor currently connected to this node
         * @return a new instance of {@link Neighbor}
         */
        public static Neighbor createFrom(com.iota.iri.network.neighbor.Neighbor neighbor) {
            Neighbor ne = new Neighbor();
            NeighborMetrics metrics = neighbor.getMetrics();
            int port = neighbor.getRemoteServerSocketPort();
            String hostAddr = neighbor.getHostAddress();
            ne.address = hostAddr == null || hostAddr.isEmpty() ? "" : neighbor.getHostAddressAndPort();
            ne.domain = neighbor.getDomain();
            ne.numberOfAllTransactions = metrics.getAllTransactionsCount();
            ne.numberOfInvalidTransactions = metrics.getInvalidTransactionsCount();
            ne.numberOfStaleTransactions = metrics.getStaleTransactionsCount();
            ne.numberOfNewTransactions = metrics.getNewTransactionsCount();
            ne.numberOfSentTransactions = metrics.getSentTransactionsCount();
            ne.numberOfDroppedSentPackets = metrics.getDroppedSendPacketsCount();
            ne.numberOfRandomTransactionRequests = metrics.getRandomTransactionRequestsCount();
            ne.connectionType = "tcp";
            ne.connected = neighbor.getState() == NeighborState.READY_FOR_MESSAGES;
            return ne;
        }

        /**
         *
         * {@link #address}
         */
        public String getAddress() {
            return address;
        }

        /**
         * {@link #domain}
         */
        public String getDomain() {
            return domain;
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
         * {@link #numberOfDroppedSentPackets}
         */
        public long getNumberOfDroppedSentPackets() {
            return numberOfDroppedSentPackets;
        }

        /**
         * {@link #connected}
         */
        public boolean isConnected(){
            return connected;
        }

        /**
         *
         * {@link #connectionType}
         */
        public String getConnectionType() {
            return connectionType;
        }
    }
}