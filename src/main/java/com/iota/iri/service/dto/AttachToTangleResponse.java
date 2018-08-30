package com.iota.iri.service.dto;

import java.util.List;

public class AttachToTangleResponse extends AbstractResponse {

	private List<String> trytes;
	
	public static AbstractResponse create(List<String> elements) {
		AttachToTangleResponse res = new AttachToTangleResponse();
		res.trytes = elements;
		return res;
	}
    
	/**
     * The processed transactions which you can input into broadcastTransactions and storeTransactions
     * The last 243 trytes basically consist of the: trunkTransaction + branchTransaction + nonce.
     * 
     * @return The trytes.
     */
	public List<String> getTrytes() {
		return trytes;
	}
}
