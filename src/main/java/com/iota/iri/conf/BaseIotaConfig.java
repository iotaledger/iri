package com.iota.iri.conf;

import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.utils.IotaUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.ArrayUtils;

/**
  Note: the fields in this class are being deserialized from Jackson so they must follow Java Bean convention.
  Meaning that every field must have a getter that is prefixed with `get` unless it is a boolean and then it should be
  prefixed with `is`.
 */
public abstract class BaseIotaConfig implements IotaConfig {

    public static final String SPLIT_STRING_TO_LIST_REGEX = ",| ";

    private boolean help;
    private boolean testnet = false;
    
    //API
    protected int port = Defaults.PORT;
    protected String apiHost = Defaults.API_HOST;
    protected List<String> remoteLimitApi = Defaults.REMOTE_LIMIT_API;
    protected List<InetAddress> remoteTrustedApiHosts = Defaults.REMOTE_LIMIT_API_HOSTS;
    protected int maxFindTransactions = Defaults.MAX_FIND_TRANSACTIONS;
    protected int maxRequestsList = Defaults.MAX_REQUESTS_LIST;
    protected int maxGetTrytes = Defaults.MAX_GET_TRYTES;
    protected int maxBodyLength = Defaults.MAX_BODY_LENGTH;
    protected String remoteAuth = Defaults.REMOTE_AUTH;
    
    //We don't have a REMOTE config but we have a remote flag. We must add a field for JCommander
    private boolean remote;

    //Network
    protected String neighboringSocketAddress = Defaults.NEIGHBORING_SOCKET_ADDRESS;
    protected int neighboringSocketPort = Defaults.NEIGHBORING_SOCKET_PORT;
    protected int reconnectAttemptIntervalSeconds = Defaults.RECONNECT_ATTEMPT_INTERVAL_SECONDS;
    protected boolean autoTetheringEnabled = Defaults.AUTO_TETHERING_ENABLED;
    protected double pRemoveRequest = Defaults.P_REMOVE_REQUEST;
    protected double pDropCacheEntry = Defaults.P_DROP_CACHE_ENTRY;
    protected int sendLimit = Defaults.SEND_LIMIT;
    protected int maxNeighbors = Defaults.MAX_NEIGHBORS;
    protected boolean dnsRefresherEnabled = Defaults.DNS_REFRESHER_ENABLED;
    protected boolean dnsResolutionEnabled = Defaults.DNS_RESOLUTION_ENABLED;
    protected List<String> neighbors = Collections.EMPTY_LIST;

    //IXI
    protected String ixiDir = Defaults.IXI_DIR;

    //DB
    protected String dbPath = Defaults.DB_PATH;
    protected String dbLogPath = Defaults.DB_LOG_PATH;
    protected int dbCacheSize = Defaults.DB_CACHE_SIZE; //KB
    protected String mainDb = Defaults.MAIN_DB;
    protected boolean revalidate = Defaults.REVALIDATE;
    protected boolean rescanDb = Defaults.RESCAN_DB;

    //Protocol
    protected double pReplyRandomTip = Defaults.P_REPLY_RANDOM_TIP;
    protected double pDropTransaction = Defaults.P_DROP_TRANSACTION;
    protected double pSelectMilestoneChild = Defaults.P_SELECT_MILESTONE_CHILD;
    protected double pSendMilestone = Defaults.P_SEND_MILESTONE;
    protected double pPropagateRequest = Defaults.P_PROPAGATE_REQUEST;

    //ZMQ
    protected boolean zmqEnableTcp = Defaults.ZMQ_ENABLE_TCP;
    protected boolean zmqEnableIpc = Defaults.ZMQ_ENABLE_IPC;
    protected int zmqPort = Defaults.ZMQ_PORT;
    protected int zmqThreads = Defaults.ZMQ_THREADS;
    protected String zmqIpc = Defaults.ZMQ_IPC;
    protected int qSizeNode = Defaults.QUEUE_SIZE;
    protected int cacheSizeBytes = Defaults.CACHE_SIZE_BYTES;
    /**
     * @deprecated This field was replaced by {@link #zmqEnableTcp} and {@link #zmqEnableIpc}. It is only needed
     * for backward compatibility to --zmq-enabled parameter with JCommander.
     */
    @Deprecated
    private boolean zmqEnabled;

    //Tip Selection
    protected int maxDepth = Defaults.MAX_DEPTH;
    protected double alpha = Defaults.ALPHA;
    protected int tipSelectionTimeoutSec = Defaults.TIP_SELECTION_TIMEOUT_SEC;
    private int maxAnalyzedTransactions = Defaults.BELOW_MAX_DEPTH_TRANSACTION_LIMIT;

