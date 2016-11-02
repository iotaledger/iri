package com.iota.iri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.service.API;
import com.iota.iri.service.Node;
import com.iota.iri.service.Storage;
import com.iota.iri.service.TipsManager;

/**
 * Main IOTA Reference Implementation starting class
 */
public class IRI {

	private static final Logger log = LoggerFactory.getLogger(IRI.class);

	public static final String NAME = "IRI";
	public static final String VERSION = "1.1.0";

	public static void main(final String[] args) {

		log.info("Welcome to {} {}", NAME, VERSION);
		
		validateParams(args);

		try {

			Storage.launch();
			Node.launch(args);
			TipsManager.launch();
			API.launch();

		} catch (final Exception e) {
			log.error("Exception during IOTA node initialisation: ", e);
		}
	}

	private static void validateParams(final String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("-h")) {
			log.info("Usage: java -jar {}-{}.jar [port number] <list of neighbors>", NAME, VERSION);
			System.exit(0);
		}
		
		if (args.length < 2) {
			log.error("Invalid arguments list. Provide port number and at least one upd node addresse.");
			throw new IllegalStateException();
		}

		if (Integer.parseInt(args[0]) < 1024) {
			log.warn("Warning: port value seems too low.");
		}
	}

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			
			log.info("Shutting down IOTA node, please hold tight...");
			try {

				API.shutDown();
				TipsManager.shutDown();
				Node.shutDown();
				Storage.shutDown();

			} catch (final Exception e) {
				log.error("Exception occurred shutting down IOTA node: ", e);
			}

		}, "Shutdown Hook"));
	}
}
