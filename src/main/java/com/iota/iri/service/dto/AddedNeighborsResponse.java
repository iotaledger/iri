package com.iota.iri.service.dto;

/**
 * This class represents the core API request 'addNeighbors'.
 * Add a list of neighbors to your node. 
 * It should be noted that this is only temporary, and the added neighbors will be removed from your set of neighbors after you relaunch IRI.
 **/
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
