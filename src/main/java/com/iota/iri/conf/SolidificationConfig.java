package com.iota.iri.conf;

import com.iota.iri.model.Hash;

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
     * @return the coordinator address hash
     */
    Hash getCoordinator();

    /**
     * Field descriptions
     */
    interface Descriptions {
        String PRINT_SYNC_PROGRESS_ENABLED = "Whether the node should print out progress when synchronizing.";
    }
}
