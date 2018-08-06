package com.iota.iri.conf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.iota.iri.IRI;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigUtils {

    public static IotaConfig parseFromArgs(String[] args, IotaConfig iotaConfig) throws ParameterException {
        if (ArrayUtils.isNotEmpty(args)) {
            JCommander jCommander = JCommander.newBuilder()
                    .addObject(iotaConfig)
                    //This is in order to enable the `--conf` and `--testnet` option
                    .acceptUnknownOptions(true)
                    .allowParameterOverwriting(true)
                    //This is the first line of JCommander Usage
                    .programName("java -jar iri-" + IRI.VERSION + ".jar")
                    .build();
            jCommander.parse(args);
            if (iotaConfig.isHelp()) {
                jCommander.usage();
                System.exit(0);
            }
        }
        return iotaConfig;
    }

    public static IotaConfig createIotaConfig(boolean isTestnet) {
        IotaConfig iotaConfig;
        if (isTestnet) {
            iotaConfig = new TestnetConfig();
        }
        else {
            iotaConfig = new MainnetConfig();
        }
        return iotaConfig;
    }

    public static IotaConfig createFromFile(File configFile, boolean testnet) throws IOException,
            IllegalArgumentException {
        IotaConfig iotaConfig;

        try (FileInputStream confStream = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(confStream);
            boolean isTestnet = testnet || Boolean.parseBoolean(props.getProperty("TESTNET", "false"));
            Class<? extends IotaConfig> iotaConfigClass = isTestnet ? TestnetConfig.class : MainnetConfig.class;
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
            objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
            iotaConfig = objectMapper.convertValue(props, iotaConfigClass);
        }
        return iotaConfig;
    }

}
