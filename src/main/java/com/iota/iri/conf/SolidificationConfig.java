package com.iota.iri.conf;

/**
 * 
 * Configurations that should be used for the solidification processes.
 */
public interface SolidificationConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PRINT_SYNC_PROGRESS_ENABLED}
     *
     * @return {@value SolidificationConfig.Descriptions#PRINT_SYNC_PROGRESS_ENABLED}
     */
    boolean isPrintSyncProgressEnabled();

    /**
     * Field descriptions
     */
    interface Descriptions {
        String PRINT_SYNC_PROGRESS_ENABLED = "Whether the node should print out progress when synchronizing.";
    }
}
