package com.iota.iri.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.iota.iri.conf.IotaConfig;

/**
 * 
 * Features describe the capability of the node. 
 * They do not have to have a name specifically linked to a class/action. 
 * Features are based on enabled/disabled functionality due to, for example, config values
 *
 */
public enum Feature {
    /**
     * This node allows doing proof of work for you.
     */
    PROOF_OF_WORK("RemotePOW"),
    
    /**
     * This node has enabled snapshot pruning
     * It will most likely just contain "recent" transactions, as defined in the nodes config
     */
    SNAPSHOT_PRUNING("snapshotPruning"),
    
    /**
     * This node automatically tries to get the new IP from neighbors with dynamic IPs
     * 
     */
    DNS_REFRESHER("dnsRefresher"),
    
    /**
     * This node is connected to the testnet instead of the mainnet tangle
     */
    TESTNET("testnet"),
    
    /**
     * This node has the zero message queue enabled for fetching/reading "activities" on the node
     */
    ZMQ("zeroMessageQueue");
    
    private String name;

    Feature(String name) {
        this.name = name;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    /**
     * Calculates all features for this Iota node
     * @param instance the instance of this node
     * @return An array of features
     */
    public static Feature[] calculateFeatures(IotaConfig configuration) {
        List<Feature> features = new ArrayList<>();
        
        if (configuration.getLocalSnapshotsPruningEnabled()) {
            features.add(SNAPSHOT_PRUNING);
        }
        if (configuration.isDnsRefresherEnabled()) {
            features.add(DNS_REFRESHER);
        }
        if (configuration.isTestnet()) {
            features.add(TESTNET);
        }
        if (configuration.isZmqEnabled()) {
            features.add(ZMQ);
        }
        
        List<Feature> apiFeatures = new ArrayList<Feature>(Arrays.asList(new Feature[] {
                PROOF_OF_WORK
        }));
        
        for (String disabled : configuration.getRemoteLimitApi()) {
            switch (disabled) {
            case "attachToTangle":
                apiFeatures.remove(PROOF_OF_WORK);
                break;

            default:
                break;
            }
        }
        features.addAll(apiFeatures);
        
        return features.toArray(new Feature[]{});
    }
    
    /**
     * Calculates all features for this Iota node
     * @param instance the instance of this node
     * @return An array of the features in readable name format
     */
    public static String[] calculateFeatureNames(IotaConfig configuration) {
        Feature[] features = calculateFeatures(configuration);
        
        String[] featureNames = Arrays.stream(features)
                .map(feature -> feature.toString())
                .toArray(String[]::new);
        
        return featureNames;
    }
}
