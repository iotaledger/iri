package com.iota.iri.service.dto;

import java.util.List;

public class FindTransactionesponse extends AbstractResponse {
	
	private String [] hashes;

	public static AbstractResponse create(List<String> elements) {
		FindTransactionesponse res = new FindTransactionesponse();
		res.hashes = elements.toArray(new String[] {});
		return res;
	}
	
	public String[] getHashes() {
		return hashes;
	}
}
