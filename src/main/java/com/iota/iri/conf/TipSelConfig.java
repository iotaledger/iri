package com.iota.iri.conf;

public interface TipSelConfig extends Config {

    int getMaxDepth();

    double getAlpha();

    int getBelowMaxDepthTransactionLimit();

    interface Descriptions {

        String MAX_DEPTH = "The maximal number of previous milestones from where you can perform the random walk";
        String ALPHA = "Parameter that defines the randomness of the tip selection. " +
                "Should be a number between 0 to infinity, where 0 is most random and infinity is most deterministic.";
        String BELOW_MAX_DEPTH_TRANSACTION_LIMIT = "The maximal number of unconfirmed transactions that may be analyzed in " +
                "order to find the latest milestone the transaction that we are stepping on during the walk approves";
    }
}
