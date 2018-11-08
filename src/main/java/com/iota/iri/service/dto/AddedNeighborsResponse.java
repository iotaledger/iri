package com.iota.iri.service.dto;

/**
 * 
 * Contains information about the result of a successful {@code addNeighbors} API call.
 * 
 */
public class AddedNeighborsResponse extends AbstractResponse {
	
    /**
     * The amount of temporally added neighbors to this node.
     * This amount can be 0 or more.
     */
	private int addedNeighbors;
	
	/**
     * Creates a new {@link AddedNeighborsResponse}
     * 
     * @param numberOfAddedNeighbors {@linkplain #addedNeighbors}
     * @return an {@link AddedNeighborsResponse} filled with the number of added neighbors
     */
	public static AbstractResponse create(int numberOfAddedNeighbors) {
		AddedNeighborsResponse res = new AddedNeighborsResponse();
		res.addedNeighbors = numberOfAddedNeighbors;
		return res;
	}

    /**
     * 
     * @return {@linkplain #addedNeighbors}
     */
	public int getAddedNeighbors() {
		return addedNeighbors;
	}
	
}
