package com.iota.iri.service.dto;

public class AddedNeighborsResponse extends AbstractResponse {
	
	private int addedNeighbors;

	public static AbstractResponse create(int numberOfAddedNeighbors) {
		AddedNeighborsResponse res = new AddedNeighborsResponse();
		res.addedNeighbors = numberOfAddedNeighbors;
		return res;
	}

	@SuppressWarnings("unused") // used in the API
	public int getAddedNeighbors() {
		return addedNeighbors;
	}
	
}
