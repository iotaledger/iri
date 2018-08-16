package com.iota.iri.conf;

/**
 *  General configuration parameters that every module in IRI needs.
 */
public interface Config  {

    String TESTNET_FLAG = "--testnet";

    /**
     * @return {@value Descriptions#TESTNET}
     */
    boolean isTestnet();

    interface Descriptions {

        String TESTNET = "Start in testnet mode.";
    }

     class DescriptionHelper {

         protected static final String PROB_OF = "A number between 0 and 1 that represents the probability of ";
     }
}
