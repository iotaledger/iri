package com.iota.iri.service.dto;

/**
  * Temporarily removes a list of neighbors from your node.
  * The added neighbors will be added again after relaunching IRI. 
  * Remove the neighbors from your config file or make sure you don't supply them in the -n command line option if you 
  * want to keep them removed after restart.
  */
public class RemoveNeighborsResponse extends AbstractResponse {
	
	private int removedNeighbors;

	public static AbstractResponse create(int numberOfRemovedNeighbors) {
		RemoveNeighborsResponse res = new RemoveNeighborsResponse();
		res.removedNeighbors = numberOfRemovedNeighbors;
		return res;
	}
	
    /**
     * The number of removed neighbors.
     *
     * @return The number of removed neighbors.
     */
	public int getRemovedNeighbors() {
		return removedNeighbors;
	}

}
