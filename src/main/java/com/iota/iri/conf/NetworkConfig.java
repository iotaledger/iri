package com.iota.iri.conf;

import java.util.List;

/**
 * Configurations for the node networking. Including ports, DNS settings, list of neighbors,
 * and various optimization parameters.
 */
public interface NetworkConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#NEIGHBORING_SOCKET_ADDRESS}
     *
     * @return {@value NetworkConfig.Descriptions#NEIGHBORING_SOCKET_ADDRESS}
     */
    String getNeighboringSocketAddress();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#NEIGHBORING_SOCKET_PORT}
     *
     * @return {@value NetworkConfig.Descriptions#NEIGHBORING_SOCKET_PORT}
     */
    int getNeighboringSocketPort();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#RECONNECT_ATTEMPT_INTERVAL_SECONDS}
     *
     * @return {@value NetworkConfig.Descriptions#RECONNECT_ATTEMPT_INTERVAL_SECONDS}
     */
    int getReconnectAttemptIntervalSeconds();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#AUTO_TETHERING_ENABLED}
     *
     * @return {@value NetworkConfig.Descriptions#AUTO_TETHERING_ENABLED{
     */
    boolean isAutoTetheringEnabled();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#P_REMOVE_REQUEST}
     *
     * @return {@value NetworkConfig.Descriptions#P_REMOVE_REQUEST}
     */
    double getpRemoveRequest();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#SEND_LIMIT}
     *
     * @return {@value NetworkConfig.Descriptions#SEND_LIMIT}
     */
    int getSendLimit();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#MAX_NEIGHBORS}
     *
     * @return {@value NetworkConfig.Descriptions#MAX_NEIGHBORS}
     */
    int getMaxNeighbors();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#DNS_REFRESHER_ENABLED}
     *
     * @return {@value NetworkConfig.Descriptions#DNS_REFRESHER_ENABLED}
     */
    boolean isDnsRefresherEnabled();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#DNS_RESOLUTION_ENABLED}
     *
     * @return {@value NetworkConfig.Descriptions#DNS_RESOLUTION_ENABLED}
     */
    boolean isDnsResolutionEnabled();

    /**
     * @return {@value NetworkConfig.Descriptions#NEIGHBORS}
     */
    List<String> getNeighbors();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#QUEUE_SIZE}
     * @return {@value NetworkConfig.Descriptions#Q_SIZE_NODE}
     */
    int getqSizeNode();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#P_DROP_CACHE_ENTRY}
     *
     * @return {@value NetworkConfig.Descriptions#P_DROP_CACHE_ENTRY}
     */
    double getpDropCacheEntry();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#CACHE_SIZE_BYTES}
     *
     * @return {@value NetworkConfig.Descriptions#CACHE_SIZE_BYTES}
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
        String P_DROP_CACHE_ENTRY = DescriptionHelper.PROB_OF +
                "dropping recently seen transactions out of the network cache. " +
                "It may relieve cases of spam or transactions that weren't stored properly in the database";
        String CACHE_SIZE_BYTES = "The size of the network cache in bytes";
    }
}
