package com.iota.iri.conf;

public interface SnapshotConfig extends Config {

    long getSnapshotTime();

    //TODO should return File not String
    String getSnapshotFile();

    String getSnapshotSignatureFile();

    int getMilestoneStartIndex();

    int getNumberOfKeysInMilestone();

    String getPreviousEpochSpentAddressesFile();

    String getPreviousEpochSpentAddressesSigFile();

    interface Descriptions {

        String SNAPSHOT_TIME = "Epoch time of the last snapshot.";
        String SNAPSHOT_FILE = "Path of the file that contains the state of the ledger since the last snapshot";
        String SNAPSHOT_SIGNATURE_FILE = "Path to the file that validates the snapshot used is indeed valid";
        String MILESTONE_START_INDEX = "The start index of the milestones. This index is encoded in each milestone transaction by the coordinator.";
        String NUMBER_OF_KEYS_IN_A_MILESTONE = "The height of the merkle tree which in turn determines the number leaves (private keys) that the coordinator can use to sign a message.";
    }
}
