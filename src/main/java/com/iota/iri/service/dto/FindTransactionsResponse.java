package com.iota.iri.service.dto;

import java.util.List;

public class FindTransactionsResponse extends AbstractResponse {
	
	private String [] hashes;

	public static AbstractResponse create(List<String> elements) {
		FindTransactionsResponse res = new FindTransactionsResponse();
		res.hashes = elements.toArray(new String[] {});
		return res;
	}
	
    /**
      * The transaction hashes which are returned depend on your input. 
      * For each specified input value, the command will return the following:
      * <code>bundles</code>: returns the list of transactions which contain the specified bundle hash.
      * <code>addresses</code>: returns the list of transactions which have the specified address as an input/output field.
      * <code>tags</code>: returns the list of transactions which contain the specified tag value.
      * <code>approvees</code>: returns the list of transactions which reference (i.e. confirm) the specified transaction.
     *
     * @return The hashes.
     */
	public String[] getHashes() {
		return hashes;
	}
}
