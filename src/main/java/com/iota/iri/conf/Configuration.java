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
    private Ini ini;
    private Preferences prefs;


    private final Logger log = LoggerFactory.getLogger(Configuration.class);

    private final Map<String, String> conf = new ConcurrentHashMap<>();

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
        P_REPLY_RANDOM_TIP,
        P_PROPAGATE_REQUEST,
        MAIN_DB, EXPORT, // exports transaction trytes to filesystem
        SEND_LIMIT,
        MAX_PEERS,
        COORDINATOR,
        REVALIDATE,
        RESCAN_DB,
        MIN_RANDOM_WALKS,
        MAX_RANDOM_WALKS,
        MAX_FIND_TRANSACTIONS,
        MAX_GET_TRYTES,
        MAX_DEPTH,
        MAINNET_MWM,
        TESTNET_MWM,
        ZMQ_ENABLED,
        ZMQ_PORT,
        ZMQ_IPC,
        ZMQ_THREADS,
    }

    {
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
        conf.put(DefaultConfSettings.P_REMOVE_REQUEST.name(), "0.01");
        conf.put(DefaultConfSettings.P_DROP_TRANSACTION.name(), "0.0");
        conf.put(DefaultConfSettings.P_SELECT_MILESTONE_CHILD.name(), "0.7");
        conf.put(DefaultConfSettings.P_SEND_MILESTONE.name(), "0.02");
        conf.put(DefaultConfSettings.P_REPLY_RANDOM_TIP.name(), "0.66");
        conf.put(DefaultConfSettings.P_PROPAGATE_REQUEST.name(), "0.01");
        conf.put(DefaultConfSettings.MAIN_DB.name(), "rocksdb");
        conf.put(DefaultConfSettings.EXPORT.name(), "false");
        conf.put(DefaultConfSettings.SEND_LIMIT.name(), "-1.0");
        conf.put(DefaultConfSettings.MAX_PEERS.name(), "0");
        conf.put(DefaultConfSettings.REVALIDATE.name(), "false");
        conf.put(DefaultConfSettings.RESCAN_DB.name(), "false");
        conf.put(DefaultConfSettings.MAINNET_MWM.name(), "15");
        conf.put(DefaultConfSettings.TESTNET_MWM.name(), "13");

        // Pick a number based on best performance
        conf.put(DefaultConfSettings.MIN_RANDOM_WALKS.name(), "5");
        conf.put(DefaultConfSettings.MAX_RANDOM_WALKS.name(), "27");
        // Pick a milestone depth number depending on risk model
        conf.put(DefaultConfSettings.MAX_DEPTH.name(), "15");

        conf.put(DefaultConfSettings.MAX_FIND_TRANSACTIONS.name(), "100000");
        conf.put(DefaultConfSettings.MAX_GET_TRYTES.name(), "10000");
        conf.put(DefaultConfSettings.ZMQ_ENABLED.name(), "false");
        conf.put(DefaultConfSettings.ZMQ_PORT.name(), "5556");
        conf.put(DefaultConfSettings.ZMQ_IPC.name(), "ipc://iri");
        conf.put(DefaultConfSettings.ZMQ_THREADS.name(), "2");

    }

    public boolean init() throws IOException {
        File confFile = new File(string(Configuration.DefaultConfSettings.CONFIG));
        if(confFile.exists()) {
            ini = new Ini(confFile);
            prefs = new IniPreferences(ini);
            return true;
        }
        return false;
    }

    public String getIniValue(String k) {
        if(ini != null) {
            return prefs.node("IRI").get(k, null);
        }
        return null;
    }

    private String getConfValue(String k) {
        String value = getIniValue(k);
        return value == null? conf.get(k): value;
    }

    public String allSettings() {
        final StringBuilder settings = new StringBuilder();
        conf.keySet().forEach(t -> settings.append("Set '").append(t).append("'\t -> ").append(getConfValue(t)).append("\n"));
        return settings.toString();
    }

    public void put(final String k, final String v) {
        log.debug("Setting {} with {}", k, v);
        conf.put(k, v);
    }

    public void put(final DefaultConfSettings d, String v) {
        log.debug("Setting {} with {}", d.name(), v);
        conf.put(d.name(), v);
    }

    private String string(String k) {
        return getConfValue(k);
    }

    public float floating(String k) {
        return Float.parseFloat(getConfValue(k));
    }

    public double doubling(String k) {
        return Double.parseDouble(getConfValue(k));
    }

    private int integer(String k) {
        return Integer.parseInt(getConfValue(k));
    }

    private boolean booling(String k) {
        return Boolean.parseBoolean(getConfValue(k));
    }

    public String string(final DefaultConfSettings d) {
        return string(d.name());
    }

    public int integer(final DefaultConfSettings d) {
        return integer(d.name());
    }

    public boolean booling(final DefaultConfSettings d) {
        return booling(d.name());
    }
}
