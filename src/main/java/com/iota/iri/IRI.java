package com.iota.iri;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.iota.iri.model.Hash;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.tangle.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.service.viewModels.TransactionRequester;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.service.API;
import com.iota.iri.service.Node;
import com.iota.iri.service.TipsManager;
import com.iota.iri.service.replicator.Replicator;
import com.iota.iri.service.replicator.ReplicatorSinkPool;
import com.iota.iri.service.replicator.ReplicatorSourcePool;
import com.sanityinc.jargs.CmdLineParser;
import com.sanityinc.jargs.CmdLineParser.Option;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;

/**
 * Main IOTA Reference Implementation starting class
 */
public class IRI {

    private static final Logger log = LoggerFactory.getLogger(IRI.class);

    public static final Hash MAINNET_COORDINATOR = new Hash("KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU");
    public static final Hash TESTNET_COORDINATOR = new Hash("XNZBYAST9BETSDNOVQKKTBECYIPMF9IPOZRWUPFQGVH9HJW9NDSQVIPVBWU9YKECRYGDSJXYMZGHZDXCA");

    public static final String MAINNET_NAME = "IRI";
    public static final String TESTNET_NAME = "IRI Testnet";
    public static final String VERSION = "1.1.3.6";

    public static void main(final String[] args) throws IOException {

        log.info("Welcome to {} {}", MAINNET_NAME, VERSION);
        validateParams(args);
        shutdownHook();

        if (!Configuration.booling(DefaultConfSettings.HEADLESS)) {
            showIotaLogo();
        }
        
        if (Configuration.booling(DefaultConfSettings.EXPORT)) {
            File exportDir = new File("export");

            // if the directory does not exist, create it
            if (!exportDir.exists()) {
                log.info("Create directory 'export'");
                try {
                    exportDir.mkdir();
                } catch (SecurityException e) {
                    log.error("Could not create directory",e);
                }
            }
        }

        try {
            if (Configuration.booling(Configuration.DefaultConfSettings.TESTNET)) {
                Milestone.init(TESTNET_COORDINATOR, true);
            } else {
                Milestone.init(MAINNET_COORDINATOR, false);
            }
            TransactionViewModel.init();
            Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
            Tangle.instance().init();
            TipsManager.instance().init();
            TransactionRequester.instance().init();
            Node.instance().init();
            API.instance().init();
            Replicator.instance().init();
            //IXI.init();

        } catch (final Exception e) {
            log.error("Exception during IOTA node initialisation: ", e);
            System.exit(-1);
        }
        log.info("IOTA Node initialised correctly.");
    }

