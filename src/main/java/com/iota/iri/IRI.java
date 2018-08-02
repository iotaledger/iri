package com.iota.iri;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.service.API;
import com.sanityinc.jargs.CmdLineParser;
import com.sanityinc.jargs.CmdLineParser.Option;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Main IOTA Reference Implementation starting class.
 */
public class IRI {

    public static final String MAINNET_NAME = "IRI";
    public static final String TESTNET_NAME = "IRI Testnet";
    public static final String VERSION = "1.5.3";

    public static void main(String[] args) throws Exception {
        // Logging is configured first before any references to Logger or LoggerFactory.
        // Any public method or field accessors needed in IRI should be put in IRI and then delegate to IRILauncher. That
        // ensures that future code does not need to know about this setup.
        configureLogging();
        IRILauncher.main(args);
    }

    private static void configureLogging() {
        String config = System.getProperty("logback.configurationFile");
        String level = System.getProperty("logging-level", "").toUpperCase();
        switch (level) {
            case "OFF":
            case "ERROR":
            case "WARN":
            case "INFO":
            case "DEBUG":
            case "TRACE":
                break;
            case "ALL":
                level = "TRACE";
                break;
            default:
                level = "INFO";
        }
        System.getProperties().put("logging-level", level);
        System.out.println("Logging - property 'logging-level' set to: [" + level + "]");
        if (config != null) {
            System.out.println("Logging - alternate logging configuration file specified at: '" + config + "'");
        }
    }

    private static class IRILauncher {
        private static final Logger log = LoggerFactory.getLogger(IRILauncher.class);

        public static Iota iota;
        public static API api;
        public static IXI ixi;
        public static Configuration configuration;

        private static final String TESTNET_FLAG_REQUIRED = "--testnet flag must be turned on to use ";

        public static void main(final String[] args) throws Exception {

            configuration = new Configuration();
            if (!isValidated(configuration, args)) {
                printUsage();
                return;
            }

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
                if (!exportDir.exists()) {
                    log.info("Create directory 'export'");
                    try {
                        exportDir.mkdir();
                    } catch (SecurityException e) {
                        log.error("Could not create directory", e);
                    }
                }
                exportDir = new File("export-solid");
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
                log.info("IOTA Node initialised correctly.");
            } catch (Exception e) {
                log.error("Exception during IOTA node initialisation: ", e);
                throw e;
            }
        }

