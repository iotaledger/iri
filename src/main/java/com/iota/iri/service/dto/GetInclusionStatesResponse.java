package com.iota.iri.service.dto;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code getInclusionStates} API call.
 * See {@link API#getInclusionStatesStatement} for how this response is created.
 *
 */
public class GetInclusionStatesResponse extends AbstractResponse {

    /**
     * A list of booleans indicating if the transaction is confirmed or not, according to the tips supplied.
     * Order of booleans is equal to order of the supplied transactions.
     */
	private boolean [] states; 

	/**
     * Creates a new {@link GetInclusionStatesResponse}
     * 
     * @param inclusionStates {@link #states}
     * @return an {@link GetInclusionStatesResponse} filled with the error message
     */
	public static AbstractResponse create(boolean[] inclusionStates) {
		GetInclusionStatesResponse res = new GetInclusionStatesResponse();
		res.states = inclusionStates;
		return res;
	}
	
    /**
     * 
     * @return {@link #states}
     */
	public boolean[] getStates() {
		return states;
	}

}
