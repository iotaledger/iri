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
        API_PORT,
        API_HOST,
        TANGLE_RECEIVER_PORT,
        CORS_ENABLED,
        TESTNET, // not used yet
        HEADLESS,
        REMOTEAPILIMIT,
        NEIGHBORS,
        DEBUG,
        IXI_DIR,
        DB_PATH,
        DB_LOG_PATH,
        CONF_PATH,
        P_REMOVE_REQUEST,
        P_DROP_TRANSACTION,
        EXPERIMENTAL // experimental features.
    }

    static {
        // defaults
        conf.put(DefaultConfSettings.API_PORT.name(), "14700");
        conf.put(DefaultConfSettings.API_HOST.name(), "localhost");
        conf.put(DefaultConfSettings.TANGLE_RECEIVER_PORT.name(), "14700");
        conf.put(DefaultConfSettings.CORS_ENABLED.name(), "*");
        conf.put(DefaultConfSettings.TESTNET.name(), "false");
        conf.put(DefaultConfSettings.HEADLESS.name(), "false");
        conf.put(DefaultConfSettings.DEBUG.name(), "false");
        conf.put(DefaultConfSettings.REMOTEAPILIMIT.name(), "");
        conf.put(DefaultConfSettings.IXI_DIR.name(), "ixi");
        conf.put(DefaultConfSettings.DB_PATH.name(), "testnetdb");
        conf.put(DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
        conf.put(DefaultConfSettings.CONF_PATH.name(), "iota.ini");
        conf.put(DefaultConfSettings.P_REMOVE_REQUEST.name(), "0.1");
        conf.put(DefaultConfSettings.P_DROP_TRANSACTION.name(), "0.2");
        conf.put(DefaultConfSettings.EXPERIMENTAL.name(), "false");
    }

    public static boolean init() throws IOException {
        File confFile = new File(Configuration.string(Configuration.DefaultConfSettings.CONF_PATH));
        if(confFile.exists()) {
            ini = new Ini(confFile);
            prefs = new IniPreferences(ini);
            return true;
        }
        return false;
    }

    public static String getIniValue(String k) {
        if(ini != null) {
            return prefs.node("IRI").get(k.toString(), null);
        }
        return null;
    }

    public static String getConfValue(String k) {
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

    public static String string(String k) {
        return getConfValue(k);
    }

    public static float floating(String k) {
        return Float.parseFloat(getConfValue(k));
    }

    public static double doubling(String k) {
        return Double.parseDouble(getConfValue(k));
    }

    public static int integer(String k) {
        return Integer.parseInt(getConfValue(k));
    }

    public static boolean booling(String k) {
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
