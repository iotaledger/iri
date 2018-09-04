package com.iota.iri.service.dto;

import java.util.List;

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