    //Tip Solidification
    protected boolean tipSolidifierEnabled = Defaults.TIP_SOLIDIFIER_ENABLED;

    //PearlDiver
    protected int powThreads = Defaults.POW_THREADS;

    //Snapshot
    protected boolean localSnapshotsEnabled = Defaults.LOCAL_SNAPSHOTS_ENABLED;
    protected boolean localSnapshotsPruningEnabled = Defaults.LOCAL_SNAPSHOTS_PRUNING_ENABLED;
    protected int localSnapshotsPruningDelay = Defaults.LOCAL_SNAPSHOTS_PRUNING_DELAY;
    protected int localSnapshotsIntervalSynced = Defaults.LOCAL_SNAPSHOTS_INTERVAL_SYNCED;
    protected int localSnapshotsIntervalUnsynced = Defaults.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED;
    protected int localSnapshotsDepth = Defaults.LOCAL_SNAPSHOTS_DEPTH;
    protected String localSnapshotsBasePath = Defaults.LOCAL_SNAPSHOTS_BASE_PATH;
    protected String spentAddressesDbPath = Defaults.SPENT_ADDRESSES_DB_PATH;
    protected String spentAddressesDbLogPath = Defaults.SPENT_ADDRESSES_DB_LOG_PATH;

    public BaseIotaConfig() {
        //empty constructor
    }

    @Override
    public JCommander parseConfigFromArgs(String[] args) throws ParameterException {
        //One can invoke help via INI file (feature/bug) so we always create JCommander even if args is empty
        JCommander jCommander = JCommander.newBuilder()
                .addObject(this)
                //This is in order to enable the `--conf` option
                .acceptUnknownOptions(true)
                .allowParameterOverwriting(true)
                //This is the first line of JCommander Usage
                .programName("java -jar iri-" + IotaUtils.getIriVersion() + ".jar")
                .build();
        if (ArrayUtils.isNotEmpty(args)) {
            jCommander.parse(args);
        }
        return jCommander;
    }

    @Override
    public boolean isHelp() {
        return help;
    }
    
    @Override
    public boolean isTestnet() {
        return testnet;
    }
    
    @JsonIgnore
    @Parameter(names = {Config.TESTNET_FLAG}, description = Config.Descriptions.TESTNET, arity = 1)
    protected void setTestnet(boolean testnet) {
        this.testnet = testnet;
    }

    @JsonProperty
    @Parameter(names = {"--help", "-h"}, help = true, hidden = true)
    public void setHelp(boolean help) {
        this.help = help;
    }

    @Override
    public int getPort() {
        return port;
    }

    @JsonProperty
    @Parameter(names = {"--port", "-p"}, description = APIConfig.Descriptions.PORT)
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getApiHost() {
        if (remote) {
            return "0.0.0.0";
        }
        
        return apiHost;
    }

    @JsonProperty
    @Parameter(names = {"--api-host"}, description = APIConfig.Descriptions.API_HOST)
    protected void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    @JsonIgnore
    @Parameter(names = {"--remote"}, description = APIConfig.Descriptions.REMOTE, arity = 1)
    protected void setRemote(boolean remote) {
        this.remote = remote;
    }

    @Override
    public List<String> getRemoteLimitApi() {
        return remoteLimitApi;
    }

    @JsonProperty
    @Parameter(names = {"--remote-limit-api"}, description = APIConfig.Descriptions.REMOTE_LIMIT_API)
    protected void setRemoteLimitApi(String remoteLimitApi) {
        this.remoteLimitApi = IotaUtils.splitStringToImmutableList(remoteLimitApi, SPLIT_STRING_TO_LIST_REGEX);
    }

    @Override
    public List<InetAddress> getRemoteTrustedApiHosts() {
        return remoteTrustedApiHosts;
    }

    @JsonProperty
    @Parameter(names = {"--remote-trusted-api-hosts"}, description = APIConfig.Descriptions.REMOTE_TRUSTED_API_HOSTS)
    public void setRemoteTrustedApiHosts(String remoteTrustedApiHosts) {
        List<String> addresses = IotaUtils.splitStringToImmutableList(remoteTrustedApiHosts, SPLIT_STRING_TO_LIST_REGEX);
        List<InetAddress> inetAddresses = addresses.stream().map(host -> {
            try {
                return InetAddress.getByName(host.trim());
            } catch (UnknownHostException e) {
                throw new ParameterException("Invalid value for --remote-trusted-api-hosts address: ", e);
            }
        }).collect(Collectors.toList());

        // always make sure that localhost exists as trusted host
        if(!inetAddresses.contains(Defaults.REMOTE_TRUSTED_API_HOSTS)) {
            inetAddresses.add(Defaults.REMOTE_TRUSTED_API_HOSTS);
        }
        this.remoteTrustedApiHosts = Collections.unmodifiableList(inetAddresses);
    }

