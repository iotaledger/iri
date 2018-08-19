package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;

/**
 * This class represents the core API request 'getTransactionsToApprove'.
 * Tip selection which returns <code>trunkTransaction</code> and <code>branchTransaction</code>. 
 * The input value <code>depth</code> determines how many milestones to go back to for finding the transactions to approve. 
 * The higher your <code>depth</code> value, the more work you have to do as you are confirming more transactions. 
 * If the <code>depth</code> is too large (usually above 15, it depends on the node's configuration) an error will be returned. 
 * The <code>reference</code> is an optional hash of a transaction you want to approve. 
 * If it can't be found at the specified <code>depth</code> then an error will be returned.
 **/
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
