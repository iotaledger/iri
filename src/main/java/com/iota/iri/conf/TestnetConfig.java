package com.iota.iri.conf;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;

import java.util.Objects;

public class TestnetConfig extends BaseIotaConfig {

    protected Hash coordinator = Defaults.COORDINATOR_ADDRESS;
    protected int numberOfKeysInMilestone = Defaults.KEYS_IN_MILESTONE;
    protected int maxMilestoneIndex = Defaults.MAX_MILESTONE_INDEX;
    protected int coordinatorSecurityLevel = Defaults.COORDINATOR_SECURITY_LEVEL;

    protected boolean dontValidateTestnetMilestoneSig = Defaults.DONT_VALIDATE_TESTNET_MILESTONE_SIG;
    protected String snapshotFile = Defaults.SNAPSHOT_FILE;
    protected String snapshotSignatureFile = Defaults.SNAPSHOT_SIG;
    protected long snapshotTime = Defaults.SNAPSHOT_TIME;
    protected int mwm = Defaults.MWM;
    protected int milestoneStartIndex = Defaults.MILESTONE_START_INDEX;
    protected int transactionPacketSize = Defaults.PACKET_SIZE;
    protected int requestHashSize = Defaults.REQUEST_HASH_SIZE;
    protected SpongeFactory.Mode coordinatorSignatureMode = Defaults.COORDINATOR_SIGNATURE_MODE;

    public TestnetConfig() {
        super();
        dbPath = Defaults.DB_PATH;
        dbLogPath = Defaults.DB_LOG_PATH;
        localSnapshotsBasePath = Defaults.LOCAL_SNAPSHOTS_BASE_PATH;
    }

    @Override
    public boolean isTestnet() {
        return true;
    }

    @Override
    public Hash getCoordinator() {
        return coordinator;
    }

    @JsonProperty
    @Parameter(names = "--testnet-coordinator", description = MilestoneConfig.Descriptions.COORDINATOR)
    protected void setCoordinator(String coordinator) {
        this.coordinator = HashFactory.ADDRESS.create(coordinator);
    }

    @Override
    public boolean isDontValidateTestnetMilestoneSig() {
        return dontValidateTestnetMilestoneSig;
    }

    @JsonProperty
    @Parameter(names = "--testnet-no-coo-validation", 
        description = MilestoneConfig.Descriptions.DONT_VALIDATE_TESTNET_MILESTONE_SIG, arity = 1)
    protected void setDontValidateTestnetMilestoneSig(boolean dontValidateTestnetMilestoneSig) {
        this.dontValidateTestnetMilestoneSig = dontValidateTestnetMilestoneSig;
    }

    @Override
    public int getNumberOfKeysInMilestone() {
        return numberOfKeysInMilestone;
    }

    @JsonProperty("NUMBER_OF_KEYS_IN_A_MILESTONE")
    @Parameter(names = "--milestone-keys", description = MilestoneConfig.Descriptions.NUMBER_OF_KEYS_IN_A_MILESTONE)
    protected void setNumberOfKeysInMilestone(int numberOfKeysInMilestone) {
        this.numberOfKeysInMilestone = numberOfKeysInMilestone;
        this.maxMilestoneIndex = 1 << numberOfKeysInMilestone;
    }

    @Override
    public int getMaxMilestoneIndex() {
        return maxMilestoneIndex;
    }

    @Override
    public int getCoordinatorSecurityLevel() {
        return coordinatorSecurityLevel;
    }

    @JsonProperty("COORDINATOR_SECURITY_LEVEL")
    @Parameter(names = "--testnet-coordinator-security-level", description = MilestoneConfig.Descriptions.COORDINATOR_SECURITY_LEVEL)
    protected void setCoordinatorSecurityLevel(int coordinatorSecurityLevel) {
        this.coordinatorSecurityLevel = coordinatorSecurityLevel;
    }

    @Override
    public SpongeFactory.Mode getCoordinatorSignatureMode() {
        return coordinatorSignatureMode;
    }

    @JsonProperty("COORDINATOR_SIGNATURE_MODE")
    @Parameter(names = "--testnet-coordinator-signature-mode", description = MilestoneConfig.Descriptions.COORDINATOR_SIGNATURE_MODE)
    protected void setCoordinatorSignatureMode(SpongeFactory.Mode coordinatorSignatureMode) {
        this.coordinatorSignatureMode = coordinatorSignatureMode;
    }

