package com.iota.iri.service;

/**
 * 
 * ApiCommand is a list of all public API endpoints officially supported by IRI
 *
 */
public enum ApiCommand {
    
    /**
     * Add a temporary neighbor to this node
     */
    ADD_NEIGHBORS("addNeighbors"),
    
    /**
     * Prepare transactions for tangle attachment by doing proof of work
     */
    ATTACH_TO_TANGLE("attachToTangle"),
    
    /**
     * Broadcast transactions to the tangle
     */
    BROADCAST_TRANSACTIONs("broadcastTransactions"),
    
    /**
     * Check the consistency of a transaction
     */
    CHECK_CONSISTENCY("checkConsistency"),
    
    /**
     * Find transactions by bundle, address, tag and approve 
     */
    FIND_TRANSACTIONS("findTransactions"),
    
    /**
     * Get the balance of an address
     */
    GET_BALANCES("getBalances"),
    
    /**
     * Get the acceptance of a transaction on the tangle
     */
    GET_INCLUSION_STATES("getInclusionStates"),
    
    /**
     * Get the neighbors on this node, including temporary added
     */
    GET_NEIGHBORS("getNeighbors"),
    
    /**
     * Get information about this node
     */
    GET_NODE_INFO("getNodeInfo"),
    
    /**
     * Get information about the API configuration
     */
    GET_NODE_API_CONFIG("getNodeAPIConfiguration"),
    
    /**
     * Get all tips currently on this node
     */
    GET_TIPS("getTips"),
    
    /**
     * Get all the transactions this node is currently requesting
     */
    GET_MISSING_TRANSACTIONS("getMissingTransactions"),
    
    /**
     * Get 2 transactions to approve for proof of work
     */
    GET_TRANSACTIONS_TO_APPROVE("getTransactionsToApprove"),
    
    /**
     * Get trytes of a transaction by its hash
     */
    GET_TRYTES("getTrytes"),
    
    /**
     * Stop attaching to the tangle
     */
    INTERRUPT_ATTACHING_TO_TANGLE("interruptAttachingToTangle"),
    
    /**
     * Temporary remove a neighbor from this node
     */
    REMOVE_NEIGHBORS("removeNeighbors"),
    
    /**
     * Store a transaction on this node, without broadcasting
     */
    STORE_TRANSACTIONS("storeTransactions"),
    
    /**
     * Check if an address has been spent from
     */
    WERE_ADDRESSES_SPENT_FROM("wereAddressesSpentFrom");
    
    private String name;

    private ApiCommand(String name) {
        this.name = name;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    /** 
     * Looks up the {@link ApiCommand} based on its name    
     *  
     * @param name the name of the API we are looking for   
     * @return The ApiCommand if it exists, otherwise <code>null</code> 
     */ 
    public static ApiCommand findByName(String name) {  
        for (ApiCommand c : values()) { 
            if (c.toString().equals(name)) {    
                return c;   
            }   
        }   
        return null;    
    }
}
