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

    interface Descriptions {

        String DB_PATH = "The folder where the DB saves its data.";
        String DB_LOG_PATH = "The folder where the DB logs info";
        String DB_CACHE_SIZE = "The size of the DB cache in KB";
        String MAIN_DB = "The DB engine used to store the transactions. Currently only RocksDB is supported.";
        String REVALIDATE = "Reload from the db data about confirmed transaction (milestones), state of the ledger, " +
                "and transaction metadata.";
        String RESCAN_DB = "Rescan all transaction metadata (Approvees, Bundles, and Tags)";
    }
}
