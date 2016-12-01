package com.iota.iri;

import java.util.Collection;
import java.util.Collections;

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
	public static final String VERSION = "1.1.1";

	public static void main(final String[] args) {
		
		log.info("Welcome to {} {}", NAME, VERSION);
		validateParams(args);
		shutdownHook();
		
		try {

			Storage.instance().init();
			Node.instance().init();
			TipsManager.instance().init();
			API.instance().init();

		} catch (final Exception e) {
			log.error("Exception during IOTA node initialisation: ", e);
		}
	}

	private static void validateParams(final String[] args) {
		
		if (args.length > 0 && args[0].equalsIgnoreCase("-h")) {
			printUsage();
		}
		
		if (args.length < 2) {
			log.error("Invalid arguments list. Provide api port number and at least one udp node address.");
			printUsage();
		}
		
		final CmdLineParser parser = new CmdLineParser();
		
		final Option<String> port = parser.addStringOption('p', "port");
	    final Option<String> rport = parser.addStringOption('r', "receiver-port");
	    final Option<String> cors = parser.addStringOption('c', "enabled-cors");
	    final Option<Boolean> headless = parser.addBooleanOption('h', "headless");
	    final Option<Boolean> debug = parser.addBooleanOption('d',"debug");
	    final Option<String> neighbors = parser.addStringOption('n', "neighbors");
	    final Option<Boolean> help = parser.addBooleanOption('h',"help");
	    
	    try {
            parser.parse(args);
        }
        catch ( CmdLineParser.OptionException e ) {
            log.error("Cli error: ", e);
        	printUsage();
            System.exit(2);
        }
	    
	    // mandatory args
	    
	    final String cport = parser.getOptionValue(port);
	    if (cport == null) {
    		log.error("Invalid arguments list. Provide api port number with -p or --port");
			printUsage();
	    }
	
	    final String cns = parser.getOptionValue(neighbors);
	    if (cns == null) {
    		log.error("Invalid arguments list. Provide at least 1 neighbor with -n or --neighbors '<list>'");
			printUsage();
	    }
	    
	    // optionals
	    if (parser.getOptionValue(help) != null) {
	    	printUsage();
	    }
	    
	    if (parser.getOptionValue(cors) != null) {
	    	Configuration.put(DefaultConfSettings.CORS_ENABLED, parser.getOptionValue(cors));
	    }
	    
	    if (parser.getOptionValue(rport) != null) {
	    	Configuration.put(DefaultConfSettings.TANGLE_RECEIVER_PORT, parser.getOptionValue(rport));
	    }
	    
	    if (parser.getOptionValue(headless) != null) {
	    	log.info("Headless feature is WIP...");
	    	Configuration.put(DefaultConfSettings.HEADLESS, "true");
	    }
	    
	    if (parser.getOptionValue(debug) != null) {
	    	Configuration.put(DefaultConfSettings.DEBUG, "true");
	    	log.info(Configuration.allSettings());
	    }
	    
	    Configuration.put(DefaultConfSettings.API_PORT, cport);
	    Configuration.put(DefaultConfSettings.NEIGHBORS, cns.toString());
	
	    if (Integer.parseInt(cport) < 1024) {
			log.warn("Warning: api port value seems too low.");
		}
	}

	private static void printUsage() {
		log.info(
				"Usage: java -jar {}-{}.jar "
				+ "[{-p,--port} 14265] "
				+ "[{-r,--receiver-port} 14265] "
				+ "[{-c,--enabled-cors} *] "
				+ "[{-h,--headless} false] "
				+ "[{-d,--debug} false] "
				//+ "[{-t,--testnet} false] " // -> TBDiscussed
				+ "[{-n,--neighbors} '<list of neighbors>'] ",
				NAME, VERSION);
		System.exit(0);
	}

	private static void shutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {

			log.info("Shutting down IOTA node, please hold tight...");
			try {

				API.instance().shutDown();
				TipsManager.instance().shutDown();
				Node.instance().shutdown();
				Storage.instance().shutdown();

			} catch (final Exception e) {
				log.error("Exception occurred shutting down IOTA node: ", e);
			}
		}, "Shutdown Hook"));
	}
}
