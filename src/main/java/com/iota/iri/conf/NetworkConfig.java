package com.iota.iri.conf;

import java.util.List;

/**
 * Configurations for the node networking. Including ports, DNS settings, list of neighbors,
 * and various optimization parameters.
 */
public interface NetworkConfig extends Config {

    /**
     * @return Descriptions#NEIGHBORING_SOCKET_ADDRESS
     */
    String getNeighboringSocketAddress();

    /**
     * @return Descriptions#NEIGHBORING_SOCKET_PORT
     */
    int getNeighboringSocketPort();

    /**
     *
     * @return Descriptions#RECONNECT_ATTEMPT_INTERVAL_SECONDS
     */
    int getReconnectAttemptIntervalSeconds();

    /**
     * @return Descriptions#AUTO_TETHERING_ENABLED
     */
    boolean isAutoTetheringEnabled();

    /**
     * @return Descriptions#P_REMOVE_REQUEST
     */
    double getpRemoveRequest();

    /**
     * @return Descriptions#SEND_LIMIT
     */
    int getSendLimit();

    /**
     * @return Descriptions#MAX_NEIGHBORS
     */
    int getMaxNeighbors();

    /**
     * @return Descriptions#DNS_REFRESHER_ENABLED
     */
    boolean isDnsRefresherEnabled();

    /**
     * @return Descriptions#DNS_RESOLUTION_ENABLED
     */
    boolean isDnsResolutionEnabled();

    /**
     * @return Descriptions#NEIGHBORS
     */
    List<String> getNeighbors();

    /**
     * @return Descriptions#Q_SIZE_NODE
     */
    int getqSizeNode();

    /**
     * @return Descriptions#P_DROP_CACHE_ENTRY
     */
    double getpDropCacheEntry();

    /**
     * @return Descriptions#CACHE_SIZE_BYTES
     */
    int getCacheSizeBytes();

    interface Descriptions {
        String NEIGHBORING_SOCKET_ADDRESS = "The address to bind the TCP server socket to.";
        String NEIGHBORING_SOCKET_PORT = "The TCP Receiver Port.";
        String RECONNECT_ATTEMPT_INTERVAL_SECONDS = "The interval at which to reconnect to wanted neighbors.";
        String AUTO_TETHERING_ENABLED = "Whether to accept new connections from unknown neighbors. "
                + "Unknown meaning neighbors which are not defined in the config and were not added via addNeighbors.";
        String P_REMOVE_REQUEST = DescriptionHelper.PROB_OF + " stopping to request a transaction. This number should be " +
                "closer to 0 so non-existing transaction hashes will eventually be removed.";
        String SEND_LIMIT = "The maximum number of packets that may be sent by this node in a 1 second interval. If this number is below 0 then there is no limit.";
        String MAX_NEIGHBORS = "The maximum number of neighbors allowed to be connected.";
        String DNS_REFRESHER_ENABLED = "Reconnect to neighbors that have dynamic IPs.";
        String DNS_RESOLUTION_ENABLED = "Enable using DNS for neighbor peering.";
        String NEIGHBORS = "Urls of neighbor iota nodes.";
        String Q_SIZE_NODE = "The size of the REPLY, BROADCAST, and RECEIVE network queues.";
        String P_DROP_CACHE_ENTRY = DescriptionHelper.PROB_OF + "dropping recently seen transactions out of the network cache.";
        String CACHE_SIZE_BYTES = "The size of the network cache in bytes";
    }
}
