package com.iota.iri.conf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import java.io.File;

public interface IotaConfig extends APIConfig, NodeConfig,
        IXIConfig, DbConfig, ConsensusConfig, ZMQConfig, TipSelConfig {
    File CONFIG_FILE = new File("iota.ini");

    /**
     * Parses the args to populate the configuration object
     *
     * @param args command line args
     * @return Jcommander instance that was used for parsing. It contains metadata about the parsing.
     * @throws ParameterException if the parsing failed
     */
    JCommander parseConfigFromArgs(String[] args) throws ParameterException;

    boolean isHelp();
}
