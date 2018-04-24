package com.iota.iri.conf;

public interface TipSelConfig extends Config {

    int getMaxDepth();

    double getAlpha();

    interface Descriptions {

        String MAX_DEPTH = "The maximal number of previous milestones from where you can perform the random walk";
        String ALPHA = "Parameter that defines the randomness of the tip selection. " +
                "Should be a number between 0 to 1, where 0 is most random and 1 is most deterministic.";
    }
}
