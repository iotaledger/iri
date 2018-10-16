package com.iota.iri.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.iota.iri.Iota;
import com.iota.iri.conf.IotaConfig;

public enum Feature {
    ATTACH_TO_TANGLE("attachToTange"),
    SNAPSHOT_PRUNING("snapshotPruning"),
    DNS_REFRESHER("dnsRefresher"),
    TESTNET("testnet"),
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
     * @return A list of features
     */
    public static Feature[] calculateFeatures(Iota instance) {
        List<Feature> features = new ArrayList<>();
        
        IotaConfig configuration = instance.configuration;
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
        
        List<Feature> apiFeatures = Arrays.asList(new Feature[] {
                ATTACH_TO_TANGLE
        });
        
        for (String disabled : configuration.getRemoteLimitApi()) {
            switch (disabled) {
            case "attachToTange":
                apiFeatures.remove(ATTACH_TO_TANGLE);
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
     * @return A list of the features in readable name format
     */
    public static String[] calculateFeatureNames(Iota instance) {
        Feature[] features = calculateFeatures(instance);
        
        List<String> featureNames = Arrays.stream(features)
                .map(feature -> feature.toString())
                .collect(Collectors.toList());
        
        return featureNames.toArray(new String[features.length]);
    }
}
