package com.iota.iri.service.dto;

import java.util.List;

public class GetNeighborsResponse extends AbstractResponse {
	
	private String [] neighbors;

	public static AbstractResponse create(List<String> elements) {
		GetNeighborsResponse res = new GetNeighborsResponse();
		res.neighbors = elements.toArray(new String[] {});
		return res;
	}
	
	public String[] getNeighbors() {
		return neighbors;
	}

}
