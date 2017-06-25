package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;

import java.util.List;

public class GetBalancesResponse extends AbstractResponse {
	
	private List<String> balances;
	private String milestone;
	private int milestoneIndex;

	public static AbstractResponse create(List<String> elements, Hash milestone, int milestoneIndex) {
		GetBalancesResponse res = new GetBalancesResponse();
		res.balances = elements;
		res.milestone = milestone.toString();
		res.milestoneIndex = milestoneIndex;
		return res;
	}

	@SuppressWarnings("unused") // used in the API
	public String getMilestone() {
		return milestone;
	}

	@SuppressWarnings("unused") // used in the API
	public int getMilestoneIndex() {
		return milestoneIndex;
	}

	@SuppressWarnings("unused") // used in the API
	public List<String> getBalances() {
		return balances;
	}
}
