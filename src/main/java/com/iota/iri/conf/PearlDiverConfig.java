package com.iota.iri.conf;

/**
 * Configurations for PearlDiver proof-of-work hasher.
 */
public interface PearlDiverConfig extends Config {

    /**
     * @return {@value PearlDiverConfig.Descriptions#POW_THREADS}
     */
    int getPowThreads();

    /**
    * Field descriptions
    */
    interface Descriptions {
        String POW_THREADS = "Number of threads to use for proof-of-work calculation";
    }
}
