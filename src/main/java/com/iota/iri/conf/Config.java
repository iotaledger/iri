package com.iota.iri.conf;

/**
 *  General configuration parameters that every module in IRI needs.
 */
public interface Config  {

    String TESTNET_FLAG = "--testnet";
    String FAKE_CONFIG_FLAG = "--fakeconfig";
    /**
     * @return {@value Descriptions#TESTNET}
     */
    boolean isTestnet();
    
    /**
     * @return {@value Descriptions#FAKE_CONFIG}
     */
    boolean isFakeconfig();

    interface Descriptions {

        String TESTNET = "Start in testnet mode.";
        String FAKE_CONFIG = "Marker that defines this config as not beeing a real config, but used in e.g. testing.";
    }

     class DescriptionHelper {

         protected static final String PROB_OF = "A number between 0 and 1 that represents the probability of ";
     }
}
