package com.iota.iri;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.model.Hash;
import com.iota.iri.service.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Main IOTA Reference Implementation starting class
 */
public class IRI {

    private static final Logger log = LoggerFactory.getLogger(IRI.class);

    public static final Hash MAINNET_COORDINATOR = new Hash("KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU");
    public static final Hash TESTNET_COORDINATOR = new Hash("XNZBYAST9BETSDNOVQKKTBECYIPMF9IPOZRWUPFQGVH9HJW9NDSQVIPVBWU9YKECRYGDSJXYMZGHZDXCA");

    public static final String MAINNET_NAME = "IRI";
    public static final String TESTNET_NAME = "IRI Testnet";
    public static final String VERSION = "1.4.1.2";
    public static Iota iota;
    public static API api;
    public static IXI ixi;
    public static Configuration configuration;

    public static void main(final String[] args) throws IOException {
        configuration = new Configuration();
        validateParams(configuration);
        log.info("Welcome to {} {}", configuration.booling(DefaultConfSettings.TESTNET) ? TESTNET_NAME : MAINNET_NAME, VERSION);
        iota = new Iota(configuration);
        ixi = new IXI(iota);
        api = new API(iota, ixi);
        shutdownHook();

        if (configuration.booling(DefaultConfSettings.DEBUG)) {
            log.info("You have set the debug flag. To enable debug output, you need to uncomment the DEBUG appender in the source tree at iri/src/main/resources/logback.xml and re-package iri.jar");
        }

        if (configuration.booling(DefaultConfSettings.EXPORT)) {
            File exportDir = new File("export");
            // if the directory does not exist, create it
            if (!exportDir.exists()) {
                log.info("Create directory 'export'");
                try {
                    exportDir.mkdir();
                } catch (SecurityException e) {
                    log.error("Could not create directory", e);
                }
            }
            exportDir = new File("export-solid");
            // if the directory does not exist, create it
            if (!exportDir.exists()) {
                log.info("Create directory 'export-solid'");
                try {
                    exportDir.mkdir();
                } catch (SecurityException e) {
                    log.error("Could not create directory", e);
                }
            }
        }

        try {
            iota.init();
            api.init();
            ixi.init(configuration.string(Configuration.DefaultConfSettings.IXI_DIR));
        } catch (final Exception e) {
            log.error("Exception during IOTA node initialisation: ", e);
            System.exit(-1);
        }
        log.info("IOTA Node initialised correctly.");
    }

    private static void validateParams(final Configuration configuration) throws IOException {

        // mandatory args
        if (!configuration.has(DefaultConfSettings.PORT)) {
            log.error("Provide at least the PORT in application.conf, as environment variable or with -DPORT option");
            printUsage();
        }

        if (!configuration.has(DefaultConfSettings.NEIGHBORS)) {
            log.warn("No neighbor has been specified. Server starting nodeless.");
        }

        if (configuration.has(DefaultConfSettings.REMOTE_LIMIT_API)) {
            log.debug("The following api calls are not allowed : {} ", configuration.string(DefaultConfSettings.REMOTE_LIMIT_API));
        }

        if (configuration.has(DefaultConfSettings.REMOTE_AUTH)) {
            log.debug("Remote access requires basic authentication");
        }

        if (configuration.has(DefaultConfSettings.API_HOST)) {
            log.info("Remote access enabled. Binding API socket to listen any interface.");
        }

        if (configuration.has(DefaultConfSettings.EXPORT)) {
            log.info("Export transaction trytes turned on.");
        }

        if (configuration.integer(DefaultConfSettings.PORT) < 1024) {
            log.warn("Warning: api port value seems too low.");
        }

        // TODO should be refactored to support ENVIRONMENTS
        if (configuration.has(DefaultConfSettings.DEBUG) && configuration.booling(DefaultConfSettings.DEBUG)) {
            StatusPrinter.print((LoggerContext) LoggerFactory.getILoggerFactory());
        }

        // TODO this should be refactored to support different TESTNET configuration file
        // configuration within code (hardcoded) isn't good practice
        if (configuration.has(DefaultConfSettings.TESTNET) && configuration.booling(DefaultConfSettings.TESTNET)) {
            configuration.put(DefaultConfSettings.DB_PATH.name(), "testnetdb");
            configuration.put(DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
        }
    }

    private static void printUsage() {
        log.info("Usage: java -jar {}-{}.jar " +
                        "[{-DNEIGHBOURS} '<list of neighbors>'] " +
                        "[{-DPORT} 14600] " +
                        "[{-Dconfig.file} 'config-file-name'] " +
                        "[{-DUDP_RECEIVER_PORT} 14600] " +
                        "[{-DTCP_RECEIVER_PORT} 15600] " +
                        "[{-DDEBUG} false] " +
                        "[{-DTESTNET} false]" +
                        "[{-DREMOTE} false]" +
                        "[{-DREMOTE_AUTH} string]" +
                        "[{-DREMOTE_LIMIT_API} string]"
                , MAINNET_NAME, VERSION);
        System.exit(0);
    }

    private static void shutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            log.info("Shutting down IOTA node, please hold tight...");
            try {
                ixi.shutdown();
                api.shutDown();
                iota.shutdown();
            } catch (final Exception e) {
                log.error("Exception occurred shutting down IOTA node: ", e);
            }
        }, "Shutdown Hook"));
    }
}
