package com.iota.iri.conf;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.iota.iri.IRI;
import com.iota.iri.utils.IotaUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

/*
  Note: the fields in this class are being deserialized from Jackson so they must follow Java Bean convention.
  Meaning that every field must have a getter that is prefixed with `get` unless it is a boolean and then it should be
  prefixed with `is`.
 */
public abstract class BaseIotaConfig implements IotaConfig {

    protected static final String SPLIT_STRING_TO_LIST_REGEX = ",| ";

    private boolean help;

    //API
    protected int port = Defaults.API_PORT;
    protected String apiHost = Defaults.API_HOST;
    protected List<String> remoteLimitApi = Defaults.REMOTE_LIMIT_API;
    protected int maxFindTransactions = Defaults.MAX_FIND_TRANSACTIONS;
    protected int maxRequestsList = Defaults.MAX_REQUESTS_LIST;
    protected int maxGetTrytes = Defaults.MAX_GET_TRYTES;
    protected int maxBodyLength = Defaults.MAX_BODY_LENGTH;
    protected String remoteAuth = Defaults.REMOTE_AUTH;
    protected boolean enableRemoteAuth = Defaults.ENABLE_REMOTE_AUTH;
    //We don't have a REMOTE config but we have a remote flag. We must add a field for JCommander
    private boolean remote;


    //Network
    protected int udpReceiverPort = Defaults.UDP_RECEIVER_PORT;
    protected int tcpReceiverPort = Defaults.TCP_RECEIVER_PORT;
    protected double pRemoveRequest = Defaults.P_REMOVE_REQUEST;
    protected double pDropCacheEntry = Defaults.P_DROP_CACHE_ENTRY;
    protected int sendLimit = Defaults.SEND_LIMIT;
    protected int maxPeers = Defaults.MAX_PEERS;
    protected boolean dnsRefresherEnabled = Defaults.DNS_REFRESHER_ENABLED;
    protected boolean dnsResolutionEnabled = Defaults.DNS_RESOLUTION_ENABLED;
    protected List<String> neighbors = new ArrayList<>();

    //IXI
    protected String ixiDir = Defaults.IXI_DIR;

    //Node
    protected boolean wasmSupport = Defaults.WASM_SUPPORT;
    protected boolean streamingGraphSupport = Defaults.STREAMING_GRAPH_SUPPORT;
    protected long numBlocksPerPeriod = Defaults.NUM_BLOCKS_PER_PERIOD;

    //DB
    protected String dbPath = Defaults.DB_PATH;
    protected String dbLogPath = Defaults.DB_LOG_PATH;
    protected int dbCacheSize = Defaults.DB_CACHE_SIZE; //KB
    protected String mainDb = Defaults.ROCKS_DB;
    protected boolean revalidate = Defaults.REVALIDATE;
    protected boolean rescanDb = Defaults.RESCAN_DB;
    protected String graphDbPath = Defaults.GRAPH_DB_PATH;
    protected boolean enableBatchTxns = Defaults.ENABLE_BATCH_TXNS;
    protected boolean enableIPFSTxns = Defaults.ENABLE_IPFS_TXNS;
    protected boolean enableCompressionTxns = Defaults.ENABLE_COMPRESSION_TXNS;

    //Protocol
    protected double pReplyRandomTip = Defaults.P_REPLY_RANDOM_TIP;
    protected double pDropTransaction = Defaults.P_DROP_TRANSACTION;
    protected double pSelectMilestoneChild = Defaults.P_SELECT_MILESTONE_CHILD;
    protected double pSendMilestone = Defaults.P_SEND_MILESTONE;
    protected double pPropagateRequest = Defaults.P_PROPAGATE_REQUEST;

    //ZMQ
    protected boolean zmqEnabled = Defaults.ZMQ_ENABLED;
    protected int zmqPort = Defaults.ZMQ_PORT;
    protected int zmqThreads = Defaults.ZMQ_THREADS;
    protected String zmqIpc = Defaults.ZMQ_IPC;
    protected int qSizeNode = Defaults.QUEUE_SIZE;
    protected int cacheSizeBytes = Defaults.CACHE_SIZE_BYTES;


