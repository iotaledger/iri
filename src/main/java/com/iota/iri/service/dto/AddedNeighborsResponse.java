package com.iota.iri.service.dto;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code addNeighbors} API call.
 * See {@link API#addNeighborsStatement} for how this response is created.
 * 
 */
public class AddedNeighborsResponse extends AbstractResponse {
	
    /**
     * The amount of temporally added neighbors to this node.
     * Can be 0 or more.
     */
	private int addedNeighbors;
	
	/**
     * Creates a new {@link AddedNeighborsResponse}
     * 
     * @param numberOfAddedNeighbors {@link #addedNeighbors}
     * @return an {@link AddedNeighborsResponse} filled with the number of added neighbors
     */
	public static AbstractResponse create(int numberOfAddedNeighbors) {
		AddedNeighborsResponse res = new AddedNeighborsResponse();
		res.addedNeighbors = numberOfAddedNeighbors;
		return res;
	}

    /**
     * 
     * @return {link #addedNeighbors}
     */
	public int getAddedNeighbors() {
		return addedNeighbors;
	}
	
}
