package com.iota.iri.service.dto;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code removeNeighbors} API call.
 * See {@link API#removeNeighborsStatement} for how this response is created.
 *
 */
public class RemoveNeighborsResponse extends AbstractResponse {
    
    /**
     * The amount of temporarily removed neighbors from this node.
     * Can be 0 or more.
     */
	private int removedNeighbors;
	
	/**
     * Creates a new {@link RemoveNeighborsResponse}
     * 
     * @param numberOfRemovedNeighbors {@link #removedNeighbors}
     * @return an {@link RemoveNeighborsResponse} filled with the number of removed neighbors
     */
	public static AbstractResponse create(int numberOfRemovedNeighbors) {
		RemoveNeighborsResponse res = new RemoveNeighborsResponse();
		res.removedNeighbors = numberOfRemovedNeighbors;
		return res;
	}
	
    /**
     * 
     * @return {@link #removedNeighbors}
     */
	public int getRemovedNeighbors() {
		return removedNeighbors;
	}

}
