package com.iota.iri.service.dto;

import java.util.List;

import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code getBalances} API call.
 * See {@link API#getBalancesStatement} for how this response is created.
 *
 */
public class GetBalancesResponse extends AbstractResponse {
	
    /**
     * The balances as a list in the same order as the addresses were provided as input
     */
	private List<String> balances;
	
	/**
	 * The tips used to view the balances. If none were supplied this will be the latest confirmed milestone.
	 */
	private List<String> references;
	
	/**
     * The milestone index with which the confirmed balance was determined
     */
	private int milestoneIndex;

	/**
     * Creates a new {@link GetBalancesResponse}
     * 
     * @param elements {@link #balances}
     * @param references {@link #references}
     * @param milestoneIndex {@link #milestoneIndex}
     * @return an {@link GetBalancesResponse} filled with the balances, references used and index used
     */
	public static AbstractResponse create(List<String> elements, List<String> references, int milestoneIndex) {
		GetBalancesResponse res = new GetBalancesResponse();
		res.balances = elements;
		res.references = references;
		res.milestoneIndex = milestoneIndex;
		return res;
	}
	
    /**
     * 
     * @return {@link #references}
     */
	public List<String> getReferences() {
		return references;
	}
	
    /**
     * 
     * @return {@link #milestoneIndex}
     */
	public int getMilestoneIndex() {
		return milestoneIndex;
	}
	
    /**
     * 
     * @return {@link #balances}
     */
	public List<String> getBalances() {
		return balances;
	}
}
