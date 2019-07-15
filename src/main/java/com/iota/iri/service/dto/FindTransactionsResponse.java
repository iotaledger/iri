package com.iota.iri.service.dto;

import java.util.List;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code findTransactions} API call.
 * See {@link API#findTransactionsStatement} for how this response is created.
 * 
 */
public class FindTransactionsResponse extends AbstractResponse {
	
  /**
    * The transaction hashes which are returned depend on your input. 
    * For each specified input value, the command will return the following:
    * <ul>
    * <li><code>bundles</code>: returns an array of transaction hashes that contain the given bundle hash.</li>
    * <li><code>addresses</code>: returns an array of transaction hashes that contain the given address in the `address` field.</li>
    * <li><code>tags</code>: returns an array of transaction hashes that contain the given value in the `tag` field.</li>
    * <li><code>approvees</code>: returns an array of transaction hashes that contain the given transactions in their `branchTransaction` or `trunkTransaction` fields.</li>
    * </ul>
    */
	private String [] hashes;

	/**
	 * Creates a new {@link FindTransactionsResponse}
	 * 
	 * @param elements {@link #hashes}
	 * @return an {@link FindTransactionsResponse} filled with the hashes
	 */
	public static AbstractResponse create(List<String> elements) {
		FindTransactionsResponse res = new FindTransactionsResponse();
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
