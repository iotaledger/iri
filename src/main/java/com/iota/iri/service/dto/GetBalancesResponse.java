package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;
import com.iota.iri.utils.Pair;

import java.util.List;

public class GetBalancesResponse extends AbstractResponse {
	
	private List<String> balances;
	private String milestone;
	private int milestoneIndex;

	public static AbstractResponse create(Pair<List<String>, Pair<Hash, Integer>> balances) {
		GetBalancesResponse res = new GetBalancesResponse();
		res.balances = balances.low;
		res.milestone = balances.hi.low.toString();
		res.milestoneIndex = balances.hi.hi;
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
