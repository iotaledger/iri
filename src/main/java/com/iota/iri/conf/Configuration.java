package com.iota.iri.conf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All those settings are modificable at runtime, 
 * but for most of them the node needs to be restarted.
 */
public class Configuration {
    
    private static final Logger log = LoggerFactory.getLogger(Configuration.class);
    
    private static final Map<String, String> conf = new ConcurrentHashMap<>();

    public enum DefaultConfSettings {
    	API_PORT,
    	TANGLE_RECEIVER_PORT,
    	CORS_ENABLED,
    	TESTNET, // not used yet
    	HEADLESS,
    	NEIGHBORS,
    	DEBUG
    }
    
    static {
    	// defaults
        conf.put(DefaultConfSettings.API_PORT.name(), "14265");
        conf.put(DefaultConfSettings.TANGLE_RECEIVER_PORT.name(), "14265");
        conf.put(DefaultConfSettings.CORS_ENABLED.name(), "*"); 
        conf.put(DefaultConfSettings.TESTNET.name(), "false");
        conf.put(DefaultConfSettings.HEADLESS.name(), "false");
        conf.put(DefaultConfSettings.DEBUG.name(), "false");
    }
    
    public static String allSettings() {
        final StringBuilder settings = new StringBuilder();
        conf.keySet().forEach(t -> settings.append("Set '").append(t).append("'\t -> ").append(conf.get(t)).append("\n"));
        return settings.toString();
    }

    public static void put(final String k, final String v) {
        log.debug("Setting {} with {}", k, v);
        conf.put(k, v);
    }
    
    public static void put(final DefaultConfSettings d, String v) {
        log.debug("Setting {} with {}", d.name(), v);
        conf.put(d.name(), v);
    }
    
    public static String string(String k) {
        return conf.get(k);
    }
    public static float floating(String k) {
        return Float.parseFloat(conf.get(k));
    }
    public static double doubling(String k) {
        return Double.parseDouble(conf.get(k));
    }
    public static int integer(String k) {
        return Integer.parseInt(conf.get(k));
    }
    public static boolean booling(String k) {
        return Boolean.parseBoolean(conf.get(k));
    }
    
    public static String string(final DefaultConfSettings d) {
        return string(d.name());
    }
    public static int integer(final DefaultConfSettings d) {
        return integer(d.name());
    }
    public static boolean booling(final DefaultConfSettings d) {
        return booling(d.name());
    }
}
