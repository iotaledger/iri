package com.iota.iri.conf;

/**
 * Configurations for PearlDiver proof-of-work hasher.
 */
public interface PearlDiverConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#POW_THREADS}
     *
     * @return {@value PearlDiverConfig.Descriptions#POW_THREADS}
     */
    int getPowThreads();

    /**
    * Field descriptions
    */
    interface Descriptions {
        String POW_THREADS = "Number of threads to use for proof-of-work calculation. " +
                "0 means you default to a number that depends on the number of cores your machine has.";
    }
}
