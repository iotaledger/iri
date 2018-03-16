package com.iota.iri;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.service.API;
import com.sanityinc.jargs.CmdLineParser;
import com.sanityinc.jargs.CmdLineParser.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Objects;

/**
 * Main IOTA Reference Implementation starting class
 */
public class IRIImpl implements IRI {

    private static final Logger log = LoggerFactory.getLogger(IRIImpl.class);

    private static final String MAINNET_NAME = "IRIImpl";
    private static final String TESTNET_NAME = "IRIImpl Testnet";
    private static final String VERSION = "1.4.2.2";
    private static final String JARNAME = MAINNET_NAME + "-" + VERSION;


    private final String version;
    private final String jarName;
    private final String netName;
    private final Configuration configuration;

    /**
     * The default launcher uses the hard coded MAINET_NAME, TESTNET_NAME, VERSION and JARNAME.
     * Implementers may create a custom launcher by overriding methods below and/or specifying different
     * names and versions.
     *
     * @param args startup configuration arguments
     */
    public static void main(final String[] args) {
        try {
            IRIImpl iri = new IRIImpl(MAINNET_NAME, TESTNET_NAME, VERSION, JARNAME, args);
            iri.init();
        } catch (CmdLineParser.OptionException e) {
            log.error("CLI error", e);
            printUsage(JARNAME);
            System.exit(2);
        } catch (final Exception e) {
            String reason = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
            log.error("Initialization exception: " + reason, e);
            printUsage(JARNAME);
            System.exit(-1);
        }
    }

    public IRIImpl(final String mainNetName,
                   final String testNetName,
                   final String version,
                   final String jarName,
                   final String[] args
    ) throws Exception {

        Validate.notBlank(mainNetName);
        Validate.notBlank(testNetName);
        Validate.notBlank(version);
        Validate.notBlank(jarName);

        this.version = version;
        this.jarName = jarName;
        this.configuration = new Configuration();
        if (validateParams(configuration, args)) {
            this.netName = configuration.booling(DefaultConfSettings.TESTNET) ? testNetName : mainNetName;
        } else {
            this.netName = null;
            printUsage(jarName);
        }
    }

    public void init() throws Exception {
        log.info("Welcome to {} {}", netName, version);

        final Iota iota = new Iota(configuration);
        final IXI ixi = new IXI(iota);
        final API api = new API(this, iota, ixi);
        shutdownHook(iota, ixi, api);

        if (configuration.booling(DefaultConfSettings.DEBUG)) {
            log.info("You have set the debug flag. To enable debug output, you need to uncomment the DEBUG appender in the source tree at iri/src/main/resources/logback.xml and re-package iri.jar");
        }

        if (configuration.booling(DefaultConfSettings.EXPORT)) {
            File dir = new File("export");
            if (!dir.exists()) {
                log.info("Create directory 'export'");
                try {
                    dir.mkdir();
                } catch (SecurityException e) {
                    log.error("Could not create directory", e);
                }
            }
            dir = new File("export-solid");
            if (!dir.exists()) {
                log.info("Create directory 'export-solid'");
                try {
                    dir.mkdir();
                } catch (SecurityException e) {
                    log.error("Could not create directory", e);
                }
            }
        }

        iota.init();
        api.init();
        ixi.init(configuration.string(DefaultConfSettings.IXI_DIR));
        log.info("IOTA Node initialised correctly.");
    }


    // THIS METHOD RETURNS TRUE IF THE PROGRAM IS TO CONTINUE
    // FALSE IF IT IS TO EXIT NORMALLY
    private boolean validateParams(final Configuration configuration, final String[] args) throws Exception {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Missing arguments list. Provide ini-file 'iota.ini' or API port number (i.e. '-p 14600').");
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
        parser.parse(args);

        // first flag we check is '-h' || '--help'
        if (parser.getOptionValue(help) != null) {
            return false;
        }

        // PARSE MANDATORY ARGS - FIRST FIND CONFIG FILE - IF IT IS ALREADY SPECIFIED
        {
            String confFilePath = parser.getOptionValue(config);
            if (confFilePath != null) {
                configuration.put(DefaultConfSettings.CONFIG, confFilePath);
                if (configuration.init()) {
                    log.info("Configuration file at '{}' being used.", confFilePath);
                } else {
                    throw new Exception("Invalid configuration argument or bad config file. " +
                            "Please check to see that '" + confFilePath + "' is a valid INI file.");
                }
            } else if (args.length < 2 && !configuration.init()) {
                throw new Exception("Invalid arguments list. Provide ini-file 'iota.ini' or API port number (i.e. '-p 14600').");
            }
        }

        final Integer cport = obtainPort(configuration, parser, port);
        if (cport == null) {
            throw new Exception("Invalid arguments list. Provide at least the PORT in iota.ini or with -p option");
        } else if (cport < 1024) {
            log.warn("Warning: api port value seems too low: port= {}", cport);
        }
        configuration.put(DefaultConfSettings.PORT, "" + cport);


        // optional flags
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
        return true;
    }


    private static void printUsage(String jarName) {
        log.info("Usage: java -jar {}.jar " +
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
                , jarName);
    }

    private Integer obtainPort(Configuration configuration, CmdLineParser parser, Option<String> port) {
        final String iniValue = configuration.getIniValue(DefaultConfSettings.PORT.name());
        final String argsValue = parser.getOptionValue(port);
        if (iniValue != null && argsValue != null && !iniValue.equals(argsValue)) {
            log.info("Port setting in INI file is different from the command line arguments: INI= {}, ARGS= {}", iniValue, argsValue);
        }
        if (argsValue != null) {
            log.info("Port setting obtained from commandline: {}", argsValue);
            return Integer.parseInt(argsValue);
        }
        if (iniValue != null) {
            log.info("Port setting obtained from ini file: {}", iniValue);
            return Integer.parseInt(iniValue);
        }
        return null;
    }

    private void shutdownHook(Iota iota, IXI ixi, API api) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            log.info("Shutting down IOTA node, please hold tight...");
            try {
                ixi.shutdown();
                api.shutDown();
                iota.shutdown();
            } catch (final Exception e) {
                log.error("Exception occurred shutting down IOTA node: ", e);
            }
        }, "Shutdown Hook - " + netName));
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getJarName() {
        return jarName;
    }

    @Override
    public String getNetName() {
        return netName;
    }
}
