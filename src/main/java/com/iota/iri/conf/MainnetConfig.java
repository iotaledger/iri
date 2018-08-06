package com.iota.iri.conf;

public class MainnetConfig extends NetConfig {

    public MainnetConfig() {
        //All the configs are defined in the super class
        super();
    }

    @Override
    public boolean isTestnet() {
        return false;
    }
}
