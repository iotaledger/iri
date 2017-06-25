package com.iota.iri.service.dto;

import java.util.List;

public class GetTipsResponse extends AbstractResponse {
	
	private String [] hashes;

	public static AbstractResponse create(List<String> elements) {
		GetTipsResponse res = new GetTipsResponse();
		res.hashes = elements.toArray(new String[] {});
		return res;
	}

	@SuppressWarnings("unused") // used in the API
	public String[] getHashes() {
		return hashes;
	}

}
