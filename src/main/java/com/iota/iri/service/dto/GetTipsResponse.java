package com.iota.iri.service.dto;

import java.util.List;

public class GetTipsResponse extends AbstractResponse {
	
    /**
     * The list of current tips
     */
	private String[] hashes;

	/**
	 * Creates a new {@link GetTipsResponse}
	 * 
	 * @param elements {@link #hashes}
	 * @return a {@link GetTipsResponse} filled with the provided tips
	 */
	public static AbstractResponse create(List<String> elements) {
		GetTipsResponse res = new GetTipsResponse();
		res.hashes = elements.toArray(new String[] {});
		return res;
	}
	
    /**
     *
     * @return {@link #hashes}
     */
	public String[] getHashes() {
		return hashes;
	}

}
