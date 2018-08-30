package com.iota.iri.service.dto;

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
