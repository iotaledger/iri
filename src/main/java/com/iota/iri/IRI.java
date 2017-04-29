package com.iota.iri;

import java.io.File;
import java.io.IOException;

import com.iota.iri.controllers.TransactionRequester;
import com.iota.iri.service.TipsManager;
import com.iota.iri.storage.FileExportProvider;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Node;
import com.iota.iri.network.UDPReceiver;
import com.iota.iri.network.replicator.Replicator;
import com.iota.iri.network.replicator.ReplicatorSinkPool;
import com.iota.iri.network.replicator.ReplicatorSourcePool;
import com.iota.iri.service.API;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
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
            exportDir = new File("export-solid");
            // if the directory does not exist, create it
            if (!exportDir.exists()) {
                log.info("Create directory 'export-solid'");
                try {
                    exportDir.mkdir();
                } catch (SecurityException e) {
                    log.error("Could not create directory",e);
                }
            }
        }

        try {
            String dbPath = Configuration.string(Configuration.DefaultConfSettings.DB_PATH);
            if (Configuration.booling(Configuration.DefaultConfSettings.TESTNET)) {
                Milestone.init(TESTNET_COORDINATOR, true);                
                if (dbPath.isEmpty() || dbPath.equals("mainnetdb")) {
                    // testnetusers must not use mainnetdb, overwrite it unless an explicit name is set.
                    Configuration.put(DefaultConfSettings.DB_PATH.name(), "testnetdb");
                    Configuration.put(DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
                }
            } else {
                Milestone.init(MAINNET_COORDINATOR, false);
                if (dbPath.isEmpty() || dbPath.equals("testnetdb")) {
                    // mainnetusers must not use testnetdb, overwrite it unless an explicit name is set.
                    Configuration.put(DefaultConfSettings.DB_PATH.name(), "mainnetdb");
                    Configuration.put(DefaultConfSettings.DB_LOG_PATH.name(), "mainnetdb.log");
                }
            }
            TransactionValidator.init(Configuration.booling(Configuration.DefaultConfSettings.TESTNET));
            Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
            if (Configuration.booling(DefaultConfSettings.EXPORT)) {
                Tangle.instance().addPersistenceProvider(new FileExportProvider());
            }
            Tangle.instance().init();
            LedgerValidator.init();
            Milestone.instance().init();
            TipsManager.instance.init();
            TransactionRequester.init(Configuration.doubling(Configuration.DefaultConfSettings.P_REMOVE_REQUEST.name()));
            Node.instance().init(Configuration.doubling(DefaultConfSettings.P_DROP_TRANSACTION.name()),
                    Configuration.doubling(DefaultConfSettings.P_SELECT_MILESTONE_CHILD.name()),
                    Configuration.doubling(DefaultConfSettings.P_SEND_MILESTONE.name()),
                    Configuration.string(DefaultConfSettings.NEIGHBORS));
            UDPReceiver.instance().init(Configuration.integer(DefaultConfSettings.UDP_RECEIVER_PORT));
            API.instance().init();
            Replicator.instance().init(Configuration.integer(DefaultConfSettings.TCP_RECEIVER_PORT));
            IXI.instance().init(Configuration.string(DefaultConfSettings.IXI_DIR));

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

        final Option<String> config = parser.addStringOption('c', "config");
        final Option<String> port = parser.addStringOption('p', "port");
        final Option<String> rportudp = parser.addStringOption('u', "udp-receiver-port");
        final Option<String> rporttcp = parser.addStringOption('t', "tcp-receiver-port");
        final Option<Boolean> debug = parser.addBooleanOption('d', "debug");
        final Option<Boolean> remote = parser.addBooleanOption("remote");
        final Option<String> remoteLimitApi = parser.addStringOption("remote-limit-api");
        final Option<String> remoteAuth = parser.addStringOption("remote-auth");
        final Option<String> neighbors = parser.addStringOption('n', "neighbors");
        final Option<Boolean> export = parser.addBooleanOption('e', "export");
        final Option<Boolean> help = parser.addBooleanOption('h', "help");
        final Option<Boolean> testnet = parser.addBooleanOption("testnet");

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
            Configuration.put(DefaultConfSettings.CONFIG, confFilePath);
            Configuration.init();
        }

        // mandatory args
        String inicport = Configuration.getIniValue(DefaultConfSettings.PORT.name());
        final String cport = inicport == null ? parser.getOptionValue(port) : inicport;
        if (cport == null) {
            log.error("Invalid arguments list. Provide at least the PORT in iota.ini or with -p option");
            printUsage();
        }
        else {
            Configuration.put(DefaultConfSettings.PORT, cport);
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

        final String vremoteapilimit = parser.getOptionValue(remoteLimitApi);
        if (vremoteapilimit != null) {
            log.debug("The following api calls are not allowed : {} ", vremoteapilimit);
            Configuration.put(DefaultConfSettings.REMOTE_LIMIT_API, vremoteapilimit);
        }
        
        final String vremoteauth = parser.getOptionValue(remoteAuth);
        if (vremoteauth != null) {
            log.debug("Remote access requires basic authentication");
            Configuration.put(DefaultConfSettings.REMOTE_AUTH, vremoteauth);
        }

        final String vrportudp = parser.getOptionValue(rportudp);
        if (vrportudp != null) {
            Configuration.put(DefaultConfSettings.UDP_RECEIVER_PORT, vrportudp);
        }
        
        final String vrporttcp = parser.getOptionValue(rporttcp);
        if (vrporttcp != null) {
            Configuration.put(DefaultConfSettings.TCP_RECEIVER_PORT, vrporttcp);
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
        
        if (parser.getOptionValue(testnet) != null) {
            Configuration.put(DefaultConfSettings.TESTNET, "true");
            Configuration.put(DefaultConfSettings.DB_PATH.name(), "testnetdb");
            Configuration.put(DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
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
                "[{--export} false]" +
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
                IXI.instance().shutdown();
                API.instance().shutDown();
                Milestone.instance().shutDown();
                TipsManager.instance.shutdown();
                Node.instance().shutdown();
                UDPReceiver.instance().shutdown();
                ReplicatorSourcePool.instance().shutdown();
                ReplicatorSinkPool.instance().shutdown();
                Tangle.instance().shutdown();
            } catch (final Exception e) {
                log.error("Exception occurred shutting down IOTA node: ", e);
            }
        }, "Shutdown Hook"));
    }
}