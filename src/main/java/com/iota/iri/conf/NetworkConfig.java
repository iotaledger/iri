package com.iota.iri.conf;

import java.util.List;

/**
 * Configurations for the node networking. Including ports, DNS settings, list of neighbors,
 * and various optimization parameters.
 */
public interface NetworkConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#UDP_RECEIVER_PORT}
     *
     * @return {@value NetworkConfig.Descriptions#UDP_RECEIVER_PORT}
     */
    int getUdpReceiverPort();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#TCP_RECEIVER_PORT}
     *
     * @return {@value NetworkConfig.Descriptions#TCP_RECEIVER_PORT}
     */
    int getTcpReceiverPort();

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
     * Default Value: {@value BaseIotaConfig.Defaults#MAX_PEERS}
     *
     * @return {@value NetworkConfig.Descriptions#MAX_PEERS}
     */
    int getMaxPeers();

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
     * Default Value: {@code {}}
     *
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
        String UDP_RECEIVER_PORT = "The UDP Receiver Port.";
        String TCP_RECEIVER_PORT = "The TCP Receiver Port.";
        String P_REMOVE_REQUEST = DescriptionHelper.PROB_OF + " stopping to request a transaction. This number should be " +
            "closer to 0 so non-existing transaction hashes will eventually be removed.";
        String SEND_LIMIT = "The maximum number of packets that may be sent by this node in a 1 second interval. If this number is below 0 then there is no limit.";
        String MAX_PEERS = "The maximum number of non mutually tethered connections allowed. Works only in testnet mode";
        String DNS_REFRESHER_ENABLED = "Reconnect to neighbors that have dynamic IPs.";
        String DNS_RESOLUTION_ENABLED = "Enable using DNS for neighbor peering.";
        String NEIGHBORS = "Urls of peer iota nodes.";
        String Q_SIZE_NODE = "The size of the REPLY, BROADCAST, and RECEIVE network queues.";
        String P_DROP_CACHE_ENTRY = DescriptionHelper.PROB_OF +
                "dropping recently seen transactions out of the network cache. " +
                "It may relieve cases of spam or transactions that weren't stored properly in the database";
        String CACHE_SIZE_BYTES = "The size of the network cache in bytes";
    }
}
