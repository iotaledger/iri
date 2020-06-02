package com.iota.iri.service.milestone;

/**
 * 
 * Service for checking our node status
 *
 */
public interface InSyncService {
    
    /**
     * Verifies if this node is currently considered in sync
     * 
     * @return <code>true</code> if we are in sync, otherwise <code>false</code>
     */
    boolean isInSync();
}
