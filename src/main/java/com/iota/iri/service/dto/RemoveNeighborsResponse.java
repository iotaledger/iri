package com.iota.iri.service.dto;

public class RemoveNeighborsResponse extends AbstractResponse {
    
    /**
     * The amount of temporally removed neighbors to this node.
     * This amount can be 0 or more.
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
