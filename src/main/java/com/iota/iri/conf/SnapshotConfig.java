package com.iota.iri.conf;

/**
 * Configurations for handling global snapshot data
 */
public interface SnapshotConfig extends Config {

    /**
     * @return {@value Descriptions#LOCAL_SNAPSHOTS_ENABLED}
     */
    boolean getLocalSnapshotsEnabled();

    /**
     * @return {@value Descriptions#LOCAL_SNAPSHOTS_PRUNING_ENABLED}
     */
    boolean getLocalSnapshotsPruningEnabled();

    /**
     * @return {@value Descriptions#LOCAL_SNAPSHOTS_PRUNING_DELAY}
     */
    int getLocalSnapshotsPruningDelay();

    /**
     * @return {@value Descriptions#LOCAL_SNAPSHOTS_INTERVAL_SYNCED}
     */
    int getLocalSnapshotsIntervalSynced();

    /**
     * @return {@value Descriptions#LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED}
     */
    int getLocalSnapshotsIntervalUnsynced();

    /**
     * @return {@value Descriptions#LOCAL_SNAPSHOTS_DEPTH}
     */
    int getLocalSnapshotsDepth();

    /**
     * @return {@value Descriptions#SNAPSHOT_TIME}
     */
    long getSnapshotTime();

    /**
     * return {@value Descriptions#SNAPSHOT_FILE}
     */
    String getSnapshotFile();

    /**
     * @return {@value Descriptions#SNAPSHOT_SIGNATURE_FILE}
     */
    String getSnapshotSignatureFile();

    /**
     * @return {@value Descriptions#MILESTONE_START_INDEX}
     */
    int getMilestoneStartIndex();

    /**
     * @return {@value Descriptions#LOCAL_SNAPSHOTS_BASE_PATH}
     */
    String getLocalSnapshotsBasePath();

    /**
     * @return {@value Descriptions#NUMBER_OF_KEYS_IN_A_MILESTONE}
     */
    int getNumberOfKeysInMilestone();

    /**
     * @return {@value Descriptions#PREVIOUS_EPOCH_SPENT_ADDRESSES_FILE}
     */
    String getPreviousEpochSpentAddressesFiles();

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
        String NUMBER_OF_KEYS_IN_A_MILESTONE = "The depth of the Merkle tree which in turn determines the number of" +
                "leaves (private keys) that the coordinator can use to sign a message.";
        String PREVIOUS_EPOCH_SPENT_ADDRESSES_FILE = "The file that contains the list of all used addresses " +
                "from previous epochs";
    }
}
