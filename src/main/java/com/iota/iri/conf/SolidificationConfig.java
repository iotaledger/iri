package com.iota.iri.conf;

/**
 * 
 * Configurations that should be used for the tip solidification process, 
 * You can also completely disable the process.
 */
public interface SolidificationConfig extends Config {

    /**
     * @return {@value Descriptions#TIP_SOLIDIFIER_ENABLED}
     */
    boolean isTipSolidifierEnabled();
    
    /**
     * Field descriptions
     */
    interface Descriptions {

        String TIP_SOLIDIFIER_ENABLED = "Flag that determines if tip solidification is enabled.";
    }
}
