package com.iota.iri.conf;

/**
 * Configuration for how we perform tip selections. Tip selection is invoked when a client wants to find tips to
 * attach its transactions to. The tips are invoked via random walks that start at a certain point in the tangle.
 * The parameters here affect the length and randomness of this walk.
 */
public interface TipSelConfig extends Config {

    /**
     * @return Descriptions#MAX_DEPTH
     */
    int getMaxDepth();

     /**
     * @return Descriptions#ALPHA
     */
    double getAlpha();

    /**
     * @return Descriptions#BELOW_MAX_DEPTH_TRANSACTION_LIMIT
     */
    int getBelowMaxDepthTransactionLimit();

    String getWeightCalAlgo();

    String getWalkValidator();

    String getLedgerValidator();

    String getEntryPointSelector();

    String getTipSelector();

    String getConfluxScoreAlgo();

    interface Descriptions {

        String MAX_DEPTH = "The maximal number of previous milestones from where you can perform the random walk";
        String ALPHA = "Parameter that defines the randomness of the tip selection. " +
                "Should be a number between 0 to infinity, where 0 is most random and infinity is most deterministic.";
        String BELOW_MAX_DEPTH_TRANSACTION_LIMIT = "The maximal number of unconfirmed transactions that may be analyzed in " +
                "order to find the latest milestone the transaction that we are stepping on during the walk approves";
        String WEIGHT_CAL_ALGO = "The algorithm used for computing the weight of a transaction when applying tip selection algorithms.";
        String WALK_VALIDATOR = "The validator that is being chosen.";
        String LEDGER_VALIDATOR = "The ledger validator logic that is being chosen.";
        String ENTRY_POINT_SEL_ALGO = "The algorithm being used to select entry point, default using COO (if no COO, will start from genesis).";
        String TIP_SEL_ALGO = "Tip selector algorithm MCMC/CONFLUX (MCMC by default).";
        String CONFLUX_SCORE_ALGO = "The score method used in conflux algorihtm, one is by num approvers, one is by katz centrality";
    }
}
