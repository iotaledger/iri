package com.iota.iri.service.dto;

import java.util.List;

/**
 * This class represents the core API request 'attachToTangle'.
 * Attaches the specified transactions (trytes) to the Tangle by doing Proof of Work. 
 * You need to supply branchTransaction as well as trunkTransaction (basically the tips which you're going to validate and reference with this transaction) - both of which you'll get through the getTransactionsToApprove API call.
 * The returned value is a different set of tryte values which you can input into broadcastTransactions and storeTransactions.
 * The returned tryte value, the last 243 trytes basically consist of the: trunkTransaction + branchTransaction + nonce. 
 * These are valid trytes which are then accepted by the network.
 **/
public class AttachToTangleResponse extends AbstractResponse {

	private List<String> trytes;
	
	public static AbstractResponse create(List<String> elements) {
		AttachToTangleResponse res = new AttachToTangleResponse();
		res.trytes = elements;
		return res;
	}
    
	/**
     * Gets the trytes
     *
     * @return The trytes.
     */
	public List<String> getTrytes() {
		return trytes;
	}
}
