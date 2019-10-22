package com.iota.iri.conf;

/**
 * 
 * Configurations that should be used for the solidification processes.
 */
public interface SolidificationConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#TIP_SOLIDIFIER_ENABLED}
     * 
     * @return {@value SolidificationConfig.Descriptions#TIP_SOLIDIFIER}
     */
    boolean isTipSolidifierEnabled();

    /**
     * The depth the solidifier will use. {@value BaseIotaConfig.Defaults#SOLIDIFIER_DEPTH}
     * 
     * @return {@value BaseIotaConfig.Defaults#SOLIDIFIER_DEPTH}
     */
    int getSolidifierDepth();

    /**
     * Returns the interval at which the solidifer will run. {@value BaseIotaConfig.Defaults#SOLIDIFIER_INTERVAL_MILLISEC}
     * 
     * @return {@value BaseIotaConfig.Defaults#SOLIDIFIER_INTERVAL_MILLISEC}
     */
    int getSolidifierIntervalMillisec();

    /**
     * Field descriptions
     */
    interface Descriptions {

        String TIP_SOLIDIFIER = "Scan the current tips and attempt to mark them as solid";
        String SOLIDIFIER_DEPTH = "The depth at which the solidifer will start";
        String SOLIDIFIER_INTERVAL_MILLISEC = "The interval at which the solidifier will run";
    }
}
