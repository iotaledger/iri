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
	
	public List<String> getReferences() {
		return references;
	}
	
	public int getMilestoneIndex() {
		return milestoneIndex;
	}
	
	public List<String> getBalances() {
		return balances;
	}
}
