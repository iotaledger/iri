package com.iota.iri.service.dto;

import java.util.List;

public class GetTrytesResponse extends AbstractResponse {
	
    private String [] trytes;
    
	public static GetTrytesResponse create(List<String> elements) {
		GetTrytesResponse res = new GetTrytesResponse();
		res.trytes = elements.toArray(new String[] {});
		return res;
	}

	@SuppressWarnings("unused") // used in the API
	public String [] getTrytes() {
		return trytes;
	}
}
