package com.iota.iri.conf;

public interface CooConfig extends Config {

    String getCoordinator();

    boolean isDontValidateTestnetMilestoneSig();

    interface Descriptions {

        String COORDINATOR = "The address of the coordinator";
        String VALIDATE_TESTNET_MILESTONE_SIG = "Enable coordinator validation on testnet";
    }
}
