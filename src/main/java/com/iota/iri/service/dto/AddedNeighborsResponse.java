package com.iota.iri.service.dto;

public class AddedNeighborsResponse extends AbstractResponse {
	
	private int addedNeighbors;

	public static AbstractResponse create(int numberOfAddedNeighbors) {
		AddedNeighborsResponse res = new AddedNeighborsResponse();
		res.addedNeighbors = numberOfAddedNeighbors;
		return res;
	}

    /**
     * Gets the number of added neighbors.
     *
     * @return The number of added neighbors.
     */
	public int getAddedNeighbors() {
		return addedNeighbors;
	}
	
}
