package com.iota.iri.conf;

/**
 * Configurations for IXI modules
 */
public interface IXIConfig extends Config {

    String IXI_DIR = "ixi";

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#IXI_DIR}
     *
     * @return {@value IXIConfig.Descriptions#IXI_DIR}
     */
    String getIxiDir();

    interface Descriptions {
        String IXI_DIR = "The folder where ixi modules should be added for automatic discovery by IRI.";
    }
}
