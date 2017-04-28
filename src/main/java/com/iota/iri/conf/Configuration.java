package com.iota.iri.conf;

import org.ini4j.Ini;
import org.ini4j.IniPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

/**
 * All those settings are modificable at runtime,
 * but for most of them the node needs to be restarted.
 */
public class Configuration {
    private static Ini ini;
    private static Preferences prefs;


    private static final Logger log = LoggerFactory.getLogger(Configuration.class);

    private static final Map<String, String> conf = new ConcurrentHashMap<>();

    public enum DefaultConfSettings {
        CONFIG,
        PORT,
        API_HOST,
        UDP_RECEIVER_PORT,
        TCP_RECEIVER_PORT,
        TESTNET,
        DEBUG,
        REMOTE_LIMIT_API,
        REMOTE_AUTH,
        NEIGHBORS,        
        IXI_DIR,
        DB_PATH,
        DB_LOG_PATH,
        P_REMOVE_REQUEST,
        P_DROP_TRANSACTION,
        P_SELECT_MILESTONE_CHILD,
        P_SEND_MILESTONE,
        EXPORT // exports transaction trytes to filesystem
    }

    static {
        // defaults
        conf.put(DefaultConfSettings.PORT.name(), "14600");
        conf.put(DefaultConfSettings.API_HOST.name(), "localhost");
        conf.put(DefaultConfSettings.UDP_RECEIVER_PORT.name(), "14600");
        conf.put(DefaultConfSettings.TCP_RECEIVER_PORT.name(), "15600");
        conf.put(DefaultConfSettings.TESTNET.name(), "false");
        conf.put(DefaultConfSettings.DEBUG.name(), "false");
        conf.put(DefaultConfSettings.REMOTE_LIMIT_API.name(), "");
        conf.put(DefaultConfSettings.REMOTE_AUTH.name(), "");
        conf.put(DefaultConfSettings.NEIGHBORS.name(), "");
        conf.put(DefaultConfSettings.IXI_DIR.name(), "ixi");
        conf.put(DefaultConfSettings.DB_PATH.name(), "mainnetdb");
        conf.put(DefaultConfSettings.DB_LOG_PATH.name(), "mainnet.log");
        conf.put(DefaultConfSettings.CONFIG.name(), "iota.ini");
        conf.put(DefaultConfSettings.P_REMOVE_REQUEST.name(), "0.0");
        conf.put(DefaultConfSettings.P_DROP_TRANSACTION.name(), "0.0");
        conf.put(DefaultConfSettings.P_SELECT_MILESTONE_CHILD.name(), "0.7");
        conf.put(DefaultConfSettings.P_SEND_MILESTONE.name(), "0.02");
        conf.put(DefaultConfSettings.EXPORT.name(), "false");
    }

    public static boolean init() throws IOException {
        File confFile = new File(Configuration.string(Configuration.DefaultConfSettings.CONFIG));
        if(confFile.exists()) {
            ini = new Ini(confFile);
            prefs = new IniPreferences(ini);
            return true;
        }
        return false;
    }

    public static String getIniValue(String k) {
        if(ini != null) {
            return prefs.node("IRI").get(k, null);
        }
        return null;
    }

    private static String getConfValue(String k) {
        String value = getIniValue(k);
        return value == null? conf.get(k): value;
    }

    public static String allSettings() {
        final StringBuilder settings = new StringBuilder();
        conf.keySet().forEach(t -> settings.append("Set '").append(t).append("'\t -> ").append(getConfValue(t)).append("\n"));
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

    private static String string(String k) {
        return getConfValue(k);
    }

    public static float floating(String k) {
        return Float.parseFloat(getConfValue(k));
    }

    public static double doubling(String k) {
        return Double.parseDouble(getConfValue(k));
    }

    private static int integer(String k) {
        return Integer.parseInt(getConfValue(k));
    }

    private static boolean booling(String k) {
        return Boolean.parseBoolean(getConfValue(k));
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
