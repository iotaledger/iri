package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;

/**
 * This interface is used to enforce usage of the getEntryPoint() method
 * for selecting the entry point for tip selection. In order to
 * accurately determine the entry point, a tangle db connection and a
 * milestone tracking service will be required in the class calls.
 */

public interface EntryPoint{


    Hash getEntryPoint(int depth)throws Exception;

}
