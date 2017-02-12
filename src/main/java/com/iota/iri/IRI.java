package com.iota.iri;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ch.qos.logback.classic.Level;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.service.API;
import com.iota.iri.service.Node;
import com.iota.iri.service.TipsManager;
import com.iota.iri.service.storage.Storage;
import com.sanityinc.jargs.CmdLineParser;
import com.sanityinc.jargs.CmdLineParser.Option;

/**
 * Main IOTA Reference Implementation starting class
 */
public class IRI {

    private static final Logger log = LoggerFactory.getLogger(IRI.class);

    public static final String NAME = "IRI";
    public static final String VERSION = "1.1.2.6";

    public static void main(final String[] args) {

        log.info("Welcome to {} {}", NAME, VERSION);
        validateParams(args);
        shutdownHook();

        if (!Configuration.booling(DefaultConfSettings.HEADLESS)) {
            //showIotaLogo();
        }

        try {

            Storage.instance().init();
            Node.instance().init();
            TipsManager.instance().init();
            API.instance().init();
            //IXI.instance().init();

        } catch (final Exception e) {
            log.error("Exception during IOTA node initialisation: ", e);
            System.exit(-1);
        }
        log.info("IOTA Node initialised correctly.");
    }

    private static void validateParams(final String[] args) {

        if (args == null || args.length < 2) {
            log.warn("No api port number provided (i.e. '-p 14265') using default value 14265.");
        }

        final CmdLineParser parser = new CmdLineParser();

        final Option<String> port = parser.addStringOption('p', "port");
        final Option<String> rport = parser.addStringOption('r', "receiver-port");
        final Option<String> cors = parser.addStringOption('c', "enabled-cors");
        final Option<Boolean> debug = parser.addBooleanOption('d', "debug");
        final Option<Boolean> remote = parser.addBooleanOption("remote");
        final Option<String> remoteLimitApi = parser.addStringOption("remote-limit-api");
        final Option<String> neighbors = parser.addStringOption('n', "neighbors");
        final Option<String> dataDir = parser.addStringOption( "data-dir");
        final Option<Boolean> help = parser.addBooleanOption('h', "help");

        try {
            parser.parse(args);
        } catch (CmdLineParser.OptionException e) {
            log.error("CLI error: ", e);
            printUsage();
            System.exit(2);
        }

        // optional args
        String cport = parser.getOptionValue(port);
        if (cport == null) {
            cport = Configuration.integer(DefaultConfSettings.API_PORT) + "";
            log.warn("No port specified, using default port " + Configuration.integer(DefaultConfSettings.API_PORT));
        }

        if (Integer.parseInt(cport) < 1024) {
            log.warn("Warning: api port value seems too low.");
        }

        // optional flags
        if (parser.getOptionValue(help) != null) {
            printUsage();
        }

        String cns = parser.getOptionValue(neighbors);
        if (cns == null || cns.isEmpty()) {
            log.warn("No neighbor has been specified. Server starting nodeless.");
            cns = StringUtils.EMPTY;
        }
        Configuration.put(DefaultConfSettings.NEIGHBORS, cns);


        final String vcors = parser.getOptionValue(cors);
        if (vcors != null) {
            log.debug("Enabled CORS with value : {} ", vcors);
            Configuration.put(DefaultConfSettings.CORS_ENABLED, vcors);
        }

        String vpath = parser.getOptionValue(dataDir);
        if (vpath != null) {
            log.debug("Custom data dir : {} ", vpath);
            if(!vpath.endsWith("\\") && !vpath.endsWith("/") ){
                vpath += File.separator;
            }
            Configuration.put(DefaultConfSettings.DATA_DIR, vpath);
        }

        final String vremoteapilimit = parser.getOptionValue(remoteLimitApi);
        if (vremoteapilimit != null) {
            log.debug("The following api calls are not allowed : {} ", vremoteapilimit);
            Configuration.put(DefaultConfSettings.REMOTEAPILIMIT, vremoteapilimit);
        }

        final String vrport = parser.getOptionValue(rport);
        if (vrport != null) {
            Configuration.put(DefaultConfSettings.TANGLE_RECEIVER_PORT, vrport);
        }

        if (parser.getOptionValue(remote) != null) {
            log.info("Remote access enabled. Binding API socket to listen any interface.");
            Configuration.put(DefaultConfSettings.API_HOST, "0.0.0.0");
        }

        if (parser.getOptionValue(debug) != null) {
            Configuration.put(DefaultConfSettings.DEBUG, "true");
            log.info(Configuration.allSettings());
            //StatusPrinter.print((LoggerContext) LoggerFactory.getILoggerFactory());
        } else{
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(IRI.class);
            root.setLevel(Level.OFF);
        }
    }

    private static void printUsage() {
        log.info("Usage: java -jar {}-{}.jar " +
                "[{-p,--port} 14265] " +
                "[{-r,--receiver-port} 14265] " +
                "[{-c,--enabled-cors} *] " +
                "[{-h}] [{--headless}] " +
                "[{-d,--debug}] " +
                "[{--data-dir} '/tmp'] " +
                "[{--remote}]" +
                "[{--remote-limit-api} '<list of api calls>']" +
                // + "[{-t,--testnet} false] " // -> TBDiscussed (!)
                "[{-n,--neighbors} '<list of neighbors>'] ", NAME, VERSION);
        System.exit(0);
    }

    private static void shutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            log.info("Shutting down IOTA node, please hold tight...");
            try {

                //IXI.instance().shutdown();
                API.instance().shutDown();
                TipsManager.instance().shutDown();
                Node.instance().shutdown();
                Storage.instance().shutdown();

            } catch (final Exception e) {
                log.error("Exception occurred shutting down IOTA node: ", e);
            }
        }, "Shutdown Hook"));
    }

    private static void showIotaLogo() {
        final String charset = "UTF8";

        try {
            final Path path = Paths.get("logo.utf8.ans");
            Files.readAllLines(path, Charset.forName(charset)).forEach(System.out::println);
        } catch (IOException e) {
            log.error("Impossible to display logo. Charset {} not supported by terminal.", charset);
        }
    }
}