    @Override
    public String getSnapshotFile() {
        return snapshotFile;
    }

    @JsonProperty
    @Parameter(names = "--snapshot", description = SnapshotConfig.Descriptions.SNAPSHOT_FILE)
    protected void setSnapshotFile(String snapshotFile) {
        this.snapshotFile = snapshotFile;
    }

    @Override
    public String getSnapshotSignatureFile() {
        return snapshotSignatureFile;
    }

    @JsonProperty
    @Parameter(names = "--snapshot-sig", description = SnapshotConfig.Descriptions.SNAPSHOT_SIGNATURE_FILE)
    protected void setSnapshotSignatureFile(String snapshotSignatureFile) {
        this.snapshotSignatureFile = snapshotSignatureFile;
    }

    @Override
    public long getSnapshotTime() {
        return snapshotTime;
    }

    @JsonProperty
    @Parameter(names = "--snapshot-timestamp", description = SnapshotConfig.Descriptions.SNAPSHOT_TIME)
    protected void setSnapshotTime(long snapshotTime) {
        this.snapshotTime = snapshotTime;
    }

    @Override
    public int getMwm() {
        return mwm;
    }

    @JsonProperty
    @Parameter(names = "--mwm", description = ProtocolConfig.Descriptions.MWM)
    protected void setMwm(int mwm) {
        this.mwm = mwm;
    }

    @Override
    public int getMilestoneStartIndex() {
        return milestoneStartIndex;
    }

    @JsonProperty
    @Parameter(names = "--milestone-start", description = SnapshotConfig.Descriptions.MILESTONE_START_INDEX)
    protected void setMilestoneStartIndex(int milestoneStartIndex) {
        this.milestoneStartIndex = milestoneStartIndex;
    }

    @Override
    public int getTransactionPacketSize() {
        return transactionPacketSize;
    }

    @JsonProperty
    @Parameter(names = {"--packet-size"}, description = ProtocolConfig.Descriptions.TRANSACTION_PACKET_SIZE)
    protected void setTransactionPacketSize(int transactionPacketSize) {
        this.transactionPacketSize = transactionPacketSize;
    }

    @Override
    public int getRequestHashSize() {
        return requestHashSize;
    }

    @JsonProperty
    @Parameter(names = {"--request-hash-size"}, description = ProtocolConfig.Descriptions.REQUEST_HASH_SIZE)
    public void setRequestHashSize(int requestHashSize) {
        this.requestHashSize = requestHashSize;
    }

    @JsonProperty
    @Override
    public void setDbPath(String dbPath) {
        if (Objects.equals(MainnetConfig.Defaults.DB_PATH, dbPath)) {
            throw new ParameterException("Testnet Db folder cannot be configured to mainnet's db folder");
        }
            super.setDbPath(dbPath);
    }

    @JsonProperty
    @Override
    public void setDbLogPath(String dbLogPath) {
        if (Objects.equals(MainnetConfig.Defaults.DB_LOG_PATH, dbLogPath)) {
            throw new ParameterException("Testnet Db log folder cannot be configured to mainnet's db log folder");
        }
            super.setDbLogPath(dbLogPath);
    }

    public interface Defaults {
        Hash COORDINATOR_ADDRESS = HashFactory.ADDRESS.create(
                "EQQFCZBIHRHWPXKMTOLMYUYPCN9XLMJPYZVFJSAY9FQHCCLWTOLLUGKKMXYFDBOOYFBLBI9WUEILGECYM");
        boolean DONT_VALIDATE_TESTNET_MILESTONE_SIG = false;
        int COORDINATOR_SECURITY_LEVEL = 1;
        SpongeFactory.Mode COORDINATOR_SIGNATURE_MODE = SpongeFactory.Mode.CURLP27;
        int KEYS_IN_MILESTONE = 22;
        int MAX_MILESTONE_INDEX = 1 << KEYS_IN_MILESTONE;

        String LOCAL_SNAPSHOTS_BASE_PATH = "testnet";
        String SNAPSHOT_FILE = "/snapshotTestnet.txt";
        int REQUEST_HASH_SIZE = 49;
        String SNAPSHOT_SIG = "/snapshotTestnet.sig";
        int SNAPSHOT_TIME = 1522306500;
        int MWM = 9;
        int MILESTONE_START_INDEX = 434525;
        int PACKET_SIZE = 1653;
        String DB_PATH = "testnetdb";
        String DB_LOG_PATH = "testnetdb.log";

    }
}