        private static boolean isValidated(final Configuration configuration, final String[] args) throws IOException {

            boolean configurationInit = configuration.init();

            if (args == null || (args.length < 2 && !configurationInit)) {
                log.error("Invalid arguments list. Provide ini-file 'iota.ini' or API port number (i.e. '-p 14600').");
                return false;
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
            final Option<Boolean> zmq = parser.addBooleanOption("zmq-enabled");
            final Option<Boolean> help = parser.addBooleanOption('h', "help");
            final Option<Boolean> testnet = parser.addBooleanOption("testnet");
            final Option<Boolean> revalidate = parser.addBooleanOption("revalidate");
            final Option<Boolean> rescan = parser.addBooleanOption("rescan");
            final Option<String> sendLimit = parser.addStringOption("send-limit");
            final Option<Boolean> sync = parser.addBooleanOption("sync");
            final Option<Boolean> dnsResolutionFalse = parser.addBooleanOption("dns-resolution-false");
            final Option<String> maxPeers = parser.addStringOption("max-peers");
            final Option<String> testnetCoordinator = parser.addStringOption("testnet-coordinator");
            final Option<Boolean> disableCooValidation = parser.addBooleanOption("testnet-no-coo-validation");
            final Option<String> snapshot = parser.addStringOption("snapshot");
            final Option<String> snapshotSignature = parser.addStringOption("snapshot-sig");
            final Option<Integer> minimalWeightMagnitude = parser.addIntegerOption("mwm");
            final Option<Integer> milestoneStartIndex = parser.addIntegerOption("milestone-start");
            final Option<Integer> milestoneKeys = parser.addIntegerOption("milestone-keys");
            final Option<Long> snapshotTime = parser.addLongOption("snapshot-timestamp");
            final Option<Integer> belowMaxDepthTxLimit = parser.addIntegerOption("max-depth-tx-limit");

            try {
                parser.parse(args);
            } catch (CmdLineParser.OptionException e) {
                log.error("CLI error: ", e);
                throw new IllegalArgumentException("CLI error: " + e, e);
            }

            // optional config file path
            String confFilePath = parser.getOptionValue(config);
            if (confFilePath != null) {
                configuration.put(DefaultConfSettings.CONFIG, confFilePath);
                configuration.init();
            }

            //This block cannot be moved down
            final boolean isTestnet = Optional.ofNullable(parser.getOptionValue(testnet)).orElse(Boolean.FALSE)
                || configuration.booling(DefaultConfSettings.TESTNET);
            if (isTestnet) {
                configuration.put(DefaultConfSettings.TESTNET, "true");
                configuration.put(DefaultConfSettings.DB_PATH.name(), "testnetdb");
                configuration.put(DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
                configuration.put(DefaultConfSettings.COORDINATOR, Configuration.TESTNET_COORDINATOR_ADDRESS);
                configuration.put(DefaultConfSettings.SNAPSHOT_FILE, Configuration.TESTNET_SNAPSHOT_FILE);
                configuration.put(DefaultConfSettings.MILESTONE_START_INDEX, Configuration.TESTNET_MILESTONE_START_INDEX);
                configuration.put(DefaultConfSettings.SNAPSHOT_SIGNATURE_FILE, "");
                configuration.put(DefaultConfSettings.MWM, Configuration.TESTNET_MWM);
                configuration.put(DefaultConfSettings.NUMBER_OF_KEYS_IN_A_MILESTONE,
                    Configuration.TESTNET_NUM_KEYS_IN_MILESTONE);
                configuration.put(DefaultConfSettings.TRANSACTION_PACKET_SIZE, Configuration.TESTNET_PACKET_SIZE);
                configuration.put(DefaultConfSettings.REQUEST_HASH_SIZE, Configuration.TESTNET_REQ_HASH_SIZE);
                configuration.put(DefaultConfSettings.SNAPSHOT_TIME, Configuration.TESTNET_GLOBAL_SNAPSHOT_TIME);
            }

            // mandatory args
            String inicport = configuration.getIniValue(DefaultConfSettings.PORT.name());
            final String cport = inicport == null ? parser.getOptionValue(port) : inicport;
            if (cport == null) {
                log.error("Invalid arguments list. Provide at least the PORT in iota.ini or with -p option");
                return false;
            } else {
                configuration.put(DefaultConfSettings.PORT, cport);
            }

            // optional flags
            if (parser.getOptionValue(help) != null) {
                return false;
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

            if (parser.getOptionValue(zmq) != null) {
                configuration.put(DefaultConfSettings.ZMQ_ENABLED, "true");
            }

            if (Integer.parseInt(cport) < 1024) {
                log.warn("Warning: api port value seems too low.");
            }

            if (parser.getOptionValue(debug) != null) {
                configuration.put(DefaultConfSettings.DEBUG, "true");
                log.info(configuration.allSettings());
                StatusPrinter.print((LoggerContext) LoggerFactory.getILoggerFactory());
            }


            final String coordinatorAddress = parser.getOptionValue(testnetCoordinator);
            if (coordinatorAddress != null) {
                if (isTestnet) {
                    configuration.put(DefaultConfSettings.COORDINATOR, coordinatorAddress);
                } else {
                    log.warn(TESTNET_FLAG_REQUIRED + testnetCoordinator.longForm());
                }
            }

            final Boolean noCooValidation = parser.getOptionValue(disableCooValidation);
            if (noCooValidation != null) {
                if (isTestnet) {
                    configuration.put(DefaultConfSettings.DONT_VALIDATE_TESTNET_MILESTONE_SIG, noCooValidation.toString());
                } else {
                    log.warn(TESTNET_FLAG_REQUIRED + noCooValidation);
                }
            }

            //TODO check what happens if string is invalid int
            final Integer mwm = parser.getOptionValue(minimalWeightMagnitude);
            if (mwm != null) {
                configuration.put(DefaultConfSettings.MWM, String.valueOf(mwm));
            }

            final String snapshotFile = parser.getOptionValue(snapshot);
            if (snapshotFile != null) {
                configuration.put(DefaultConfSettings.SNAPSHOT_FILE, snapshotFile);
            }

            final String snapshotSig = parser.getOptionValue(snapshotSignature);
            if (snapshotSig != null) {
                configuration.put(DefaultConfSettings.SNAPSHOT_SIGNATURE_FILE, snapshotSig);
            }

            final Integer milestoneStart = parser.getOptionValue(milestoneStartIndex);
            if (milestoneStart != null) {
                configuration.put(DefaultConfSettings.MILESTONE_START_INDEX, String.valueOf(milestoneStart));
            }

            final Integer numberOfKeys = parser.getOptionValue(milestoneKeys);
            if (numberOfKeys != null) {
                configuration.put(DefaultConfSettings.NUMBER_OF_KEYS_IN_A_MILESTONE, String.valueOf(numberOfKeys));
            }

            final Long snapshotTimestamp = parser.getOptionValue(snapshotTime);
            if (snapshotTimestamp != null) {
                configuration.put(DefaultConfSettings.SNAPSHOT_TIME, String.valueOf(snapshotTimestamp));
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

            return true;
        }

        private static void printUsage() {
            log.info("Usage: java -jar {}-{}.jar " +
                    "[{-n,--neighbors} '<list of neighbors>'] " +
                    "[{-p,--port} 14265] " +
                    "[{-c,--config} 'config-file-name'] " +
                    "[{-u,--udp-receiver-port} 14600] " +
                    "[{-t,--tcp-receiver-port} 15600] " +
                    "[{-d,--debug} false] " +
                    "[{--testnet} false]" +
                    "[{--remote} false]" +
                    "[{--remote-auth} string]" +
                    "[{--remote-limit-api} string]"
                , MAINNET_NAME, VERSION);
        }

        private static void shutdownHook() {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down IOTA node, please hold tight...");
                try {
                    ixi.shutdown();
                    api.shutDown();
                    iota.shutdown();
                } catch (Exception e) {
                    log.error("Exception occurred shutting down IOTA node: ", e);
                }
            }, "Shutdown Hook"));
        }
    }

    public static boolean validateParams(Configuration configuration, String[] args) throws IOException {
        return IRILauncher.isValidated(configuration, args);
    }
}