    private static void validateParams(final String[] args) throws IOException {

        boolean configurationInit = Configuration.init();
        
        if (args == null || (args.length < 2 && !configurationInit)) {
            log.error("Invalid arguments list. Provide ini-file 'iota.ini' or API port number (i.e. '-p 14265').");
            printUsage();
        }

        final CmdLineParser parser = new CmdLineParser();

        final Option<String> config = parser.addStringOption('c', "conf");
        final Option<String> port = parser.addStringOption('p', "port");
        final Option<String> rportudp = parser.addStringOption('r', "receiver-port-udp");
        final Option<String> rporttcp = parser.addStringOption('r', "receiver-port-tcp");
        final Option<String> cors = parser.addStringOption('c', "enabled-cors");
        final Option<Boolean> headless = parser.addBooleanOption("headless");
        final Option<Boolean> debug = parser.addBooleanOption('d', "debug");
        final Option<Boolean> remote = parser.addBooleanOption("remote");
        final Option<String> remoteLimitApi = parser.addStringOption("remote-limit-api");
        final Option<String> neighbors = parser.addStringOption('n', "neighbors");
        final Option<Boolean> export = parser.addBooleanOption('e', "export");
        final Option<Boolean> help = parser.addBooleanOption('h', "help");
        final Option<Integer> ratingThr = parser.addIntegerOption('x', "rating-threshold");
        final Option<Integer> artLatency = parser.addIntegerOption('a', "art-latency");
        final Option<Long> timestampThreshold = parser.addLongOption('t', "timestamp-threshold");
        final Option<Boolean> testnet = parser.addBooleanOption('t', "testnet");

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
        if(confFilePath != null ) {
            Configuration.put(DefaultConfSettings.CONF_PATH, confFilePath);
            Configuration.init();
        }

        // mandatory args
        String inicport = Configuration.getIniValue(DefaultConfSettings.API_PORT.name());
        final String cport = inicport == null ? parser.getOptionValue(port) : inicport;
        if (cport == null) {
            log.error("Invalid arguments list. Provide at least the API_PORT in iota.ini or with -p option");
            printUsage();
        }
        else {
            Configuration.put(DefaultConfSettings.API_PORT, cport);
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
        Configuration.put(DefaultConfSettings.NEIGHBORS, cns);


        final String vcors = parser.getOptionValue(cors);
        if (vcors != null) {
            log.debug("Enabled CORS with value : {} ", vcors);
            Configuration.put(DefaultConfSettings.CORS_ENABLED, vcors);
        }

        final String vremoteapilimit = parser.getOptionValue(remoteLimitApi);
        if (vremoteapilimit != null) {
            log.debug("The following api calls are not allowed : {} ", vremoteapilimit);
            Configuration.put(DefaultConfSettings.REMOTEAPILIMIT, vremoteapilimit);
        }

        final String vrportudp = parser.getOptionValue(rportudp);
        if (vrportudp != null) {
            Configuration.put(DefaultConfSettings.TANGLE_RECEIVER_PORT_UDP, vrportudp);
        }
        
        final String vrporttcp = parser.getOptionValue(rporttcp);
        if (vrporttcp != null) {
            Configuration.put(DefaultConfSettings.TANGLE_RECEIVER_PORT_UDP, vrporttcp);
        }

        if (parser.getOptionValue(headless) != null) {
            Configuration.put(DefaultConfSettings.HEADLESS, "true");
        }

        if (parser.getOptionValue(remote) != null) {
            log.info("Remote access enabled. Binding API socket to listen any interface.");
            Configuration.put(DefaultConfSettings.API_HOST, "0.0.0.0");
        }

        if (parser.getOptionValue(export) != null) {
            log.info("Export transaction trytes turned on.");
            Configuration.put(DefaultConfSettings.EXPORT, "true");
        }

        if (Integer.parseInt(cport) < 1024) {
            log.warn("Warning: api port value seems too low.");
        }

        if (parser.getOptionValue(debug) != null) {
            Configuration.put(DefaultConfSettings.DEBUG, "true");
            log.info(Configuration.allSettings());
            StatusPrinter.print((LoggerContext) LoggerFactory.getILoggerFactory());
        }
        
        final Integer ratingThreshold = parser.getOptionValue(ratingThr);
        if (ratingThreshold != null) {
            log.info("Rating Threshold for TipManager is set to {}.",ratingThreshold);
            TipsManager.setRATING_THRESHOLD(ratingThreshold);
        }
        
        final Integer aLatency = parser.getOptionValue(artLatency);
        if (aLatency != null) {
            log.info("Artifical Latency for milestone updater is set to {}.",aLatency);
            TipsManager.setARTIFICAL_LATENCY(aLatency);
        }
        
        final Long ts = parser.getOptionValue(timestampThreshold);
        if (ts != null) {
            log.info("Timestamp threshold is set to {}.",ts);
            Node.setTIMESTAMP_THRESHOLD(ts);
        }
        
        if (parser.getOptionValue(testnet) != null) {
            Configuration.put(DefaultConfSettings.TESTNET, "true");
            Configuration.put(DefaultConfSettings.DB_PATH.name(), "testnetdb");
            Configuration.put(DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
        }
        
    }

    private static void printUsage() {
        log.info("Usage: java -jar {}-{}.jar " +
                "[{-p,--port} 14265] " +
                "[{-r,--receiver-port} 14265] " +
                "[{-c,--enabled-cors} *] " +
                "[{-h}] [{--headless}] " +
                "[{-d,--debug}] " +
                "[{-e,--export}]" +
                "[{-t,--testnet}]" +
                "[{--remote}]" +
                "[{-t,--testnet} false] " +
                "[{-n,--neighbors} '<list of neighbors>'] ", MAINNET_NAME, VERSION);
        System.exit(0);
    }

    private static void shutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            log.info("Shutting down IOTA node, please hold tight...");
            try {
                //IXI.shutdown();
                API.instance().shutDown();
                TipsManager.instance().shutDown();
                Node.instance().shutdown();
                ReplicatorSourcePool.instance().shutdown();
                ReplicatorSinkPool.instance().shutdown();
                Tangle.instance().shutdown();
            } catch (final Exception e) {
                log.error("Exception occurred shutting down IOTA node: ", e);
            }
        }, "Shutdown Hook"));
    }

    private static void showIotaLogo() {
        final String charset = "UTF8";

        try {
            final Path path = Paths.get("logo.utf8.ans");
            Files.readAllLines(path, Charset.forName(charset)).forEach(log::info);
        } catch (IOException e) {
            log.error("Impossible to display logo. Charset {} not supported by terminal.", charset);
        }
    }
}