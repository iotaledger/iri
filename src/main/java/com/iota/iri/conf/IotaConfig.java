package com.iota.iri.conf;

import java.io.File;

public interface IotaConfig extends APIConfig, NodeConfig,
        IXIConfig, DbConfig, ConsensusConfig, ZMQConfig, TipSelConfig {
    File CONFIG_FILE = new File("iota.ini");

    boolean isHelp();
}
