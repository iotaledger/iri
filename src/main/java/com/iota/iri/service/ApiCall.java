package com.iota.iri.service;

/**
 * 
 * ApiCalls is a list of all public API endpoints officially supported by IRI
 *
 */
public enum ApiCall {
    
    /**
     * 
     */
    ADD_NEIGHBORS("addNeighbors"),
    
    /**
     * 
     */
    ATTACH_TO_TANGLE("attachToTangle"),
    
    /**
     * 
     */
    BROADCAST_TRANSACTIONs("broadcastTransactions"),
    
    /**
     * 
     */
    FIND_TRANSACTIONS("findTransactions"),
    
    /**
     * 
     */
    GET_BALANCES("getBalances"),
    
    /**
     * 
     */
    GET_INCLUSION_STATES("getInclusionStates"),
    
    /**
     * 
     */
    GET_NEIGHBORS("getNeighbors"),
    
    /**
     * 
     */
    GET_NODE_INFO("getNodeInfo"),
    
    /**
     * 
     */
    GET_NODE_API_CONFIG("getNodeAPIConfiguration"),
    
    /**
     * 
     */
    GET_TIPS("getTips"),
    
    /**
     * 
     */
    GET_TRANSACTIONS_TO_APPROVE("getTransactionsToApprove"),
    
    /**
     * 
     */
    GET_TRYTES("getTrytes"),
    
    /**
     * 
     */
    INTERRUPT_ATTACHING_TO_TANGLE("interruptAttachingToTangle"),
    
    /**
     * 
     */
    REMOVE_NEIGHBORS("removeNeighbors"),
    
    /**
     * 
     */
    STORE_TRANSACTIONS("storeTransactions"),
    
    /**
     * 
     */
    GET_MISSING_TRANSACTIONS("getMissingTransactions"),
    
    /**
     * 
     */
    CHECK_CONSISTENCY("checkConsistency"),
    
    /**
     * 
     */
    WERE_ADDRESSES_SPENT_FROM("wereAddressesSpentFrom");
    
    private String name;

    private ApiCall(String name) {
        this.name = name;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    /**
     * Looks up the {@link ApiCall} based on its name
     * 
     * @param name the name of the api we are looking for
     * @return The apicall if it exists, otherwise <code>null</code>
     */
    public static ApiCall findByName(String name) {
        for (ApiCall c : values()) {
            if (c.toString().equals(name)) {
                return c;
            }
        }
        return null;
    }
}