    //Tip Selection
    protected int maxDepth = Defaults.MAX_DEPTH;
    protected double alpha = Defaults.ALPHA;
    private int maxAnalyzedTransactions = Defaults.MAX_ANALYZED_TXS;
    private String weightCalAlgo = Defaults.WEIGHT_CAL_ALGO;
    private String entryPointSelAlgo = Defaults.ENTRY_POINT_CAL_ALGO;
    private String tipSelectorAlgo = Defaults.TIP_SELECTOR_ALGO;
    private String confluxScoreAlgo = Defaults.CONFLUX_SCORE_ALGO;
    private String walkValidator = Defaults.WALK_VALIDATOR;
    private String ledgerValidator = Defaults.LEDGER_VALIDATOR;


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

    private static BaseIotaConfig config;

    // TODO make this thread safe
    public static void setInstance(BaseIotaConfig cfg) 
    {
        if (config == null)
        {
            config = cfg;
        }
    }
    public static BaseIotaConfig getInstance() {
        if(config == null)
        {
            config = new TestnetConfig();
        }
        return config; 
    }

    public BaseIotaConfig() {
        //empty constructor
    }

    @Override
    public JCommander parseConfigFromArgs(String[] args) throws ParameterException {
        //One can invoke help via INI file (feature/bug) so we always create JCommander even if args is empty
        JCommander jCommander = JCommander.newBuilder()
                .addObject(this)
                //This is in order to enable the `--conf` and `--testnet` option
                .acceptUnknownOptions(true)
                .allowParameterOverwriting(true)
                //This is the first line of JCommander Usage
                .programName("java -jar iri-" + IRI.VERSION + ".jar")
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

    @JsonProperty
    @Parameter(names = {"--help", "-h"} , help = true, hidden = true)
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
        return apiHost;
    }

    @JsonProperty
    @Parameter(names = {"--api-host"}, description = APIConfig.Descriptions.API_HOST)
    protected void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    @JsonProperty
    @Parameter(names = {"--remote"}, description = APIConfig.Descriptions.REMOTE)
    protected void setRemote(boolean remote) {
        this.apiHost = "0.0.0.0";
        this.remote = remote;
    }

