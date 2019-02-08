package com.iota.iri.conf;

/**
 * Configurations for tangle database.
 */
public interface DbConfig extends Config {

    /**
     * @return Descriptions#DB_PATH
     */
    String getDbPath();

    /**
     * @return {@value Descriptions#DB_LOG_PATH}
     */
    String getDbLogPath();

    /**
     * @return {@value Descriptions#DB_CACHE_SIZE}
     */
    int getDbCacheSize();

    /**
     * @return {@value Descriptions#MAIN_DB}
     */
    String getMainDb();

    /**
     * @return {@value Descriptions#REVALIDATE}
     */
    boolean isRevalidate();

    /**
     * @return {@value Descriptions#RESCAN_DB}
     */
    boolean isRescanDb();

    /**
     * @return {@value Descriptions#GRAPH_DB_PATH}
     */
    String getGraphDbPath();

    /**
     * @return {@value Descriptions#ENABLE_BATCH_TXNS}
     */
    boolean isEnableBatchTxns();

    /**
     * @return {@value Descriptions#ENABLE_IPFS_TXNS}
     */
    boolean isEnableIPFSTxns();

    /**
     * @return {@value Descriptions#ENABLE_COMPRESSION_TXNS}
     */
    boolean isEnableCompressionTxns();

    interface Descriptions {

        String DB_PATH = "The folder where the DB saves its data.";
        String DB_LOG_PATH = "The folder where the DB logs info";
        String DB_CACHE_SIZE = "The size of the DB cache in KB";
        String MAIN_DB = "The DB engine used to store the transactions. Currently only RocksDB is supported.";
        String REVALIDATE = "Reload from the db data about confirmed transaction (milestones), state of the ledger, " +
                "and transaction metadata.";
        String RESCAN_DB = "Rescan all transaction metadata (Approvees, Bundles, and Tags)";
        String GRAPH_DB_PATH = "Path to the graph database storage.";
        String ENABLE_BATCH_TXNS = "The DB engine can be used to store batches of transactions.";
        String ENABLE_IPFS_TXNS = "The message is stored in IPFS.";
        String ENABLE_COMPRESSION_TXNS = "The message is compressed.";
    }
}
