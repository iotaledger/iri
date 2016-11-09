package com.iota.iri.service.dto;

import java.util.List;

public class FindTransactionsResponse extends AbstractResponse {
	
	private String [] hashes;

	public static AbstractResponse create(List<String> elements) {
		FindTransactionsResponse res = new FindTransactionsResponse();
		res.hashes = elements.toArray(new String[] {});
		return res;
	}
	
	public String[] getHashes() {
		return hashes;
	}
}
