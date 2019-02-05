package com.iota.iri.conf;

import com.iota.iri.crypto.SpongeFactory;

/**
 * Configs that should be used for tracking milestones
 */
public interface MilestoneConfig extends Config {

    /**
     * @return Descriptions#COORDINATOR
     */
    String getCoordinator();

    /**
     * @return {@value Descriptions#DONT_VALIDATE_TESTNET_MILESTONE_SIG}
     */
    boolean isDontValidateTestnetMilestoneSig();

    /**
     * @return {@value Descriptions#NUMBER_OF_KEYS_IN_A_MILESTONE}
     */
    int getNumberOfKeysInMilestone();

    /**
     * @return {@value Descriptions#COORDINATOR_SECURITY_LEVEL}
     */
    int getCoordinatorSecurityLevel();

    /**
     * @return {@value Descriptions#COORDINATOR_SIGNATURE_MODE}
     */
    SpongeFactory.Mode getCoordinatorSignatureMode();

    interface Descriptions {

        String COORDINATOR = "The address of the coordinator";
        String COORDINATOR_SECURITY_LEVEL = "The security level used in coordinator signatures";
        String COORDINATOR_SIGNATURE_MODE = "The signature mode used in coordinator signatures";
        String DONT_VALIDATE_TESTNET_MILESTONE_SIG = "Disable coordinator validation on testnet";
        String NUMBER_OF_KEYS_IN_A_MILESTONE = "The depth of the Merkle tree which in turn determines the number of" +
                "leaves (private keys) that the coordinator can use to sign a message.";
    }
}
