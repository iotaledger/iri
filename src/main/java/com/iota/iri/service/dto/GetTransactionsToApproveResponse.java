package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;

public class GetTransactionsToApproveResponse extends AbstractResponse {

    /**
     * The trunk transaction tip to reference in your transaction or bundle
     */
    private String trunkTransaction;
    
    /**
     * The branch transaction tip to reference in your transaction or bundle
     */
    private String branchTransaction;

    /**
     * Creates a new {@link GetTransactionsToApproveResponse}
     * 
     * @param trunkTransactionToApprove {@link #trunkTransaction}
     * @param branchTransactionToApprove {@link #branchTransaction}
     * @return a {@link GetTransactionsToApproveResponse} filled with the provided tips
     */
	public static AbstractResponse create(Hash trunkTransactionToApprove, Hash branchTransactionToApprove) {
		GetTransactionsToApproveResponse res = new GetTransactionsToApproveResponse();
		res.trunkTransaction = trunkTransactionToApprove.toString();
		res.branchTransaction = branchTransactionToApprove.toString();
		return res;
	}
	
    /**
     * 
     * @return {@link #branchTransaction}
     */
	public String getBranchTransaction() {
		return branchTransaction;
	}
	
    /**
     * 
     * @return {@link #trunkTransaction}
     */
	public String getTrunkTransaction() {
		return trunkTransaction;
	}
}
