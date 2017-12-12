package com.iota.iri.conf;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper and interface for the configuration stack.
 * Primary used to be backwards compatible to the legacy configuration code.
 */
public class Configuration {
    private Config config = ConfigFactory.load();
    private final Logger log = LoggerFactory.getLogger(getClass());

    public enum DefaultConfSettings {
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
        DNS_REFRESHER_ENABLED,
        COORDINATOR,
        REVALIDATE,
        RESCAN_DB,
        MIN_RANDOM_WALKS,
        MAX_RANDOM_WALKS,
        MAX_FIND_TRANSACTIONS,
        MAX_REQUESTS_LIST,
        MAX_GET_TRYTES,
        MAX_BODY_LENGTH,
        MAX_DEPTH,
        MAINNET_MWM,
        TESTNET_MWM,
        ZMQ_ENABLED,
        ZMQ_PORT,
        ZMQ_IPC,
        ZMQ_THREADS,
    }

    public Configuration() {

        // this is needed to put system env variables into the configuration
        // until https://github.com/lightbend/config/issues/488 got fixed
        System.getenv().forEach(this::put);
    }

    public void put(final String k, final String v) {
        log.debug("Setting {} with {}", k, v);
        config = config.withValue(k,
                ConfigValueFactory.fromAnyRef(v));
    }

    public void put(final DefaultConfSettings d, String v) {
        log.debug("Setting {} with {}", d.name(), v);
        this.put(d.name(), v);
    }

    public boolean has(String k) {
        return config.hasPath(k);
    }

    public boolean has(final DefaultConfSettings d) {
        return this.has(d.name());
    }

    public String string(String k) {
        return config.getString(k);
    }

    public float floating(String k) {
        return (float) config.getDouble(k);
    }

    public double doubling(String k) {
        return config.getDouble(k);
    }

    private int integer(String k) {
        return config.getInt(k);
    }

    private boolean booling(String k) {
        return config.getBoolean(k);
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
