package com.iota.iri.conf;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class TestnetConfig extends NetConfig {

    protected String coordinator = Defaults.COORDINATOR_ADDRESS;
    protected boolean dontValidateTestnetMilestoneSig = Defaults.VALIDATE_MILESTONE_SIG;
    protected String snapshotFile = Defaults.SNAPSHOT_FILE;
    //TODO should default testnet file be the same as mainnet?
    protected String snapshotSignatureFile = Defaults.SNAPSHOT_SIG;
    protected long snapshotTime = Defaults.SNAPSHOT_TIME;
    protected int mwm = Defaults.MWM;
    protected int milestoneStartIndex = Defaults.MILESTONE_START_INDEX;
    protected int numberOfKeysInMilestone = Defaults.KEYS_IN_MILESTONE;
    protected int transactionPacketSize = Defaults.PACKET_SIZE;
    protected int requestHashSize = Defaults.REQUEST_HASH_SIZE;

    public TestnetConfig() {
        super();
        dbPath = Defaults.DB_PATH;
        dbLogPath = Defaults.DB_LOG_PATH;
    }

    @Override
    public boolean isTestnet() {
        return true;
    }

    @Override
    public String getCoordinator() {
        return coordinator;
    }

    @Parameter(names = "--testnet-coordinator", description = CooConfig.Descriptions.COORDINATOR)
    protected void setCoordinator(String coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public boolean isDontValidateTestnetMilestoneSig() {
        return dontValidateTestnetMilestoneSig;
    }

    @Parameter(names = "--testnet-no-coo-validation", description = CooConfig.Descriptions.VALIDATE_TESTNET_MILESTONE_SIG)
    protected void setDontValidateTestnetMilestoneSig(boolean dontValidateTestnetMilestoneSig) {
        this.dontValidateTestnetMilestoneSig = dontValidateTestnetMilestoneSig;
    }

    @Override
    //TODO maybe change string to file. Experiment with Jackson before
    public String getSnapshotFile() {
        return snapshotFile;
    }

    @Parameter(names = "--snapshot", description = SnapshotConfig.Descriptions.SNAPSHOT_FILE)
    protected void setSnapshotFile(String snapshotFile) {
        this.snapshotFile = snapshotFile;
    }

    @Override
    //TODO maybe change string to file. Experiment with Jackson before
    public String getSnapshotSignatureFile() {
        return snapshotSignatureFile;
    }

    @Parameter(names = "--snapshot-sig", description = SnapshotConfig.Descriptions.SNAPSHOT_SIGNATURE_FILE)
    protected void setSnapshotSignatureFile(String snapshotSignatureFile) {
        this.snapshotSignatureFile = snapshotSignatureFile;
    }

    @Override
    public long getSnapshotTime() {
        return snapshotTime;
    }

    @Parameter(names = "--snapshot-timestamp", description = SnapshotConfig.Descriptions.SNAPSHOT_TIME)
    protected void setSnapshotTime(long snapshotTime) {
        this.snapshotTime = snapshotTime;
    }

    @Override
    public int getMwm() {
        return mwm;
    }

    @Parameter(names = "--mwm", description = ProtocolConfig.Descriptions.MWM)
    protected void setMwm(int mwm) {
        this.mwm = mwm;
    }

    @Override
    public int getMilestoneStartIndex() {
        return milestoneStartIndex;
    }

    @Parameter(names = "--milestone-start", description = SnapshotConfig.Descriptions.MILESTONE_START_INDEX)
    protected void setMilestoneStartIndex(int milestoneStartIndex) {
        this.milestoneStartIndex = milestoneStartIndex;
    }

    @Override
    public int getNumberOfKeysInMilestone() {
        return numberOfKeysInMilestone;
    }

    @JsonProperty("NUMBER_OF_KEYS_IN_A_MILESTONE")
    @Parameter(names = "--milestone-keys", description = SnapshotConfig.Descriptions.NUMBER_OF_KEYS_IN_A_MILESTONE)
    protected void setNumberOfKeysInMilestone(int numberOfKeysInMilestone) {
        this.numberOfKeysInMilestone = numberOfKeysInMilestone;
    }

    @Override
    public int getTransactionPacketSize() {
        return transactionPacketSize;
    }

    @Parameter(names = {"--packet-size"}, description = ProtocolConfig.Descriptions.TRANSACTION_PACKET_SIZE)
    protected void setTransactionPacketSize(int transactionPacketSize) {
        this.transactionPacketSize = transactionPacketSize;
    }

    @Override
    public int getRequestHashSize() {
        return requestHashSize;
    }

    @Parameter(names = {"--request-hash-size"}, description = ProtocolConfig.Descriptions.REQUEST_HASH_SIZE)
    public void setRequestHashSize(int requestHashSize) {
        this.requestHashSize = requestHashSize;
    }

    @Override
    public void setDbPath(String dbPath) {
        if (Objects.equals(MainnetConfig.Defaults.DB_PATH, dbPath)) {
            throw new ParameterException("Testnet Db folder cannot be configured to mainnet's db folder");
        }
            super.setDbPath(dbPath);
    }

    @Override
    public void setDbLogPath(String dbLogPath) {
        if (Objects.equals(MainnetConfig.Defaults.DB_LOG_PATH, dbLogPath)) {
            throw new ParameterException("Testnet Db log folder cannot be configured to mainnet's db log folder");
        }
            super.setDbLogPath(dbLogPath);
    }

    //TODO change to private after refactoring ReplicatorSourceProcessor
    public interface Defaults {
        String COORDINATOR_ADDRESS = "EQQFCZBIHRHWPXKMTOLMYUYPCN9XLMJPYZVFJSAY9FQHCCLWTOLLUGKKMXYFDBOOYFBLBI9WUEILGECYM";
        boolean VALIDATE_MILESTONE_SIG = true;
        String SNAPSHOT_FILE = "/snapshotTestnet.txt";
        int REQUEST_HASH_SIZE = 49;
        String SNAPSHOT_SIG = "/snapshotTestnet.sig";
        int SNAPSHOT_TIME = 1522306500;
        int MWM = 9;
        int MILESTONE_START_INDEX = 434525;
        int KEYS_IN_MILESTONE = 22;
        int PACKET_SIZE = 1653;
        String DB_PATH = "testnetdb";
        String DB_LOG_PATH = "testnetdb.log";
    }
}
