package com.iota.iri.conf;

public interface IXIConfig extends Config {

    String IXI_DIR = "ixi";
    String getIxiDir();

    interface Descriptions {
        String IXI_DIR = "The folder where ixi modules should be added for automatic discovery by IRI.";
    }
}
