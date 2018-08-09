package com.iota.iri.service.dto;

import java.util.List;

/**
 * This class represents the core API request 'findTransactions'.
 * Find the transactions which match the specified input and return. 
 * All input values are lists, for which a list of return values (transaction hashes), in the same order, is returned for all individual elements. 
 * The input fields can either be bundles, addresses, tags or approvees. 
 * Using multiple of these input fields returns the intersection of the values.
 **/
public class FindTransactionsResponse extends AbstractResponse {
	
	private String [] hashes;

	public static AbstractResponse create(List<String> elements) {
		FindTransactionsResponse res = new FindTransactionsResponse();
		res.hashes = elements.toArray(new String[] {});
		return res;
	}
	
    /**
     * Gets the hashes
     *
     * @return The hashes.
     */
	public String[] getHashes() {
		return hashes;
	}
}
