package com.iota.iri.service.dto;

import java.util.List;

/**
 * This class represents the core API request 'getTrytes'.
 * Returns the raw transaction data (trytes) of a specific transaction. 
 * These trytes can then be easily converted into the actual transaction object. 
 * See utility functions for more details.
 **/
public class GetTrytesResponse extends AbstractResponse {
	
    private String [] trytes;
    
	public static GetTrytesResponse create(List<String> elements) {
		GetTrytesResponse res = new GetTrytesResponse();
		res.trytes = elements.toArray(new String[] {});
		return res;
	}

    /**
     * The raw transaction data (trytes) of the specified transactions
     *
     * @return The trytes
     */
	public String [] getTrytes() {
		return trytes;
	}
}
