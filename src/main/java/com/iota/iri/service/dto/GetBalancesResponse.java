package com.iota.iri.service.dto;

import java.util.List;

/**
 * This class represents the core API request 'getBalances'.
 * Returns the confirmed balance, as viewed by tips, in case tips is not supplied, the balance is based on the latest confirmed milestone.
 * In addition to the balances, it also returns the referencing tips (or milestone), as well as the index with which the confirmed balance was determined. 
 * The balances is returned as a list in the same order as the addresses were provided as input.
 **/
public class GetBalancesResponse extends AbstractResponse {
	
	private List<String> balances;
	private List<String> references;
	private int milestoneIndex;

	public static AbstractResponse create(List<String> elements, List<String> references, int milestoneIndex) {
		GetBalancesResponse res = new GetBalancesResponse();
		res.balances = elements;
		res.references = references;
		res.milestoneIndex = milestoneIndex;
		return res;
	}
	
    /**
     * The referencing tips
     *
     * @return The references.
     */
	public List<String> getReferences() {
		return references;
	}
	
    /**
     * The index with which the confirmed balance was determined
     *
     * @return The milestoneIndex.
     */
	public int getMilestoneIndex() {
		return milestoneIndex;
	}
	
    /**
     * The balances as a list in the same order as the addresses were provided as input
     *
     * @return The balances.
     */
	public List<String> getBalances() {
		return balances;
	}
}
