package com.iota.iri.conf;

public interface DbConfig extends Config {

    String getDbPath();

    String getDbLogPath();

    int getDbCacheSize();

    String getMainDb();

    boolean isExport();

    boolean isRevalidate();

    boolean isRescanDb();

    interface Descriptions {

        String DB_PATH = "The folder where the DB saves its data.";
        String DB_LOG_PATH = "The folder where the DB logs info";
        String DB_CACHE_SIZE = "The size of the DB cache in KB";
        String MAIN_DB = "The DB engine used to store the transactions. Currently only RocksDB is supported.";
        String EXPORT = "Enable exporting the transaction data to files.";
        String REVALIDATE = "Reload from the db data about confirmed transaction (milestones), state of the ledger, and transaction metadata.";
        String RESCAN_DB = "Rescan all transaction metadata (Approvees, Bundles, and Tags)";
    }
}