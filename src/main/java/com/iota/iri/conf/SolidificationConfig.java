package com.iota.iri.conf;

/**
 * 
 * Configurations that should be used for the tip solidification process, 
 * You can also completely disable the process.
 */
public interface SolidificationConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#TIP_SOLIDIFIER_ENABLED}
     * 
     * @return {@value SolidificationConfig.Descriptions#TIP_SOLIDIFIER}
     */
    boolean isTipSolidifierEnabled();
    
    /**
     * Field descriptions
     */
    interface Descriptions {

        String TIP_SOLIDIFIER = "Scan the current tips and attempt to mark them as solid";
    }
}