    public boolean isRemote() {
            return remote;
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

    @Override
    public boolean getEnableRemoteAuth() {
        return enableRemoteAuth;
    }

    @JsonProperty
    @Parameter(names = {"--enable-remote-auth"}, description = APIConfig.Descriptions.ENABLE_REMOTE_AUTH)
    protected void setEnableRemoteAuth(boolean enableRemoteAuth) {
        this.enableRemoteAuth = enableRemoteAuth;
    }

    @Override
    public int getUdpReceiverPort() {
        return udpReceiverPort;
    }

    @JsonProperty
    @Parameter(names = {"-u", "--udp-receiver-port"}, description = NetworkConfig.Descriptions.UDP_RECEIVER_PORT)
    public void setUdpReceiverPort(int udpReceiverPort) {
        this.udpReceiverPort = udpReceiverPort;
    }

    @Override
    public int getTcpReceiverPort() {
        return tcpReceiverPort;
    }

    @JsonProperty
    @Parameter(names = {"-t", "--tcp-receiver-port"}, description = NetworkConfig.Descriptions.TCP_RECEIVER_PORT)
    protected void setTcpReceiverPort(int tcpReceiverPort) {
        this.tcpReceiverPort = tcpReceiverPort;
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
    public int getMaxPeers() {
        return maxPeers;
    }

    @JsonProperty
    @Parameter(names = {"--max-peers"}, description = NetworkConfig.Descriptions.MAX_PEERS)
    protected void setMaxPeers(int maxPeers) {
        this.maxPeers = maxPeers;
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
    public String getGraphDbPath() {
        return graphDbPath;
    }

    @JsonProperty
    @Parameter(names = {"--graph-db-path"}, description = DbConfig.Descriptions.GRAPH_DB_PATH)
    protected void setGraphDbPath(String graphDbPath) {
        this.graphDbPath = graphDbPath;
    }

    @Override
    public boolean isEnableBatchTxns() {
        return enableBatchTxns;
    }

    @JsonProperty
    @Parameter(names = {"--batch-txns"}, description = DbConfig.Descriptions.ENABLE_BATCH_TXNS)
    protected void setEnableBatchTxns(boolean enableBatchTxns) {
        this.enableBatchTxns = enableBatchTxns;
    }

    @Override
    public boolean isEnableIPFSTxns() {
        return enableIPFSTxns;
    }

    @JsonProperty
    @Parameter(names = {"--ipfs-txns"}, description = DbConfig.Descriptions.ENABLE_IPFS_TXNS, arity = 1)
    protected void setEnableIPFSTxns(boolean enableIPFSTxns) {
        this.enableIPFSTxns = enableIPFSTxns;
    }

    @Override
    public boolean isEnableCompressionTxns() {
        return enableCompressionTxns;
    }

    @JsonProperty
    @Parameter(names = {"--compression-txns"}, description = DbConfig.Descriptions.ENABLE_COMPRESSION_TXNS)
    protected void setEnableCompressionTxns(boolean enableCompressionTxns) {
        this.enableCompressionTxns = enableCompressionTxns;
    }

    @Override
    public boolean isRevalidate() {
        return revalidate;
    }

    @JsonProperty
    @Parameter(names = {"--revalidate"}, description = DbConfig.Descriptions.REVALIDATE)
    protected void setRevalidate(boolean revalidate) {
        this.revalidate = revalidate;
    }

    @Override
    public boolean isRescanDb() {
        return rescanDb;
    }

    @JsonProperty
    @Parameter(names = {"--rescan"}, description = DbConfig.Descriptions.RESCAN_DB)
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
        return Defaults.REQ_HASH_SIZE;
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
    @Parameter(names = {"--p-select-milestone"}, description = ProtocolConfig.Descriptions.P_SELECT_MILESTONE)
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
    @Parameter(names = {"--local-snapshots-enabled"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_ENABLED)
    protected void setLocalSnapshotsEnabled(boolean localSnapshotsEnabled) {
        this.localSnapshotsEnabled = localSnapshotsEnabled;
    }

    @Override
    public boolean getLocalSnapshotsPruningEnabled() {
        return this.localSnapshotsPruningEnabled;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-pruning-enabled"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_PRUNING_ENABLED)
    protected void setLocalSnapshotsPruningEnabled(boolean localSnapshotsPruningEnabled) {
        this.localSnapshotsPruningEnabled = localSnapshotsPruningEnabled;
    }

    @Override
    public int getLocalSnapshotsPruningDelay() {
        return this.localSnapshotsPruningDelay;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-pruning-delay"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_PRUNING_DELAY)
    protected void setLocalSnapshotsPruningDelay(int localSnapshotsPruningDelay) {
        this.localSnapshotsPruningDelay = localSnapshotsPruningDelay;
    }

    @Override
    public int getLocalSnapshotsIntervalSynced() {
        return this.localSnapshotsIntervalSynced;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-interval-synced"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_INTERVAL_SYNCED)
    protected void setLocalSnapshotsIntervalSynced(int localSnapshotsIntervalSynced) {
        this.localSnapshotsIntervalSynced = localSnapshotsIntervalSynced;
    }

    @Override
    public int getLocalSnapshotsIntervalUnsynced() {
        return this.localSnapshotsIntervalUnsynced;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-interval-unsynced"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED)
    protected void setLocalSnapshotsIntervalUnsynced(int localSnapshotsIntervalUnsynced) {
        this.localSnapshotsIntervalUnsynced = localSnapshotsIntervalUnsynced;
    }

    @Override
    public int getLocalSnapshotsDepth() {
        return this.localSnapshotsDepth;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-depth"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_DEPTH)
    protected void setLocalSnapshotsDepth(int localSnapshotsDepth) {
        this.localSnapshotsDepth = localSnapshotsDepth;
    }

    @Override
    public String getLocalSnapshotsBasePath() {
        return this.localSnapshotsBasePath;
    }

    @JsonProperty
    @Parameter(names = {"--local-snapshots-base-path"}, description = SnapshotConfig.Descriptions.LOCAL_SNAPSHOTS_BASE_PATH)
    protected void setLocalSnapshotsBasePath(String localSnapshotsBasePath) {
        this.localSnapshotsBasePath = localSnapshotsBasePath;
    }

    @Override
    public long getSnapshotTime() {
        return Defaults.GLOBAL_SNAPSHOT_TIME;
    }

    @Override
    public String getSnapshotFile() {
        return Defaults.SNAPSHOT_FILE;
    }

    @Override
    public String getSnapshotSignatureFile() {
        return Defaults.SNAPSHOT_SIG_FILE;
    }

    @Override
    public String getPreviousEpochSpentAddressesFiles() {
        return Defaults.PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT;
    }

    @Override
    public int getMilestoneStartIndex() {
        return Defaults.MILESTONE_START_INDEX;
    }

    @Override
    public int getNumberOfKeysInMilestone() {
        return Defaults.NUM_KEYS_IN_MILESTONE;
    }

    @Override
    public boolean isZmqEnabled() {
        return zmqEnabled;
    }

    @JsonProperty
    @Parameter(names = "--zmq-enabled", description = ZMQConfig.Descriptions.ZMQ_ENABLED)
    protected void setZmqEnabled(boolean zmqEnabled) {
        this.zmqEnabled = zmqEnabled;
    }

    @Override
    public int getZmqPort() {
        return zmqPort;
    }

    @JsonProperty
    @Parameter(names = "--zmq-port", description = ZMQConfig.Descriptions.ZMQ_PORT)
    protected void setZmqPort(int zmqPort) {
        this.zmqPort = zmqPort;
    }

    @Override
    public int getZmqThreads() {
        return zmqThreads;
    }

    @JsonProperty
    @Parameter(names = "--zmq-threads", description = ZMQConfig.Descriptions.ZMQ_PORT)
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
    public String getCoordinator() {
        return Defaults.COORDINATOR_ADDRESS;
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
    public boolean getWASMSupport() {
        return wasmSupport;
    }

    @JsonProperty("ENABLE_WASM")
    @Parameter(names = "--enable-wasm", description = NodeConfig.Descriptions.ENABLE_WASMVM)
    protected void getWASMSupport(Boolean wasmSupport) {
        this.wasmSupport = wasmSupport;
    }

    @Override
    public boolean getStreamingGraphSupport() {
        return streamingGraphSupport;
    }

    @JsonProperty("STREAMING_GRAPH")
    @Parameter(names = "--enable-streaming-graph", description = NodeConfig.Descriptions.STREAMING_GRAPH)
    public void setStreamingGraphSupport(Boolean streamingGraphSupport) {
        this.streamingGraphSupport = streamingGraphSupport;
    }

    @Override
    public long getNumBlocksPerPeriod() {
        return numBlocksPerPeriod;
    }

    @JsonProperty("PERIOD_SIZE")
    @Parameter(names = "--num-blocks-per-period", description = NodeConfig.Descriptions.PERIOD_SIZE)
    public void setNumBlocksPerPeriod(Long numBlocksPerPeriod) {
        this.numBlocksPerPeriod = numBlocksPerPeriod;
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
    public int getBelowMaxDepthTransactionLimit() {
        return maxAnalyzedTransactions;
    }
    
    @JsonProperty
    @Parameter(names = "--max-analyzed-transactions", description = TipSelConfig.Descriptions.BELOW_MAX_DEPTH_TRANSACTION_LIMIT)
    protected void setBelowMaxDepthTransactionLimit(int maxAnalyzedTransactions) {
        this.maxAnalyzedTransactions = maxAnalyzedTransactions;
    }

    @Override
    public String getWeightCalAlgo() {
        return weightCalAlgo;
    }

    @JsonProperty
    @Parameter(names = "--weight-calculation-algorithm", description = TipSelConfig.Descriptions.WEIGHT_CAL_ALGO)
    protected void setWeightCalAlgo(String weightCalAlgo) {
        this.weightCalAlgo = weightCalAlgo;
    }

    @Override
    public String getEntryPointSelector() {
        return entryPointSelAlgo;
    }

    @JsonProperty
    @Parameter(names = "--entrypoint-selector-algorithm", description = TipSelConfig.Descriptions.ENTRY_POINT_SEL_ALGO)
    protected void setEntryPointSelector(String entryPointSelAlgo) {
        this.entryPointSelAlgo = entryPointSelAlgo;
    }

    @Override
    public String getTipSelector() {
        return tipSelectorAlgo;
    }

    @JsonProperty
    @Parameter(names = "--conflux-score-algo", description = TipSelConfig.Descriptions.CONFLUX_SCORE_ALGO)
    public void setConfluxScoreAlgo(String confluxScoreAlgo) {
        this.confluxScoreAlgo = confluxScoreAlgo;
    }

    @Override
    public String getConfluxScoreAlgo() {
        return confluxScoreAlgo;
    }

    @JsonProperty
    @Parameter(names = "--tip-sel-algo", description = TipSelConfig.Descriptions.TIP_SEL_ALGO)
    protected void setTipSelector(String tipSelectorAlgo) {
        this.tipSelectorAlgo = tipSelectorAlgo;
    }

    @Override
    public String getWalkValidator() {
        return walkValidator;
    }

    @JsonProperty
    @Parameter(names = "--walk-validator", description = TipSelConfig.Descriptions.WALK_VALIDATOR)
    protected void setWalkValidator(String walkValidator) {
        this.walkValidator = walkValidator;
    }

    @Override
    public String getLedgerValidator() {
        return ledgerValidator;
    }

    @JsonProperty
    @Parameter(names = "--ledger-validator", description = TipSelConfig.Descriptions.LEDGER_VALIDATOR)
    protected void setLedgerValidator(String ledgerValidator) {
        this.ledgerValidator = ledgerValidator;
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

    public interface Defaults {
        //API
        int API_PORT = 14265;
        String API_HOST = "localhost";
        List<String> REMOTE_LIMIT_API = IotaUtils.createImmutableList("addNeighbors", "getNeighbors", "removeNeighbors", "attachToTangle", "interruptAttachingToTangle");
        int MAX_FIND_TRANSACTIONS = 100_000;
        int MAX_REQUESTS_LIST = 1_000;
        int MAX_GET_TRYTES = 10_000;
        int MAX_BODY_LENGTH = 1_000_000;
        String REMOTE_AUTH = "";
        boolean ENABLE_REMOTE_AUTH = false;

        //Network
        int UDP_RECEIVER_PORT = 14600;
        int TCP_RECEIVER_PORT = 15600;
        double P_REMOVE_REQUEST = 0.01d;
        int SEND_LIMIT = -1;
        int MAX_PEERS = 0;
        boolean DNS_REFRESHER_ENABLED = true;
        boolean DNS_RESOLUTION_ENABLED = true;

        //ixi
        String IXI_DIR = "ixi";

        // Node
        boolean WASM_SUPPORT = false;
        boolean STREAMING_GRAPH_SUPPORT = false;
        long NUM_BLOCKS_PER_PERIOD = 100;

        //DB
        String DB_PATH = "mainnetdb";
        String DB_LOG_PATH = "mainnet.log";
        int DB_CACHE_SIZE = 100_000;
        String ROCKS_DB = "rocksdb";
        boolean REVALIDATE = false;
        boolean RESCAN_DB = false;
        String GRAPH_DB_PATH = "";
        boolean ENABLE_BATCH_TXNS = false;
        boolean ENABLE_IPFS_TXNS = true;
        boolean ENABLE_COMPRESSION_TXNS = false;

        //Protocol
        double P_REPLY_RANDOM_TIP = 0.66d;
        double P_DROP_TRANSACTION = 0d;
        double P_SELECT_MILESTONE_CHILD = 0.7d;
        double P_SEND_MILESTONE = 0.02d;
        double P_PROPAGATE_REQUEST = 0.01d;
        int MWM = 14;
        int PACKET_SIZE = 1650;
        int REQ_HASH_SIZE = 46;
        int QUEUE_SIZE = 1_000;
        double P_DROP_CACHE_ENTRY = 0.02d;
        int CACHE_SIZE_BYTES = 150_000;



        //Zmq
        int ZMQ_THREADS = 1;
        String ZMQ_IPC = "ipc://iri";
        boolean ZMQ_ENABLED = false;
        int ZMQ_PORT = 5556;

        //TipSel
        int MAX_DEPTH = 15;
        double ALPHA = 0.001d;
        String WEIGHT_CAL_ALGO = "CUM_WEIGHT";
        String ENTRY_POINT_CAL_ALGO = "DEFAULT";
        String TIP_SELECTOR_ALGO = "MCMC";
        String CONFLUX_SCORE_ALGO = "CUM_WEIGHT";
        String WALK_VALIDATOR = "DEFAULT";
        String LEDGER_VALIDATOR = "DEFAULT";

        //PearlDiver
        int POW_THREADS = 0;

        //Coo
        String COORDINATOR_ADDRESS =
                "KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU";

        //Snapshot
        boolean LOCAL_SNAPSHOTS_ENABLED = true;
        boolean LOCAL_SNAPSHOTS_PRUNING_ENABLED = true;
        int LOCAL_SNAPSHOTS_PRUNING_DELAY = 50000;
        int LOCAL_SNAPSHOTS_INTERVAL_SYNCED = 10;
        int LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED = 1000;
        String LOCAL_SNAPSHOTS_BASE_PATH = "mainnet";
        int LOCAL_SNAPSHOTS_DEPTH = 100;
        String SNAPSHOT_FILE = "/snapshotMainnet.txt";
        String SNAPSHOT_SIG_FILE = "/snapshotMainnet.sig";
        String PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT =
                "/previousEpochsSpentAddresses1.txt /previousEpochsSpentAddresses2.txt";
        long GLOBAL_SNAPSHOT_TIME = 1537203600;
        int MILESTONE_START_INDEX = 774_805;
        int NUM_KEYS_IN_MILESTONE = 20;
        int MAX_ANALYZED_TXS = 20_000;
    }
}
