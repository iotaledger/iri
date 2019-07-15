package com.iota.iri.service.dto;

import java.util.List;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code getTips} API call.
 * See {@link API#getTipsStatement} for how this response is created.
 *
 */
public class GetTipsResponse extends AbstractResponse {
	
    /**
     * The current tips as seen by this node.
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
