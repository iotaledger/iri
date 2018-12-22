package com.iota.iri.service.dto;

import java.util.List;

import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code getTrytes} API call.
 * See {@link API#getTrytesStatement} for how this response is created.
 *
 */
public class GetTrytesResponse extends AbstractResponse {
	
    /**
     * The raw transaction data (trytes) of the specified transactions.
     * These trytes can then be easily converted into the actual transaction object. 
     * See library functions as to how to transform back to a {@link Transaction}.
     */
    private String[] trytes;
    
    /**
     * Creates a new {@link GetTrytesResponse}
     * 
     * @param elements {@link #trytes}
     * @return a {@link GetTrytesResponse} filled with the provided tips
     */
	public static GetTrytesResponse create(List<String> elements) {
		GetTrytesResponse res = new GetTrytesResponse();
		res.trytes = elements.toArray(new String[] {});
		return res;
	}

    /**
     *
     * @return {@link #trytes}
     */
	public String [] getTrytes() {
		return trytes;
	}
}
