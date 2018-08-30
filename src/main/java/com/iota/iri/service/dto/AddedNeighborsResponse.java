package com.iota.iri.service.dto;

/**
  * Temporarily add a list of neighbors to your node. 
  * The added neighbors will be removed after relaunching IRI. 
  * Add the neighbors to your config file or supply them in the -n command line option if you want to keep them after 
  * restart.
  */
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
