package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;

public class GetTransactionsToApproveResponse extends AbstractResponse {

    private String trunkTransaction;
    private String branchTransaction;

	public static AbstractResponse create(Hash trunkTransactionToApprove, Hash branchTransactionToApprove) {
		GetTransactionsToApproveResponse res = new GetTransactionsToApproveResponse();
		res.trunkTransaction = trunkTransactionToApprove.toString();
		res.branchTransaction = branchTransactionToApprove.toString();
		return res;
	}
	
    /**
     * The branch transaction
     *
     * @return The branch transaction.
     */
	public String getBranchTransaction() {
		return branchTransaction;
	}
	
    /**
     * The trunk transaction
     *
     * @return The trunk transaction.
     */
	public String getTrunkTransaction() {
		return trunkTransaction;
	}
}
