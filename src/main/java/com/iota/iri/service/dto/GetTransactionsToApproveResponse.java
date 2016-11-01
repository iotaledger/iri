package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;

public class GetTransactionsToApproveResponse extends AbstractResponse {

    private String trunkTransaction;
    private String branchTransactionToApprove;

	public static AbstractResponse create(Hash trunkTransactionToApprove, Hash branchTransactionToApprove) {
		GetTransactionsToApproveResponse res = new GetTransactionsToApproveResponse();
		res.trunkTransaction = trunkTransactionToApprove.toString();
		res.branchTransactionToApprove = branchTransactionToApprove.toString();
		return res;
	}
	
	public String getBranchTransactionToApprove() {
		return branchTransactionToApprove;
	}
	
	public String getTrunkTransaction() {
		return trunkTransaction;
	}
}
