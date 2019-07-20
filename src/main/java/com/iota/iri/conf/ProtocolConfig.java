package com.iota.iri.conf;

import com.iota.iri.model.Hash;

/**
 * Configuration for protocol rules. Controls what transactions will be accepted by the network, and how they will
 * be propagated to other nodes.
 **/
public interface ProtocolConfig extends Config {

    /**
     * @return Descriptions#MWM
     */
    int getMwm();

    /**
     * @return Descriptions#COORDINATOR
     */
    Hash getCoordinator();

    /**
     * @return Descriptions#REQUEST_HASH_SIZE
     */
    int getRequestHashSize();

    double getpSendMilestone();

    interface Descriptions {
        String MWM = "The minimum weight magnitude is the number of trailing 0s that must appear in the end of a transaction hash. Increasing this number by 1 will result in proof of work that is 3 times as hard.";
        String COORDINATOR = "The address of the coordinator";
        String REQUEST_HASH_SIZE = "The size of the requested hash in a packet. Its size is derived from the minimal MWM value the network accepts. The larger the MWM -> the more trailing zeroes we can ignore -> smaller hash size.";
        String P_SEND_MILESTONE = DescriptionHelper.PROB_OF + "sending a milestone transaction when the node looks for a random transaction to send to a neighbor.";
    }
}
