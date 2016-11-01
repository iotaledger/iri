package com.iota.iri.service.dto;

import java.util.List;

import com.iota.iri.model.Hash;

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
	
	public String getMilestone() {
		return milestone;
	}
	
	public int getMilestoneIndex() {
		return milestoneIndex;
	}
	
	public List<String> getBalances() {
		return balances;
	}
}
