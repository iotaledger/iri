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

    public static final String MAINNET_COORDINATOR_ADDRESS =
            "KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU";
    public static final String TESTNET_COORDINATOR_ADDRESS =
            "EQQFCZBIHRHWPXKMTOLMYUYPCN9XLMJPYZVFJSAY9FQHCCLWTOLLUGKKMXYFDBOOYFBLBI9WUEILGECYM";
    public static final String MAINNET_SNAPSHOT_FILE = "/snapshotMainnet.txt";
    public static final String TESTNET_SNAPSHOT_FILE = "/snapshotTestnet.txt";
    public static final String MAINNET_SNAPSHOT_SIG_FILE = "/snapshotMainnet.sig";

    public static final String PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT = "/previousEpochsSpentAddresses.txt";
    public static final String PREVIOUS_EPOCH_SPENT_ADDRESSES_SIG = "/previousEpochsSpentAddresses.sig";
    public static final String MAINNET_MILESTONE_START_INDEX = "426550";
    public static final String TESTNET_MILESTONE_START_INDEX = "434525";
    public static final String MAINNET_NUM_KEYS_IN_MILESTONE = "20";
    public static final String TESTNET_NUM_KEYS_IN_MILESTONE = "22";
    public static final String GLOBAL_SNAPSHOT_TIME = "1525042800";
    public static final String TESTNET_GLOBAL_SNAPSHOT_TIME = "1522306500";


    public static final String MAINNET_MWM = "14";
    public static final String TESTNET_MWM = "9";
    public static final String PACKET_SIZE = "1650";
    public static final String TESTNET_PACKET_SIZE = "1653";
    public static final String REQ_HASH_SIZE = "46";
    public static final String TESTNET_REQ_HASH_SIZE = "49";




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
        DB_CACHE_SIZE,
        P_REMOVE_REQUEST,
        P_DROP_TRANSACTION,
        P_SELECT_MILESTONE_CHILD,
        P_SEND_MILESTONE,
        P_REPLY_RANDOM_TIP,
        P_PROPAGATE_REQUEST,
        MAIN_DB, EXPORT, // exports transaction trytes to filesystem
        SEND_LIMIT,
        MAX_PEERS,
        DNS_RESOLUTION_ENABLED,
        DNS_REFRESHER_ENABLED,
        COORDINATOR,
        DONT_VALIDATE_TESTNET_MILESTONE_SIG,
        REVALIDATE,
        RESCAN_DB,
        MIN_RANDOM_WALKS,
        MAX_RANDOM_WALKS,
        MAX_FIND_TRANSACTIONS,
        MAX_REQUESTS_LIST,
        MAX_GET_TRYTES,
        MAX_BODY_LENGTH,
        MAX_DEPTH,
        MWM,
        ZMQ_ENABLED,
        ZMQ_PORT,
        ZMQ_IPC,
        ZMQ_THREADS,
        Q_SIZE_NODE,
        P_DROP_CACHE_ENTRY,
        CACHE_SIZE_BYTES,
        SNAPSHOT_FILE,
        SNAPSHOT_SIGNATURE_FILE,
        MILESTONE_START_INDEX,
        NUMBER_OF_KEYS_IN_A_MILESTONE,
        TRANSACTION_PACKET_SIZE,
        REQUEST_HASH_SIZE,
        SNAPSHOT_TIME,
        TIPSELECTION_ALPHA
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
        conf.put(DefaultConfSettings.DB_CACHE_SIZE.name(), "100000"); //KB
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
        conf.put(DefaultConfSettings.DNS_REFRESHER_ENABLED.name(), "true");
        conf.put(DefaultConfSettings.DNS_RESOLUTION_ENABLED.name(), "true");
        conf.put(DefaultConfSettings.REVALIDATE.name(), "false");
        conf.put(DefaultConfSettings.RESCAN_DB.name(), "false");
        conf.put(DefaultConfSettings.MWM.name(), MAINNET_MWM);

        // Pick a number based on best performance
        conf.put(DefaultConfSettings.MIN_RANDOM_WALKS.name(), "5");
        conf.put(DefaultConfSettings.MAX_RANDOM_WALKS.name(), "27");
        // Pick a milestone depth number depending on risk model
        conf.put(DefaultConfSettings.MAX_DEPTH.name(), "15");

        conf.put(DefaultConfSettings.MAX_FIND_TRANSACTIONS.name(), "100000");
        conf.put(DefaultConfSettings.MAX_REQUESTS_LIST.name(), "1000");
        conf.put(DefaultConfSettings.MAX_GET_TRYTES.name(), "10000");
        conf.put(DefaultConfSettings.MAX_BODY_LENGTH.name(), "1000000");
        conf.put(DefaultConfSettings.ZMQ_ENABLED.name(), "false");
        conf.put(DefaultConfSettings.ZMQ_PORT.name(), "5556");
        conf.put(DefaultConfSettings.ZMQ_IPC.name(), "ipc://iri");
        conf.put(DefaultConfSettings.ZMQ_THREADS.name(), "2");

        conf.put(DefaultConfSettings.Q_SIZE_NODE.name(), "1000");
        conf.put(DefaultConfSettings.P_DROP_CACHE_ENTRY.name(), "0.02");
        conf.put(DefaultConfSettings.CACHE_SIZE_BYTES.name(), "15000");

        conf.put(DefaultConfSettings.COORDINATOR.name(), MAINNET_COORDINATOR_ADDRESS);
        conf.put(DefaultConfSettings.DONT_VALIDATE_TESTNET_MILESTONE_SIG.name(), "false");
        conf.put(DefaultConfSettings.SNAPSHOT_FILE.name(), MAINNET_SNAPSHOT_FILE);
        conf.put(DefaultConfSettings.SNAPSHOT_SIGNATURE_FILE.name(), MAINNET_SNAPSHOT_SIG_FILE);
        conf.put(DefaultConfSettings.MILESTONE_START_INDEX.name(), MAINNET_MILESTONE_START_INDEX);
        conf.put(DefaultConfSettings.NUMBER_OF_KEYS_IN_A_MILESTONE.name(), MAINNET_NUM_KEYS_IN_MILESTONE);
        conf.put(DefaultConfSettings.TRANSACTION_PACKET_SIZE.name(), PACKET_SIZE);
        conf.put(DefaultConfSettings.REQUEST_HASH_SIZE.name(), REQ_HASH_SIZE);
        conf.put(DefaultConfSettings.SNAPSHOT_TIME.name(), GLOBAL_SNAPSHOT_TIME);
        conf.put(DefaultConfSettings.TIPSELECTION_ALPHA.name(), "0.1");

    }

    public boolean init() throws IOException {
        File confFile = new File(string(Configuration.DefaultConfSettings.CONFIG));
        if (confFile.exists()) {
            ini = new Ini(confFile);
            prefs = new IniPreferences(ini);
            return true;
        }
        return false;
    }

    public String getIniValue(String k) {
        if (ini != null) {
            return prefs.node("IRI").get(k, null);
        }
        return null;
    }

    private String getConfValue(String k) {
        String value = getIniValue(k);
        return value == null ? conf.get(k) : value;
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

    private long longNum(String k) { return Long.parseLong(getConfValue(k)); }

    public String string(final DefaultConfSettings d) {
        return string(d.name());
    }

    public int integer(final DefaultConfSettings d) {
        return integer(d.name());
    }

    public long longNum(final DefaultConfSettings d) {
        return longNum(d.name());
    }

    public boolean booling(final DefaultConfSettings d) {
        return booling(d.name());
    }
}
