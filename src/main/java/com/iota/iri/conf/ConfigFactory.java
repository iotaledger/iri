package com.iota.iri.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.iota.iri.conf.deserializers.CustomBoolDeserializer;
import com.iota.iri.conf.deserializers.CustomStringDeserializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Creates the global {@link IotaConfig} object with iri specific settings.
 */
public class ConfigFactory {

    /**
     * Creates the {@link IotaConfig} object for {@link TestnetConfig} or {@link MainnetConfig}.
     *
     * @param isTestnet true if {@link TestnetConfig} should be created.
     * @return return the {@link IotaConfig} configuration.
     */
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

    /**
     * Creates the {@link IotaConfig} object for {@link TestnetConfig} or {@link MainnetConfig} from config file. Parse
     * the config file for <code>TESTNET=true</code>. If <code>TESTNET=true</code> is found we creates the
     * {@link TestnetConfig} object, else creates the {@link MainnetConfig}.
     *
     * @param configFile A property file with configuration options.
     * @param testnet When true a {@link TestnetConfig} is created.
     * @return the {@link IotaConfig} configuration.
     *
     * @throws IOException When config file could not be found.
     */
    public static IotaConfig createFromFile(File configFile, boolean testnet) throws IOException {
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
            objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

            SimpleModule booleanParser = new SimpleModule("BooleanParser");
            booleanParser.addDeserializer(Boolean.TYPE, new CustomBoolDeserializer());
            objectMapper.registerModule(booleanParser);

            SimpleModule stringParser = new SimpleModule("StringParser");
            stringParser.addDeserializer(String.class, new CustomStringDeserializer());
            objectMapper.registerModule(stringParser);

            iotaConfig = objectMapper.convertValue(props, iotaConfigClass);
        }
        return iotaConfig;
    }

}
