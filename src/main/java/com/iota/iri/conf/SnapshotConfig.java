package com.iota.iri.conf;

/**
 * Configurations for handling global snapshot data
 */
public interface SnapshotConfig extends Config {

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#LOCAL_SNAPSHOTS_ENABLED}
     *
     * @return {@value SnapshotConfig.Descriptions#LOCAL_SNAPSHOTS_ENABLED}
     */
    boolean getLocalSnapshotsEnabled();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#LOCAL_SNAPSHOTS_PRUNING_ENABLED}
     *
     * @return {@value SnapshotConfig.Descriptions#LOCAL_SNAPSHOTS_PRUNING_ENABLED}
     */
    boolean getLocalSnapshotsPruningEnabled();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#LOCAL_SNAPSHOTS_PRUNING_DELAY}
     *
     * @return {@value SnapshotConfig.Descriptions#LOCAL_SNAPSHOTS_PRUNING_DELAY}
     */
    int getLocalSnapshotsPruningDelay();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#LOCAL_SNAPSHOTS_INTERVAL_SYNCED}
     *
     * @return {@value SnapshotConfig.Descriptions#LOCAL_SNAPSHOTS_INTERVAL_SYNCED}
     */
    int getLocalSnapshotsIntervalSynced();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED}
     *
     * @return {@value SnapshotConfig.Descriptions#LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED}
     */
    int getLocalSnapshotsIntervalUnsynced();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#LOCAL_SNAPSHOTS_DEPTH}
     *
     * @return {@value SnapshotConfig.Descriptions#LOCAL_SNAPSHOTS_DEPTH}
     */
    int getLocalSnapshotsDepth();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#SNAPSHOT_TIME}
     *
     * @return {@value SnapshotConfig.Descriptions#SNAPSHOT_TIME}
     */
    long getSnapshotTime();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#SNAPSHOT_FILE}
     *
     * return {@value SnapshotConfig.Descriptions#SNAPSHOT_FILE}
     */
    String getSnapshotFile();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#SNAPSHOT_SIGNATURE_FILE}
     *
     * @return {@value SnapshotConfig.Descriptions#SNAPSHOT_SIGNATURE_FILE}
     */
    String getSnapshotSignatureFile();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#MILESTONE_START_INDEX}
     *
     * @return {@value SnapshotConfig.Descriptions#MILESTONE_START_INDEX}
     */
    int getMilestoneStartIndex();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#LOCAL_SNAPSHOTS_BASE_PATH}
     *
     * @return {@value SnapshotConfig.Descriptions#LOCAL_SNAPSHOTS_BASE_PATH}
     */
    String getLocalSnapshotsBasePath();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#PREVIOUS_EPOCHS_SPENT_ADDRESSES_FILE}
     *
     * @return {@value SnapshotConfig.Descriptions#PREVIOUS_EPOCH_SPENT_ADDRESSES_FILE}
     */
    String getPreviousEpochSpentAddressesFiles();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#SPENT_ADDRESSES_DB_PATH}
     *
     * @return {@value SnapshotConfig.Descriptions#SPENT_ADDRESSES_DB_PATH}
     */
    String getSpentAddressesDbPath();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#SPENT_ADDRESSES_DB_LOG_PATH}
     *
     * @return {@value SnapshotConfig.Descriptions#SPENT_ADDRESSES_DB_LOG_PATH}
     */
    String getSpentAddressesDbLogPath();

    interface Descriptions {

        String LOCAL_SNAPSHOTS_ENABLED = "Flag that determines if local snapshots are enabled.";
        String LOCAL_SNAPSHOTS_PRUNING_ENABLED = "Flag that determines if pruning of old data is enabled.";
        String LOCAL_SNAPSHOTS_PRUNING_DELAY = "Only prune data that precedes the local snapshot by n milestones.";
        String LOCAL_SNAPSHOTS_INTERVAL_SYNCED = "Take local snapshots every n milestones if the node is fully synced.";
        String LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED = "Take local snapshots every n milestones if the node is syncing.";
        String LOCAL_SNAPSHOTS_DEPTH = "Number of milestones to keep.";
        String LOCAL_SNAPSHOTS_BASE_PATH = "Path to the snapshot files (without file extensions).";
        String SNAPSHOT_TIME = "Epoch time of the last snapshot.";
        String SNAPSHOT_FILE = "Path of the file that contains the state of the ledger at the last snapshot.";
        String SNAPSHOT_SIGNATURE_FILE = "Path to the file that contains a signature for the snapshot file.";
        String MILESTONE_START_INDEX = "The start index of the milestones. This index is encoded in each milestone " +
                "transaction by the coordinator.";
        String PREVIOUS_EPOCH_SPENT_ADDRESSES_FILE = "The file that contains the list of all used addresses " +
                "from previous epochs";
        String SPENT_ADDRESSES_DB_PATH = "The folder where the spent addresses DB saves its data.";
        String SPENT_ADDRESSES_DB_LOG_PATH = "The folder where the spent addresses DB saves its logs.";
    }
}
