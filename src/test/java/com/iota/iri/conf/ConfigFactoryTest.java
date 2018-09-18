package com.iota.iri.conf;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

public class ConfigFactoryTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void createIotaConfigTestnet() {
        IotaConfig iotaConfig = ConfigFactory.createIotaConfig(true);
        assertTrue(iotaConfig instanceof TestnetConfig);
        assertTrue(iotaConfig.isTestnet());
    }

    @Test
    public void createIotaConfigMainnet() {
        IotaConfig iotaConfig = ConfigFactory.createIotaConfig(false);
        assertTrue(iotaConfig instanceof MainnetConfig);
        assertFalse(iotaConfig.isTestnet());
    }

    @Test
    public void createFromFileTestnetWithTestnetTrueAndFalse() throws IOException {
        // lets assume in our configFile is TESTNET=true
        File configFile = createTestnetConfigFile("true");

        // but the parameter is set to testnet=false
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, false);
        assertTrue(iotaConfig instanceof TestnetConfig);
        assertTrue(iotaConfig.isTestnet());
    }

    @Test
    public void createFromFileTestnetWithTestnetTrueAndTrue() throws IOException {
        // lets assume in our configFile is TESTNET=true
        File configFile = createTestnetConfigFile("true");

        // but the parameter is set to testnet=true
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, true);
        assertTrue(iotaConfig instanceof TestnetConfig);
        assertTrue(iotaConfig.isTestnet());
    }

    @Test
    public void createFromFileTestnetWithTestnetFalseAndTrue() throws IOException {
        // lets assume in our configFile is TESTNET=false
        File configFile = createTestnetConfigFile("false");

        // but the parameter is set to testnet=true
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, true);
        assertTrue(iotaConfig instanceof TestnetConfig);
        assertTrue(iotaConfig.isTestnet());
    }

    @Test
    public void createFromFileTestnetWithTestnetFalseAndFalse() throws IOException {
        // lets assume in our configFile is TESTNET=false
        File configFile = createTestnetConfigFile("false");

        // but the parameter is set to testnet=true
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, false);
        assertTrue(iotaConfig instanceof MainnetConfig);
        assertFalse(iotaConfig.isTestnet());
    }

    @Test(expected = FileNotFoundException.class)
    public void createFromFileTestnetWithFileNotFound() throws IOException {
        File configFile = new File("doesNotExist.ini");
        ConfigFactory.createFromFile(configFile, false);
    }

    private File createTestnetConfigFile(String testnet) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("TESTNET", testnet);
        File configFile = folder.newFile("myCustomIotaConfig.ini");
        FileOutputStream fileOutputStream = new FileOutputStream(configFile);
        properties.store(fileOutputStream, "Testconfig file created by Unit test!");
        fileOutputStream.close();
        return configFile;
    }
}