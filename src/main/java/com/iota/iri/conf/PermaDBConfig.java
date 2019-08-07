package com.iota.iri.conf;

/**
 * Configurations for tangle database.
 */
public interface PermaDBConfig extends Config {


    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PERMADB_ENABLED}
     *
     * @return {@value PermaDBConfig.Descriptions#PERMADB_ENABLED}
     */
    boolean isSelectivePermaEnabled();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PERMADB_PATH}
     *
     * @return {@value PermaDBConfig.Descriptions#PERMADB_PATH}
     */
    String getPermaDbPath();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PERMADB_LOG_PATH}
     *
     * @return {@value PermaDBConfig.Descriptions#PERMADB_LOG_PATH}
     */
    String getPermaDbLogPath();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PERMADB_CACHE_SIZE}
     *
     * @return {@value PermaDBConfig.Descriptions#PERMADB_CACHE_SIZE}
     */
    int getPermaDbCacheSize();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PERMAMAIN_DB}
     *
     * @return {@value PermaDBConfig.Descriptions#PERMAMAIN_DB}
     */
    String getPermaMainDb();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PERMAREVALIDATE}
     *
     * @return {@value PermaDBConfig.Descriptions#PERMAREVALIDATE}
     */
    boolean permaIsRevalidate();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PERMARESCAN_DB}
     *
     * @return {@value PermaDBConfig.Descriptions#PERMARESCAN_DB}
     */
    boolean permaIsRescanDb();

    interface Descriptions {

        String PERMADB_PATH = "The folder where the DB saves its data.";
        String PERMADB_LOG_PATH = "The folder where the DB logs info";
        String PERMADB_CACHE_SIZE = "The size of the DB cache in KB";
        String PERMAMAIN_DB = "The DB engine used to store the transactions. Currently only RocksDB is supported.";
        String PERMAREVALIDATE = "Reload from the db data about confirmed transaction (milestones), state of the ledger, " +
                "and transaction metadata.";
        String PERMARESCAN_DB = "Rescan all transaction metadata (Approvees, Bundles, and Tags)";
        String PERMADB_ENABLED = "Enables secondary permanent storage";
    }
}
