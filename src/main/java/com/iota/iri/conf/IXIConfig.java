package com.iota.iri.conf;

/**
 * Configurations for IXI modules
 */
public interface IXIConfig extends Config {

    String IXI_DIR = "ixi";

    /**
     * @return Descriptions#IXI_DIR
     */
    String getIxiDir();

    interface Descriptions {
        String IXI_DIR = "The folder where ixi modules should be added for automatic discovery by IRI.";
    }
}
