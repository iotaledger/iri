package com.iota.iri.service.dto;

public class RemoveNeighborsResponse extends AbstractResponse {
	
	private int removedNeighbors;

	public static AbstractResponse create(int numberOfRemovedNeighbors) {
		RemoveNeighborsResponse res = new RemoveNeighborsResponse();
		res.removedNeighbors = numberOfRemovedNeighbors;
		return res;
	}
	
	public int getRemovedNeighbors() {
		return removedNeighbors;
	}

}
