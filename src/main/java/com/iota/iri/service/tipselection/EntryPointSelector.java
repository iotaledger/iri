package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;

/**
 * Selects an entryPoint for tip selection.
 * <p>
 * this point is used as the starting point where
 * the particle starts the random walk.
 * </p>
 */

public interface EntryPointSelector {

    /**
     *get an entryPoint for tip selection
     *
     *Uses depth to determine the entry point for
     *the random walk.
     *
     * @param depth Depth, in milestones. a notion of how deep to search for a good starting point.
     * @return  Entry point for walk method
     * @throws Exception If DB fails to retrieve transactions
     */
    Hash getEntryPoint(int depth)throws Exception;

}
