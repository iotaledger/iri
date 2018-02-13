package com.iota.iri;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.model.Hash;
import com.iota.iri.service.API;
import com.sanityinc.jargs.CmdLineParser;
import com.sanityinc.jargs.CmdLineParser.Option;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Main IOTA Reference Implementation starting class
 */
public class IRI {

    private static final Logger log = LoggerFactory.getLogger(IRI.class);

    public static final String MAINNET_NAME = "IRI";
    public static final String TESTNET_NAME = "IRI Testnet";
    public static final String VERSION = "1.4.2.2";
    public static Iota iota;
    public static API api;
    public static IXI ixi;
    public static Configuration configuration;

    public static void main(final String[] args) throws IOException {
        configuration = new Configuration();
        validateParams(configuration, args);
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

    private static void validateParams(final Configuration configuration, final String[] args) throws IOException {

        boolean configurationInit = configuration.init();

        if (args == null || (args.length < 2 && !configurationInit)) {
            log.error("Invalid arguments list. Provide ini-file 'iota.ini' or API port number (i.e. '-p 14600').");
            printUsage();
        }

        final CmdLineParser parser = new CmdLineParser();

        final Option<String> config = parser.addStringOption('c', "config");
        final Option<String> port = parser.addStringOption('p', "port");
        final Option<String> rportudp = parser.addStringOption('u', "udp-receiver-port");
        final Option<String> rporttcp = parser.addStringOption('t', "tcp-receiver-port");
        final Option<Boolean> debug = parser.addBooleanOption('d', "debug");
        final Option<Boolean> remote = parser.addBooleanOption("remote");
        final Option<String> remoteLimitApi = parser.addStringOption("remote-limit-api");
        final Option<String> remoteAuth = parser.addStringOption("remote-auth");
        final Option<String> neighbors = parser.addStringOption('n', "neighbors");
        final Option<Boolean> export = parser.addBooleanOption("export");
        final Option<Boolean> help = parser.addBooleanOption('h', "help");
        final Option<Boolean> testnet = parser.addBooleanOption("testnet");
        final Option<Boolean> revalidate = parser.addBooleanOption("revalidate");
        final Option<Boolean> rescan = parser.addBooleanOption("rescan");
        final Option<String> sendLimit = parser.addStringOption("send-limit");
        final Option<Boolean> sync = parser.addBooleanOption("sync");
        final Option<Boolean> dnsResolutionFalse = parser.addBooleanOption("dns-resolution-false");
        final Option<String> maxPeers = parser.addStringOption("max-peers");

        try {
            assert args != null;
            parser.parse(args);
        } catch (CmdLineParser.OptionException e) {
            log.error("CLI error: ", e);
            printUsage();
            System.exit(2);
        }

        // optional config file path
        String confFilePath = parser.getOptionValue(config);
        if (confFilePath != null) {
            configuration.put(DefaultConfSettings.CONFIG, confFilePath);
            configuration.init();
        }

        // mandatory args
        String inicport = configuration.getIniValue(DefaultConfSettings.PORT.name());
        final String cport = inicport == null ? parser.getOptionValue(port) : inicport;
        if (cport == null) {
            log.error("Invalid arguments list. Provide at least the PORT in iota.ini or with -p option");
            printUsage();
        } else {
            configuration.put(DefaultConfSettings.PORT, cport);
        }

        // optional flags
        if (parser.getOptionValue(help) != null) {
            printUsage();
        }


        String cns = parser.getOptionValue(neighbors);
        if (cns == null) {
            log.warn("No neighbor has been specified. Server starting nodeless.");
            cns = StringUtils.EMPTY;
        }
        configuration.put(DefaultConfSettings.NEIGHBORS, cns);

        final String vremoteapilimit = parser.getOptionValue(remoteLimitApi);
        if (vremoteapilimit != null) {
            log.debug("The following api calls are not allowed : {} ", vremoteapilimit);
            configuration.put(DefaultConfSettings.REMOTE_LIMIT_API, vremoteapilimit);
        }

        final String vremoteauth = parser.getOptionValue(remoteAuth);
        if (vremoteauth != null) {
            log.debug("Remote access requires basic authentication");
            configuration.put(DefaultConfSettings.REMOTE_AUTH, vremoteauth);
        }

        final String vrportudp = parser.getOptionValue(rportudp);
        if (vrportudp != null) {
            configuration.put(DefaultConfSettings.UDP_RECEIVER_PORT, vrportudp);
        }

        final String vrporttcp = parser.getOptionValue(rporttcp);
        if (vrporttcp != null) {
            configuration.put(DefaultConfSettings.TCP_RECEIVER_PORT, vrporttcp);
        }

        if (parser.getOptionValue(remote) != null) {
            log.info("Remote access enabled. Binding API socket to listen any interface.");
            configuration.put(DefaultConfSettings.API_HOST, "0.0.0.0");
        }

        if (parser.getOptionValue(export) != null) {
            log.info("Export transaction trytes turned on.");
            configuration.put(DefaultConfSettings.EXPORT, "true");
        }

        if (Integer.parseInt(cport) < 1024) {
            log.warn("Warning: api port value seems too low.");
        }

        if (parser.getOptionValue(debug) != null) {
            configuration.put(DefaultConfSettings.DEBUG, "true");
            log.info(configuration.allSettings());
            StatusPrinter.print((LoggerContext) LoggerFactory.getILoggerFactory());
        }

        if (parser.getOptionValue(testnet) != null) {
            configuration.put(DefaultConfSettings.TESTNET, "true");
            configuration.put(DefaultConfSettings.DB_PATH.name(), "testnetdb");
            configuration.put(DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
        }

        if (parser.getOptionValue(revalidate) != null) {
            configuration.put(DefaultConfSettings.REVALIDATE, "true");
        }

        if (parser.getOptionValue(rescan) != null) {
            configuration.put(DefaultConfSettings.RESCAN_DB, "true");
        }

        if (parser.getOptionValue(dnsResolutionFalse) != null) {
            configuration.put(DefaultConfSettings.DNS_RESOLUTION_ENABLED, "false");
        }


        final String vsendLimit = parser.getOptionValue(sendLimit);
        if (vsendLimit != null) {
            configuration.put(DefaultConfSettings.SEND_LIMIT, vsendLimit);
        }

        final String vmaxPeers = parser.getOptionValue(maxPeers);
        if (vmaxPeers != null) {
            configuration.put(DefaultConfSettings.MAX_PEERS, vmaxPeers);
        }
    }

    private static void printUsage() {
        log.info("Usage: java -jar {}-{}.jar " +
                        "[{-n,--neighbors} '<list of neighbors>'] " +
                        "[{-p,--port} 14600] " +
                        "[{-c,--config} 'config-file-name'] " +
                        "[{-u,--udp-receiver-port} 14600] " +
                        "[{-t,--tcp-receiver-port} 15600] " +
                        "[{-d,--debug} false] " +
                        "[{--testnet} false]" +
                        "[{--remote} false]" +
                        "[{--remote-auth} string]" +
                        "[{--remote-limit-api} string]"
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
