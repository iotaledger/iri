package com.iota.iri.conf;

import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;

/**
 * Configs that should be used for tracking milestones
 */
public interface MilestoneConfig extends Config {

    /**
     * Default Value: {@link BaseIotaConfig.Defaults#COORDINATOR}
     *
     * @return {@value MilestoneConfig.Descriptions#COORDINATOR}
     */
    Hash getCoordinator();

    /**
     * Default Value: {@value TestnetConfig.Defaults#DONT_VALIDATE_TESTNET_MILESTONE_SIG}
     *
     * @return {@value MilestoneConfig.Descriptions#DONT_VALIDATE_TESTNET_MILESTONE_SIG}
     */
    boolean isDontValidateTestnetMilestoneSig();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#NUMBER_OF_KEYS_IN_A_MILESTONE}
     * @return {@value Descriptions#NUMBER_OF_KEYS_IN_A_MILESTONE}
     */
    int getNumberOfKeysInMilestone();

    /**
     * This is a meta-config. Its value depends on {@link #getNumberOfKeysInMilestone()}
     * @return the maximal amount of possible milestones that can be issued
     */
    int getMaxMilestoneIndex();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#COORDINATOR_SECURITY_LEVEL}
     * @return {@value Descriptions#COORDINATOR_SECURITY_LEVEL}
     */
    int getCoordinatorSecurityLevel();

    /**
     * Default Value: {@link BaseIotaConfig.Defaults#COORDINATOR_SIGNATURE_MODE}
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
