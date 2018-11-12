package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;
import com.iota.iri.service.API;

/**
 * 
 * Contains information about the result of a successful {@code getTransactionsToApprove} API call.
 * See {@link API#getTransactionsToApproveStatement} for how this response is created.
 *
 */
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
