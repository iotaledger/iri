package com.iota.iri.service.dto;

/**
 * This class represents the core API request 'getInclusionStates'.
 * Get the inclusion states of a set of transactions. 
 * This is for determining if a transaction was accepted and confirmed by the network or not. 
 * You can search for multiple tips (and thus, milestones) to get past inclusion states of transactions.
 *
 * This API call simply returns a list of boolean values in the same order as the transaction list you submitted, thus you get a true/false whether a transaction is confirmed or not.
 **/
public class GetInclusionStatesResponse extends AbstractResponse {

	private boolean [] states; 

	public static AbstractResponse create(boolean[] inclusionStates) {
		GetInclusionStatesResponse res = new GetInclusionStatesResponse();
		res.states = inclusionStates;
		return res;
	}
	
    /**
     * List of boolean values in the same order as the transaction list you submitted, 
	 * thus you get a true/false whether a transaction is confirmed or not.
     *
     * @return The states.
     */
	public boolean[] getStates() {
		return states;
	}

}