    @Override
    public int getMaxFindTransactions() {
        return maxFindTransactions;
    }

    @JsonProperty
    @Parameter(names = {"--max-find-transactions"}, description = APIConfig.Descriptions.MAX_FIND_TRANSACTIONS)
    protected void setMaxFindTransactions(int maxFindTransactions) {
        this.maxFindTransactions = maxFindTransactions;
    }

    @Override
    public int getMaxRequestsList() {
        return maxRequestsList;
    }

    @JsonProperty
    @Parameter(names = {"--max-requests-list"}, description = APIConfig.Descriptions.MAX_REQUESTS_LIST)
    protected void setMaxRequestsList(int maxRequestsList) {
        this.maxRequestsList = maxRequestsList;
    }

    @Override
    public int getMaxGetTrytes() {
        return maxGetTrytes;
    }

    @JsonProperty
    @Parameter(names = {"--max-get-trytes"}, description = APIConfig.Descriptions.MAX_GET_TRYTES)
    protected void setMaxGetTrytes(int maxGetTrytes) {
        this.maxGetTrytes = maxGetTrytes;
    }

    @Override
    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    @JsonProperty
    @Parameter(names = {"--max-body-length"}, description = APIConfig.Descriptions.MAX_BODY_LENGTH)
    protected void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    @Override
    public String getRemoteAuth() {
        return remoteAuth;
    }

    @JsonProperty
    @Parameter(names = {"--remote-auth"}, description = APIConfig.Descriptions.REMOTE_AUTH)
    protected void setRemoteAuth(String remoteAuth) {
        this.remoteAuth = remoteAuth;
    }

    @JsonProperty
    @Parameter(names = {"--neighboring-socket-address"}, description = NetworkConfig.Descriptions.NEIGHBORING_SOCKET_ADDRESS)
    public void setNeighboringSocketAddress(String neighboringSocketAddress) {
        this.neighboringSocketAddress = neighboringSocketAddress;
    }

    @Override
    public String getNeighboringSocketAddress() {
        return neighboringSocketAddress;
    }

    @JsonProperty
    @Parameter(names = {"--neighboring-socket-port", "-t"}, description = NetworkConfig.Descriptions.NEIGHBORING_SOCKET_PORT)
    public void setNeighboringSocketPort(int neighboringSocketPort) {
        this.neighboringSocketPort = neighboringSocketPort;
    }

    @Override
    public int getNeighboringSocketPort() {
        return neighboringSocketPort;
    }

    @Override
    public int getReconnectAttemptIntervalSeconds() {
        return reconnectAttemptIntervalSeconds;
    }

    @JsonProperty
    @Parameter(names = {"--reconnect-attempt-interval-seconds"}, description = NetworkConfig.Descriptions.RECONNECT_ATTEMPT_INTERVAL_SECONDS)
    protected void setReconnectAttemptIntervalSeconds(int reconnectAttemptIntervalSeconds) {
        this.reconnectAttemptIntervalSeconds = reconnectAttemptIntervalSeconds;
    }

