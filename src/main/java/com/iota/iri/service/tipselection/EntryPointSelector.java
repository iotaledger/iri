package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;

/**
 * This interface is used to enforce usage of the getEntryPoint() method
 * for selecting the entry point for tip selection. In order to
 * accurately determine the entry point, a tangle db connection and a
 * milestone tracking service will be required in the class calls.
 */

public interface EntryPointSelector {

    /**
     *Entry point generator for tip selection
     *<p>
     *Uses reference point and depth to determine the entry point for
     *the random walk.
     *</p>
     *
     * @param depth  Depth in milestones used for random walk
     * @return  Entry point for walk method
     */
    Hash getEntryPoint(int depth)throws Exception;

}
