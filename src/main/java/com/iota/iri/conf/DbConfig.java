package com.iota.iri.conf;

/**
 * Configurations for tangle database.
 */
public interface DbConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#DB_PATH}
     *
     * @return {@value DbConfig.Descriptions#DB_PATH}
     */
    String getDbPath();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#DB_LOG_PATH}
     *
     * @return {@value DbConfig.Descriptions#DB_LOG_PATH}
     */
    String getDbLogPath();
    
    /**
     * Default Value: {@value BaseIotaConfig.Defaults#DB_CONFIG_FILE}
     *
     * @return {@value DbConfig.Descriptions#DB_CONFIG_FILE}
     */
    String getDbConfigFile();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#DB_CACHE_SIZE}
     *
     * @return {@value DbConfig.Descriptions#DB_CACHE_SIZE}
     */
    int getDbCacheSize();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#MAIN_DB}
     *
     * @return {@value DbConfig.Descriptions#MAIN_DB}
     */
    String getMainDb();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#REVALIDATE}
     *
     * @return {@value DbConfig.Descriptions#REVALIDATE}
     */
    boolean isRevalidate();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#RESCAN_DB}
     *
     * @return {@value DbConfig.Descriptions#RESCAN_DB}
     */
    boolean isRescanDb();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#TX_BATCH_WRITE}
     *
     * @return {@value DbConfig.Descriptions#TX_BATCH_WRITE}
     */
    long getTxBatchWrite();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#MILESTONE_BATCH_WRITE}
     *
     * @return {@value DbConfig.Descriptions#MILESTONE_BATCH_WRITE}
     */
    int getMilestoneBatchWrite();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#TX_BATCH_EVICTION_COUNT}
     *
     * @return {@value DbConfig.Descriptions#TX_BATCH_EVICTION_COUNT}
     */
    int getTxBatchEvictionCount();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#MILESTONE_BATCH_EVICTION_COUNT}
     *
     * @return {@value DbConfig.Descriptions#MILESTONE_BATCH_EVICTION_COUNT}
     */
    int getMilestoneBatchEvictionCount();

    interface Descriptions {

        String DB_PATH = "The folder where the DB saves its data.";
        String DB_LOG_PATH = "The folder where the DB logs info";
        String DB_CACHE_SIZE = "The size of the DB cache in KB";
        String MAIN_DB = "The DB engine used to store the transactions. Currently only RocksDB is supported.";
        String REVALIDATE = "Reload from the db data about confirmed transaction (milestones), state of the ledger, " +
                "and transaction metadata.";
        String RESCAN_DB = "Rescan all transaction metadata (Approvees, Bundles, and Tags)";
        String DB_CONFIG_FILE = "The location of the RocksDB configuration file";
        String TX_BATCH_WRITE = "The size of the tangle cache for transactions";
        String MILESTONE_BATCH_WRITE = "The size of the tangle cache for milestones";
        String TX_BATCH_EVICTION_COUNT = "The number of transactions to evict from cache";
        String MILESTONE_BATCH_EVICTION_COUNT = "The number of milestones to evict from cache";
    }
}