    @Override
    public boolean isAutoTetheringEnabled() {
        return autoTetheringEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--auto-tethering"}, description = NetworkConfig.Descriptions.AUTO_TETHERING_ENABLED, arity = 1)
    protected void setAutoTetheringEnabled(boolean autoTetheringEnabled) {
        this.autoTetheringEnabled = autoTetheringEnabled;
    }

    @Override
    public double getpRemoveRequest() {
        return pRemoveRequest;
    }

    @JsonProperty
    @Parameter(names = {"--p-remove-request"}, description = NetworkConfig.Descriptions.P_REMOVE_REQUEST)
    protected void setpRemoveRequest(double pRemoveRequest) {
        this.pRemoveRequest = pRemoveRequest;
    }

    @Override
    public int getSendLimit() {
        return sendLimit;
    }

    @JsonProperty
    @Parameter(names = {"--send-limit"}, description = NetworkConfig.Descriptions.SEND_LIMIT)
    protected void setSendLimit(int sendLimit) {
        this.sendLimit = sendLimit;
    }

    @Override
    public int getMaxNeighbors() {
        return maxNeighbors;
    }

    @JsonProperty
    @Parameter(names = {"--max-neighbors"}, description = NetworkConfig.Descriptions.MAX_NEIGHBORS)
    protected void setMaxNeighbors(int maxNeighbors) {
        this.maxNeighbors = maxNeighbors;
    }

    @Override
    public boolean isDnsRefresherEnabled() {
        return dnsRefresherEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--dns-refresher"}, description = NetworkConfig.Descriptions.DNS_REFRESHER_ENABLED, arity = 1)
    protected void setDnsRefresherEnabled(boolean dnsRefresherEnabled) {
        this.dnsRefresherEnabled = dnsRefresherEnabled;
    }

    @Override
    public boolean isDnsResolutionEnabled() {
        return dnsResolutionEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--dns-resolution"}, description = NetworkConfig.Descriptions.DNS_RESOLUTION_ENABLED, arity = 1)
    protected void setDnsResolutionEnabled(boolean dnsResolutionEnabled) {
        this.dnsResolutionEnabled = dnsResolutionEnabled;
    }

    @Override
    public List<String> getNeighbors() {
        return neighbors;
    }

    @JsonProperty
    @Parameter(names = {"-n", "--neighbors"}, description = NetworkConfig.Descriptions.NEIGHBORS)
    protected void setNeighbors(String neighbors) {
        this.neighbors = IotaUtils.splitStringToImmutableList(neighbors, SPLIT_STRING_TO_LIST_REGEX);
    }

    @Override
    public String getIxiDir() {
        return ixiDir;
    }

    @JsonProperty
    @Parameter(names = {"--ixi-dir"}, description = IXIConfig.Descriptions.IXI_DIR)
    protected void setIxiDir(String ixiDir) {
        this.ixiDir = ixiDir;
    }

    @Override
    public String getDbPath() {
        return dbPath;
    }

    @JsonProperty
    @Parameter(names = {"--db-path"}, description = DbConfig.Descriptions.DB_PATH)
    protected void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public String getDbLogPath() {
        return dbLogPath;
    }

    @JsonProperty
    @Parameter(names = {"--db-log-path"}, description = DbConfig.Descriptions.DB_LOG_PATH)
    protected void setDbLogPath(String dbLogPath) {
        this.dbLogPath = dbLogPath;
    }

    @Override
    public int getDbCacheSize() {
        return dbCacheSize;
    }

    @JsonProperty
    @Parameter(names = {"--db-cache-size"}, description = DbConfig.Descriptions.DB_CACHE_SIZE)
    protected void setDbCacheSize(int dbCacheSize) {
        this.dbCacheSize = dbCacheSize;
    }

    @Override
    public String getMainDb() {
        return mainDb;
    }

    @JsonProperty
    @Parameter(names = {"--db"}, description = DbConfig.Descriptions.MAIN_DB)
    protected void setMainDb(String mainDb) {
        this.mainDb = mainDb;
    }

    @Override
    public boolean isRevalidate() {
        return revalidate;
    }

    @JsonProperty
    @Parameter(names = {"--revalidate"}, description = DbConfig.Descriptions.REVALIDATE, arity = 1)
    protected void setRevalidate(boolean revalidate) {
        this.revalidate = revalidate;
    }

    @Override
    public boolean isRescanDb() {
        return rescanDb;
    }

    @JsonProperty
    @Parameter(names = {"--rescan"}, description = DbConfig.Descriptions.RESCAN_DB, arity = 1)
    protected void setRescanDb(boolean rescanDb) {
        this.rescanDb = rescanDb;
    }

    @Override
    public int getMwm() {
        return Defaults.MWM;
    }

    @Override
    public int getTransactionPacketSize() {
        return Defaults.PACKET_SIZE;
    }

    @Override
    public int getRequestHashSize() {
        return Defaults.REQUEST_HASH_SIZE;
    }

    @Override
    public double getpReplyRandomTip() {
        return pReplyRandomTip;
    }

    @JsonProperty
    @Parameter(names = {"--p-reply-random"}, description = ProtocolConfig.Descriptions.P_REPLY_RANDOM_TIP)
    protected void setpReplyRandomTip(double pReplyRandomTip) {
        this.pReplyRandomTip = pReplyRandomTip;
    }

    @Override
    public double getpDropTransaction() {
        return pDropTransaction;
    }

    @JsonProperty
    @Parameter(names = {"--p-drop-transaction"}, description = ProtocolConfig.Descriptions.P_DROP_TRANSACTION)
    protected void setpDropTransaction(double pDropTransaction) {
        this.pDropTransaction = pDropTransaction;
    }

    @Override
    public double getpSelectMilestoneChild() {
        return pSelectMilestoneChild;
    }

    @JsonProperty
    @Parameter(names = {"--p-select-milestone"}, description = ProtocolConfig.Descriptions.P_SELECT_MILESTONE_CHILD)
    protected void setpSelectMilestoneChild(double pSelectMilestoneChild) {
        this.pSelectMilestoneChild = pSelectMilestoneChild;
    }

    @Override
    public double getpSendMilestone() {
        return pSendMilestone;
    }

    @JsonProperty
    @Parameter(names = {"--p-send-milestone"}, description = ProtocolConfig.Descriptions.P_SEND_MILESTONE)
    protected void setpSendMilestone(double pSendMilestone) {
        this.pSendMilestone = pSendMilestone;
    }

    @Override
    public double getpPropagateRequest() {
        return pPropagateRequest;
    }

    @JsonProperty
    @Parameter(names = {"--p-propagate-request"}, description = ProtocolConfig.Descriptions.P_PROPAGATE_REQUEST)
    protected void setpPropagateRequest(double pPropagateRequest) {
        this.pPropagateRequest = pPropagateRequest;
    }

    @Override
    public boolean getLocalSnapshotsEnabled() {
        return this.localSnapshotsEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-enabled"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_ENABLED,
            arity = 1)
    protected void setLocalSnapshotsEnabled(boolean localSnapshotsEnabled) {
        this.localSnapshotsEnabled = localSnapshotsEnabled;
    }

    @Override
    public boolean getLocalSnapshotsPruningEnabled() {
        return this.localSnapshotsEnabled && this.localSnapshotsPruningEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-pruning-enabled"}, description =
            SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_PRUNING_ENABLED, arity = 1)
    protected void setLocalSnapshotsPruningEnabled(boolean localSnapshotsPruningEnabled) {
        this.localSnapshotsPruningEnabled = localSnapshotsPruningEnabled;
    }

    @Override
    public int getLocalSnapshotsPruningDelay() {
        return this.localSnapshotsPruningDelay;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-pruning-delay"}, description =
            SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_PRUNING_DELAY)
    protected void setLocalSnapshotsPruningDelay(int localSnapshotsPruningDelay) {
        if (localSnapshotsPruningDelay < Defaults.LOCAL_SNAPSHOTS_PRUNING_DELAY_MIN) {
            throw new ParameterException("LOCAL_SNAPSHOTS_PRUNING_DELAY should be at least "
                    + Defaults.LOCAL_SNAPSHOTS_PRUNING_DELAY_MIN
                    + "(found " + localSnapshotsPruningDelay +")");
        }

        this.localSnapshotsPruningDelay = localSnapshotsPruningDelay;
    }

    @Override
    public int getLocalSnapshotsIntervalSynced() {
        return this.localSnapshotsIntervalSynced;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-interval-synced"}, description =
            SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_INTERVAL_SYNCED)
    protected void setLocalSnapshotsIntervalSynced(int localSnapshotsIntervalSynced) {
        if (localSnapshotsIntervalSynced < 1) {
            throw new ParameterException("LOCAL_SNAPSHOTS_INTERVAL_SYNCED should be at least 1 (found " +
                    localSnapshotsIntervalSynced + ")");
        }

        this.localSnapshotsIntervalSynced = localSnapshotsIntervalSynced;
    }

    @Override
    public int getLocalSnapshotsIntervalUnsynced() {
        return this.localSnapshotsIntervalUnsynced;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-interval-unsynced"}, description =
            SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED)
    protected void setLocalSnapshotsIntervalUnsynced(int localSnapshotsIntervalUnsynced) {
        if (localSnapshotsIntervalUnsynced < 1) {
            throw new ParameterException("LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED should be at least 1 (found " +
                    localSnapshotsIntervalUnsynced + ")");
        }

        this.localSnapshotsIntervalUnsynced = localSnapshotsIntervalUnsynced;
    }

    @Override
    public int getLocalSnapshotsDepth() {
        return this.localSnapshotsDepth;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-depth"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_DEPTH)
    protected void setLocalSnapshotsDepth(int localSnapshotsDepth) {
        if (localSnapshotsDepth < Defaults.LOCAL_SNAPSHOTS_DEPTH_MIN) {
            throw new ParameterException("LOCAL_SNAPSHOTS_DEPTH should be at least "
                    + Defaults.LOCAL_SNAPSHOTS_DEPTH_MIN
                    + "(found " + localSnapshotsDepth + ")");
        }

        this.localSnapshotsDepth = localSnapshotsDepth;
    }

    @Override
    public String getLocalSnapshotsBasePath() {
        return this.localSnapshotsBasePath;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-base-path"}, description =
            SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_BASE_PATH)
    protected void setLocalSnapshotsBasePath(String localSnapshotsBasePath) {
        this.localSnapshotsBasePath = localSnapshotsBasePath;
    }

    @Override
    public long getSnapshotTime() {
        return Defaults.SNAPSHOT_TIME;
    }

    @Override
    public String getSnapshotFile() {
        return Defaults.SNAPSHOT_FILE;
    }

    @Override
    public String getSnapshotSignatureFile() {
        return Defaults.SNAPSHOT_SIGNATURE_FILE;
    }

    @Override
    public String getPreviousEpochSpentAddressesFiles() {
        return Defaults.PREVIOUS_EPOCHS_SPENT_ADDRESSES_FILE;
    }

    @Override
    public int getMilestoneStartIndex() {
        return Defaults.MILESTONE_START_INDEX;
    }

    @Override
    public int getMaxMilestoneIndex() {
        return Defaults.MAX_MILESTONE_INDEX;
    }

    @Override
    public int getNumberOfKeysInMilestone() {
        return Defaults.NUMBER_OF_KEYS_IN_A_MILESTONE;
    }

    @Override
    public String getSpentAddressesDbPath() {
        return spentAddressesDbPath;
    }

    @JsonProperty
    @Parameter(names = {"--spent-addresses-db-path"}, description = SnapshotConfig.Descriptions.SPENT_ADDRESSES_DB_PATH)
    protected void setSpentAddressesDbPath(String spentAddressesDbPath) {
        this.spentAddressesDbPath = spentAddressesDbPath;
    }

    @Override
    public String getSpentAddressesDbLogPath() {
        return spentAddressesDbLogPath;
    }

    @JsonProperty
    @Parameter(names = {"--spent-addresses-db-log-path"}, description = SnapshotConfig.Descriptions.SPENT_ADDRESSES_DB_LOG_PATH)
    protected void setSpentAddressesDbLogPath(String spentAddressesDbLogPath) {
        this.spentAddressesDbLogPath = spentAddressesDbLogPath;
    }

    /**
     * Checks if ZMQ is enabled.
     * @return true if zmqEnableTcp or zmqEnableIpc is set.
     */
    @Override
    public boolean isZmqEnabled() {
        return zmqEnableTcp || zmqEnableIpc;
    }

    /**
     * Activates ZMQ to listen on TCP and IPC.
     * @deprecated Use {@link #setZmqEnableTcp(boolean) and/or {@link #setZmqEnableIpc(boolean)}} instead.
     * @param zmqEnabled true if ZMQ should listen in TCP and IPC.
     */
    @Deprecated
    @JsonProperty
    @Parameter(names = "--zmq-enabled", description = ZMQConfig.Descriptions.ZMQ_ENABLED, arity = 1)
    protected void setZmqEnabled(boolean zmqEnabled) {
        this.zmqEnableTcp = zmqEnabled;
        this.zmqEnableIpc = zmqEnabled;
    }

    @Override
    public boolean isZmqEnableTcp() {
        return zmqEnableTcp;
    }

    @JsonProperty
    @Parameter(names = "--zmq-enable-tcp", description = ZMQConfig.Descriptions.ZMQ_ENABLE_TCP, arity = 1)
    public void setZmqEnableTcp(boolean zmqEnableTcp) {
        this.zmqEnableTcp = zmqEnableTcp;
    }

    @Override
    public boolean isZmqEnableIpc() {
        return zmqEnableIpc;
    }

    @JsonProperty
    @Parameter(names = "--zmq-enable-ipc", description = ZMQConfig.Descriptions.ZMQ_ENABLE_IPC, arity = 1)
    public void setZmqEnableIpc(boolean zmqEnableIpc) {
        this.zmqEnableIpc = zmqEnableIpc;
    }

    @Override
    public int getZmqPort() {
        return zmqPort;
    }

    @JsonProperty
    @Parameter(names = "--zmq-port", description = ZMQConfig.Descriptions.ZMQ_PORT)
    protected void setZmqPort(int zmqPort) {
        this.zmqPort = zmqPort;
        this.zmqEnableTcp = true;
    }

    @Override
    public int getZmqThreads() {
        return zmqThreads;
    }

    @JsonProperty
    @Parameter(names = "--zmq-threads", description = ZMQConfig.Descriptions.ZMQ_THREADS)
    protected void setZmqThreads(int zmqThreads) {
        this.zmqThreads = zmqThreads;
    }

    @Override
    public String getZmqIpc() {
        return zmqIpc;
    }

    @JsonProperty
    @Parameter(names = "--zmq-ipc", description = ZMQConfig.Descriptions.ZMQ_IPC)
    protected void setZmqIpc(String zmqIpc) {
        this.zmqIpc = zmqIpc;
        this.zmqEnableIpc = true;
    }

    @Override
    public int getqSizeNode() {
        return qSizeNode;
    }

    @JsonProperty
    @Parameter(names = "--queue-size", description = NetworkConfig.Descriptions.Q_SIZE_NODE)
    protected void setqSizeNode(int qSizeNode) {
        this.qSizeNode = qSizeNode;
    }

    @Override
    public double getpDropCacheEntry() {
        return pDropCacheEntry;
    }

    @JsonProperty
    @Parameter(names = "--p-drop-cache", description = NetworkConfig.Descriptions.P_DROP_CACHE_ENTRY)
    protected void setpDropCacheEntry(double pDropCacheEntry) {
        this.pDropCacheEntry = pDropCacheEntry;
    }

    @Override
    public int getCacheSizeBytes() {
        return cacheSizeBytes;
    }

    @JsonProperty
    @Parameter(names = "--cache-size", description = NetworkConfig.Descriptions.CACHE_SIZE_BYTES)
    protected void setCacheSizeBytes(int cacheSizeBytes) {
        this.cacheSizeBytes = cacheSizeBytes;
    }

    @Override
    public Hash getCoordinator() {
        return Defaults.COORDINATOR;
    }

    @Override
    public int getCoordinatorSecurityLevel() {
        return Defaults.COORDINATOR_SECURITY_LEVEL;
    }

    @Override
    public SpongeFactory.Mode getCoordinatorSignatureMode() {
        return Defaults.COORDINATOR_SIGNATURE_MODE;
    }

    @Override
    public boolean isDontValidateTestnetMilestoneSig() {
        return false;
    }

    @Override
    public int getMaxDepth() {
        return maxDepth;
    }

    @JsonProperty
    @Parameter(names = "--max-depth", description = TipSelConfig.Descriptions.MAX_DEPTH)
    protected void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    @JsonProperty("TIPSELECTION_ALPHA")
    @Parameter(names = "--alpha", description = TipSelConfig.Descriptions.ALPHA)
    protected void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getTipSelectionTimeoutSec() {
        return tipSelectionTimeoutSec;
    }

    @JsonProperty
    @Parameter(names = "--tip-selection-timeout-sec", description = TipSelConfig.Descriptions.TIP_SELECTION_TIMEOUT_SEC)
    protected void setTipSelectionTimeoutSec(int tipSelectionTimeoutSec) {
        this.tipSelectionTimeoutSec = tipSelectionTimeoutSec;
    }

    @Override
    public boolean isTipSolidifierEnabled() {
        return tipSolidifierEnabled;
    }

    @JsonProperty
    @Parameter(names = "--tip-solidifier", description = SolidificationConfig.Descriptions.TIP_SOLIDIFIER,
        arity = 1)
    protected void setTipSolidifierEnabled(boolean tipSolidifierEnabled) {
        this.tipSolidifierEnabled = tipSolidifierEnabled;
    }

    @Override
    public int getBelowMaxDepthTransactionLimit() {
        return maxAnalyzedTransactions;
    }

    @JsonProperty
    @Parameter(names = "--max-analyzed-transactions", 
        description = TipSelConfig.Descriptions.BELOW_MAX_DEPTH_TRANSACTION_LIMIT)
    protected void setBelowMaxDepthTransactionLimit(int maxAnalyzedTransactions) {
        this.maxAnalyzedTransactions = maxAnalyzedTransactions;
    }

    @Override
    public int getPowThreads() {
        return powThreads;
    }

    @JsonProperty
    @Parameter(names = "--pow-threads", description = PearlDiverConfig.Descriptions.POW_THREADS)
    protected void setPowThreads(int powThreads) {
        this.powThreads = powThreads;
    }

    /**
     * Represents the default values primarily used by the {@link BaseIotaConfig} field initialisation.
     */
    public interface Defaults {
        //API
        int PORT = 14265;
        String API_HOST = "localhost";
        List<String> REMOTE_LIMIT_API = IotaUtils.createImmutableList("addNeighbors", "getNeighbors", "removeNeighbors", "attachToTangle", "interruptAttachingToTangle");
        InetAddress REMOTE_TRUSTED_API_HOSTS = InetAddress.getLoopbackAddress();
        List<InetAddress> REMOTE_LIMIT_API_HOSTS = IotaUtils.createImmutableList(REMOTE_TRUSTED_API_HOSTS);
        int MAX_FIND_TRANSACTIONS = 100_000;
        int MAX_REQUESTS_LIST = 1_000;
        int MAX_GET_TRYTES = 10_000;
        int MAX_BODY_LENGTH = 1_000_000;
        String REMOTE_AUTH = "";

        //Network
        String NEIGHBORING_SOCKET_ADDRESS = "0.0.0.0";
        int NEIGHBORING_SOCKET_PORT = 15600;
        int RECONNECT_ATTEMPT_INTERVAL_SECONDS = 60;
        boolean AUTO_TETHERING_ENABLED = false;
        double P_REMOVE_REQUEST = 0.01d;
        int SEND_LIMIT = -1;
        int MAX_NEIGHBORS = 5;
        boolean DNS_REFRESHER_ENABLED = true;
        boolean DNS_RESOLUTION_ENABLED = true;

        //ixi
        String IXI_DIR = "ixi";

        //DB
        String DB_PATH = "mainnetdb";
        String DB_LOG_PATH = "mainnet.log";
        int DB_CACHE_SIZE = 100_000;
        String MAIN_DB = "rocksdb";
        boolean REVALIDATE = false;
        boolean RESCAN_DB = false;

        //Protocol
        double P_REPLY_RANDOM_TIP = 0.66d;
        double P_DROP_TRANSACTION = 0d;
        double P_SELECT_MILESTONE_CHILD = 0.7d;
        double P_SEND_MILESTONE = 0.02d;
        double P_PROPAGATE_REQUEST = 0.01d;
        int MWM = 14;
        int PACKET_SIZE = 1650;
        int REQUEST_HASH_SIZE = 46;
        int QUEUE_SIZE = 1_000;
        double P_DROP_CACHE_ENTRY = 0.02d;
        int CACHE_SIZE_BYTES = 150_000;


        //Zmq
        int ZMQ_THREADS = 1;
        boolean ZMQ_ENABLE_IPC = false;
        String ZMQ_IPC = "ipc://iri";
        boolean ZMQ_ENABLE_TCP = false;
        int ZMQ_PORT = 5556;

        //TipSel
        int MAX_DEPTH = 15;
        double ALPHA = 0.001d;
        int TIP_SELECTION_TIMEOUT_SEC = 60;

        //Tip solidification
        boolean TIP_SOLIDIFIER_ENABLED = false;

        //PearlDiver
        int POW_THREADS = 0;

        //Coo
        Hash COORDINATOR = HashFactory.ADDRESS.create(
                        "EQSAUZXULTTYZCLNJNTXQTQHOMOFZERHTCGTXOLTVAHKSA9OGAZDEKECURBRIXIJWNPFCQIOVFVVXJVD9");
        int COORDINATOR_SECURITY_LEVEL = 2;
        SpongeFactory.Mode COORDINATOR_SIGNATURE_MODE = SpongeFactory.Mode.KERL;
        int NUMBER_OF_KEYS_IN_A_MILESTONE = 23;
        int MAX_MILESTONE_INDEX = 1 << NUMBER_OF_KEYS_IN_A_MILESTONE;

        //Snapshot
        boolean LOCAL_SNAPSHOTS_ENABLED = true;
        boolean LOCAL_SNAPSHOTS_PRUNING_ENABLED = false;

        int LOCAL_SNAPSHOTS_PRUNING_DELAY = 40000;
        int LOCAL_SNAPSHOTS_PRUNING_DELAY_MIN = 10000;
        int LOCAL_SNAPSHOTS_INTERVAL_SYNCED = 10;
        int LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED = 1000;
        int LOCAL_SNAPSHOTS_DEPTH = 100;
        int LOCAL_SNAPSHOTS_DEPTH_MIN = 100;
        String SPENT_ADDRESSES_DB_PATH = "spent-addresses-db";
        String SPENT_ADDRESSES_DB_LOG_PATH = "spent-addresses-log";

        String LOCAL_SNAPSHOTS_BASE_PATH = "mainnet";
        String SNAPSHOT_FILE = "/snapshotMainnet.txt";
        String SNAPSHOT_SIGNATURE_FILE = "/snapshotMainnet.sig";
        String PREVIOUS_EPOCHS_SPENT_ADDRESSES_FILE =
                "/previousEpochsSpentAddresses1.txt /previousEpochsSpentAddresses2.txt " +
                        "/previousEpochsSpentAddresses3.txt";
        long SNAPSHOT_TIME = 1554904800;
        int MILESTONE_START_INDEX = 1050000;
        int BELOW_MAX_DEPTH_TRANSACTION_LIMIT = 20_000;

    }
}
