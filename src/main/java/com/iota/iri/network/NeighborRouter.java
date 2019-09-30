package com.iota.iri.network;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.pipeline.TransactionProcessingPipelineImpl;

import java.util.List;
import java.util.Map;

/**
 * A NeighborRouter takes care of managing connections to {@link Neighbor} instances, executing reads and writes from/to
 * neighbors and ensuring that wanted neighbors are connected. <br/>
 * A neighbor is identified by its identity which is made up of the IP address and the neighbor's own server socket port
 * for new incoming connections; for example: 153.59.34.101:15600. <br/>
 * The NeighborRouter and foreign neighbor will first exchange their server socket port via a handshaking packet, in
 * order to fully build up the identity between each other. If handshaking fails, the connection is dropped.
 */
public interface NeighborRouter {

    /**
     * Starts a dedicated thread for the {@link NeighborRouter} and then starts the routing mechanism.
     */
    void start();

    /**
     * <p>
     * Starts the routing mechanism which first initialises connections to neighbors from the configuration and then
     * continuously reads and writes messages from/to neighbors.
     * </p>
     * <p>
     * This method will also try to reconnect to wanted neighbors by the given
     * {@link BaseIotaConfig#getReconnectAttemptIntervalSeconds()} value.
     * </p>
     */
    void route();

    /**
     * Adds the given neighbor to the {@link NeighborRouter}. The {@link} Selector is woken up and an attempt to connect
     * to wanted neighbors is initiated.
     *
     * @param rawURI The URI of the neighbor
     * @return whether the neighbor was added or not
     */
    NeighborMutOp addNeighbor(String rawURI);

    /**
     * Removes the given neighbor from the {@link NeighborRouter} by marking it for "disconnect". The neighbor is
     * disconnected as soon as the next selector loop is executed.
     *
     * @param uri The URI of the neighbor
     * @return whether the neighbor was removed or not
     */
    NeighborMutOp removeNeighbor(String uri);

    /**
     * Returns the {@link TransactionProcessingPipelineImpl}.
     *
     * @return the {@link TransactionProcessingPipelineImpl} used by the {@link NeighborRouter}
     */
    TransactionProcessingPipeline getTransactionProcessingPipeline();

    /**
     * Gets all neighbors the {@link NeighborRouter} currently sees as either connected or attempts to build connections
     * to.
     *
     * @return The neighbors
     */
    List<Neighbor> getNeighbors();

    /**
     * Gets the currently connected neighbors.
     *
     * @return The connected neighbors.
     */
    Map<String, Neighbor> getConnectedNeighbors();

    /**
     * Gossips the given transaction to the given neighbor.
     *
     * @param neighbor The {@link Neighbor} to gossip the transaction to
     * @param tvm      The transaction to gossip
     * @throws Exception thrown when loading a hash of transaction to request fails
     */
    void gossipTransactionTo(Neighbor neighbor, TransactionViewModel tvm) throws Exception;

    /**
     * Gossips the given transaction to the given neighbor.
     *
     * @param neighbor     The {@link Neighbor} to gossip the transaction to
     * @param tvm          The transaction to gossip
     * @param useHashOfTVM Whether to use the hash of the given transaction as the requested transaction hash or not
     * @throws Exception thrown when loading a hash of transaction to request fails
     */
    void gossipTransactionTo(Neighbor neighbor, TransactionViewModel tvm, boolean useHashOfTVM)
            throws Exception;

    /**
     * Shut downs the {@link NeighborRouter} and all currently open connections.
     */
    void shutdown();

    /**
     * Defines whether a neighbor got added/removed or not and the corresponding reason.
     */
    enum NeighborMutOp {
        OK, SLOTS_FILLED, URI_INVALID, UNRESOLVED_DOMAIN, UNKNOWN_NEIGHBOR
    }
}
