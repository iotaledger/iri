package com.iota.iri.conf;

/**
 * Configs that should be used for tracking milestones
 */
public interface MilestoneConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#COORDINATOR_ADDRESS}
     *
     * @return {@value MilestoneConfig.Descriptions#COORDINATOR}
     */
    String getCoordinator();

    /**
     * Default Value: <tt>false</tt>
     *
     * @return {@value MilestoneConfig.Descriptions#DONT_VALIDATE_TESTNET_MILESTONE_SIG}
     */
    boolean isDontValidateTestnetMilestoneSig();

    interface Descriptions {

        String COORDINATOR = "The address of the coordinator";
        String DONT_VALIDATE_TESTNET_MILESTONE_SIG = "Disable coordinator validation on testnet";
    }
}
