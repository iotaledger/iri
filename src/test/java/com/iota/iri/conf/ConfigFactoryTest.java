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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests for the {@link ConfigFactory}
 */
public class ConfigFactoryTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * Creates and validates a Testnet {@link IotaConfig}.
     */
    @Test
    public void createIotaConfigTestnet() {
        IotaConfig iotaConfig = ConfigFactory.createIotaConfig(true);
        assertTrue("Expected iotaConfig as instance of TestnetConfig.", iotaConfig instanceof TestnetConfig);
        assertTrue("Expected iotaConfig as Testnet.", iotaConfig.isTestnet());
    }

    /**
     * Creates and validates a Mainnet {@link IotaConfig}.
     */
    @Test
    public void createIotaConfigMainnet() {
        IotaConfig iotaConfig = ConfigFactory.createIotaConfig(false);
        assertTrue("Expected iotaConfig as instance of MainnetConfig.", iotaConfig instanceof MainnetConfig);
        assertFalse("Expected iotaConfig as Mainnet.", iotaConfig.isTestnet());
    }

    /**
     * Creates and validates a Testnet {@link IotaConfig} with <code>TESTNET=true</code> in config file and
     * <code>testnet: false</code> as method parameter for {@link ConfigFactory#createFromFile(File, boolean)}.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTestnetTrueAndFalse() throws IOException {
        // lets assume in our configFile is TESTNET=true
        File configFile = createTestnetConfigFile("true");

        // but the parameter is set to testnet=false
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, false);
        assertTrue("Expected iotaConfig as instance of TestnetConfig.", iotaConfig instanceof TestnetConfig);
        assertTrue("Expected iotaConfig as Testnet.", iotaConfig.isTestnet());
    }

    /**
     * Creates and validates a Testnet {@link IotaConfig} with <code>TESTNET=true</code> in config file and
     * <code>testnet: true</code> as method parameter for {@link ConfigFactory#createFromFile(File, boolean)}.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTestnetTrueAndTrue() throws IOException {
        // lets assume in our configFile is TESTNET=true
        File configFile = createTestnetConfigFile("true");

        // but the parameter is set to testnet=true
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, true);
        assertTrue("Expected iotaConfig as instance of TestnetConfig.", iotaConfig instanceof TestnetConfig);
        assertTrue("Expected iotaConfig as Testnet.", iotaConfig.isTestnet());
    }

    /**
     * Creates and validates a Testnet {@link IotaConfig} with <code>TESTNET=false</code> in config file and
     * <code>testnet: true</code> as method parameter for {@link ConfigFactory#createFromFile(File, boolean)}.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTestnetFalseAndTrue() throws IOException {
        // lets assume in our configFile is TESTNET=false
        File configFile = createTestnetConfigFile("false");

        // but the parameter is set to testnet=true
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, true);
        assertTrue("Expected iotaConfig as instance of TestnetConfig.", iotaConfig instanceof TestnetConfig);
        assertTrue("Expected iotaConfig as Testnet.", iotaConfig.isTestnet());
    }

    /**
     * Creates and validates a Mainnet {@link IotaConfig} with <code>TESTNET=false</code> in config file and
     * <code>testnet: false</code> as method parameter for {@link ConfigFactory#createFromFile(File, boolean)}.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTestnetFalseAndFalse() throws IOException {
        // lets assume in our configFile is TESTNET=false
        File configFile = createTestnetConfigFile("false");

        // but the parameter is set to testnet=true
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, false);
        assertTrue("Expected iotaConfig as instance of MainnetConfig.", iotaConfig instanceof MainnetConfig);
        assertFalse("Expected iotaConfig as Mainnet.", iotaConfig.isTestnet());
    }

    /**
     * Test if leading and trailing spaces are trimmed from string in properties file.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithTrailingSpaces() throws IOException {
        File configFile = createTestnetConfigFile("true");
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, true);
        String expected = "NPCRMHDOMU9QHFFBKFCWFHFJNNQDRNDOGVPEVDVGWKHFUFEXLWJBHXDJFKQGYFRDZBQIFDSJMUCCQVICI";
        assertEquals("Expected that leading and trailing spaces were trimmed.", expected, iotaConfig.getCoordinator());
    }

    /**
     * Test if trailing spaces are correctly trimmed from integer.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithInteger() throws IOException {
        File configFile = createTestnetConfigFile("true");
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, true);
        assertEquals("Expected that trailing spaces are trimmed.", 2, iotaConfig.getMilestoneStartIndex());
    }

    /**
     * Test if trailing spaces are correctly trimmed from boolean.
     * @throws IOException when config file not found.
     */
    @Test
    public void createFromFileTestnetWithBoolean() throws IOException {
        File configFile = createTestnetConfigFile("true");
        IotaConfig iotaConfig = ConfigFactory.createFromFile(configFile, true);
        assertTrue("Expected that ZMQ is enabled.", iotaConfig.isZmqEnabled());
    }

    /**
     * Try to create an {@link IotaConfig} from a not existing configFile.
     * @throws IOException when config file not found.
     */
    @Test(expected = FileNotFoundException.class)
    public void createFromFileTestnetWithFileNotFound() throws IOException {
        File configFile = new File("doesNotExist.ini");
        ConfigFactory.createFromFile(configFile, false);
    }

    private File createTestnetConfigFile(String testnet) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("TESTNET", testnet);
        properties.setProperty("ZMQ_ENABLED", " TRUE ");
        properties.setProperty("MWM", "9");
        properties.setProperty("SNAPSHOT_FILE", "conf/snapshot.txt");
        properties.setProperty("COORDINATOR", "  NPCRMHDOMU9QHFFBKFCWFHFJNNQDRNDOGVPEVDVGWKHFUFEXLWJBHXDJFKQGYFRDZBQIFDSJMUCCQVICI ");
        properties.setProperty("MILESTONE_START_INDEX", "2 ");
        properties.setProperty("KEYS_IN_MILESTONE", "10");
        properties.setProperty("MAX_DEPTH", "1000");

        File configFile = folder.newFile("myCustomIotaConfig.ini");
        FileOutputStream fileOutputStream = new FileOutputStream(configFile);
        properties.store(fileOutputStream, "Test config file created by Unit test!");
        fileOutputStream.close();

        return configFile;
    }
}