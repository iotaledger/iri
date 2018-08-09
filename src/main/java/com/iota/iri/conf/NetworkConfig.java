package com.iota.iri.conf;

import java.util.List;

public interface NetworkConfig extends Config {

    int getUdpReceiverPort();

    int getTcpReceiverPort();

    double getPRemoveRequest();

    int getSendLimit();

    int getMaxPeers();

    boolean isDnsRefresherEnabled();

    boolean isDnsResolutionEnabled();

    List<String> getNeighbors();

    int getqSizeNode();

    double getPDropCacheEntry();

    int getCacheSizeBytes();

    interface Descriptions {
        String UDP_RECIEVER_PORT = "The UDP Reciever Port.";
        String TCP_RECIEVER_PORT = "The TCP Reciever Port.";
        String P_REMOVE_REQUEST = DescriptionHelper.PROB_OF + " stopping to request a transaction. This number should be " +
            "closer to 0 so non-existing transaction hashes will eventually be removed.";
        String SEND_LIMIT = "The maximum number of packets that may be sent by this node in a 1 second interval. If this number is below 0 then there is no limit.";
        String MAX_PEERS = "The maximum number of non mutually tethered connections allowed. Works only in testnet mode";
        String DNS_REFRESHER_ENABLED = "Reconnect to neighbors that have dynamic IPs.";
        String DNS_RESOLUTION_ENABLED = "Enable using DNS for neighbor peering.";
        String NEIGHBORS = "Urls of peer iota nodes.";
    }
}
