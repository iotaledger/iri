package com.iota.iri.conf;

import com.beust.jcommander.Parameter;
import com.iota.iri.utils.IotaUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class NetConfig implements IotaConfig {

    protected static final String SPLIT_STRING_TO_LIST_REGEX = ",| ";

    private boolean help;

    //API
    protected int port = Defaults.API_PORT;
    protected String host = Defaults.API_HOST;
    protected List<String> remoteLimitApi = new ArrayList<>();
    protected int maxFindTransactions = Defaults.MAX_FIND_TRANSACTIONS;
    protected int maxRequestList = Defaults.MAX_REQUEST_LIST;
    protected int maxGetTrytes = Defaults.MAX_GET_TRYTES;
    protected int maxBodyLength = Defaults.MAX_BODY_LENGTH;
    protected String remoteAuth = Defaults.REMOTE_AUTH;


    //Network
    protected int udpReceiverPort = Defaults.UDP_RECIEVER_PORT;
    protected int tcpReceiverPort = Defaults.TCP_RECIEVER_PORT;
    protected double pRemoveRequest = Defaults.P_REMOVE_REQUEST;
    protected double pDropCacheEntry = Defaults.P_DROP_CACHE_ENTRY;
    protected int sendLimit = Defaults.SEND_LIMIT;
    protected int maxPeers = Defaults.MAX_PEERS;
    protected boolean dnsRefresherEnabled = Defaults.DNS_REFRESHER_ENABLED;
    protected boolean dnsResolutionEnabled = Defaults.DNS_RESOLUTION_ENABLED;
    protected List<String> neighbors = new ArrayList<>();

    //General
    protected boolean testnet = false;

    //IXI
    protected String ixiDir = Defaults.IXI_DIR;

    //DB
    protected String dbPath = Defaults.DB_PATH;
    protected String dbLogPath = Defaults.DB_LOG_PATH;
    protected int dbCacheSize = Defaults.DB_CACHE_SIZE; //KB
    protected String mainDb = Defaults.ROCKS_DB;
    protected boolean export = Defaults.EXPORT;
    protected boolean revalidate = Defaults.REVALIDATE;
    protected boolean rescanDb = Defaults.RESCAN_DB;

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

    public NetConfig() {
        //empty constructor
    }

    @Override
    public boolean isHelp() {
        return help;
    }

    @Parameter(names = "--help", help = true, hidden = true)
    public void setHelp(boolean help) {
        this.help = help;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Parameter(names = {"--port", "-p"}, description = APIConfig.Descriptions.PORT)
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Parameter(names = {"--host"}, description = APIConfig.Descriptions.HOST)
    protected void setHost(String host) {
        this.host = host;
    }

    @Override
    public List<String> getRemoteLimitApi() {
        return remoteLimitApi;
    }

    @Parameter(names = {"--remote-limit-api"}, variableArity = true, description = APIConfig.Descriptions.REMOTE_LIMIT_API)
    protected void setRemoteLimitApi(List<String> remoteLimitApi) {
        this.remoteLimitApi = remoteLimitApi;
    }

    //  For Jackson file serializer
    protected void setRemoteLimitApi(String remoteLimitApi) {
        this.remoteLimitApi = IotaUtils.splitStringToImmutableList(remoteLimitApi, SPLIT_STRING_TO_LIST_REGEX);
    }


    @Override
    public int getMaxFindTransactions() {
        return maxFindTransactions;
    }

    @Parameter(names = {"--max-find-transactions"}, description = APIConfig.Descriptions.MAX_FIND_TRANSACTIONS)
    protected void setMaxFindTransactions(int maxFindTransactions) {
        this.maxFindTransactions = maxFindTransactions;
    }

    @Override
    public int getMaxRequestList() {
        return maxRequestList;
    }

    @Parameter(names = {"--max-request-list"}, description = APIConfig.Descriptions.MAX_REQUESTS_LIST)
    protected void setMaxRequestList(int maxRequestList) {
        this.maxRequestList = maxRequestList;
    }

    @Override
    public int getMaxGetTrytes() {
        return maxGetTrytes;
    }

    @Parameter(names = {"--max-get-trytes"}, description = APIConfig.Descriptions.MAX_GET_TRYTES)
    protected void setMaxGetTrytes(int maxGetTrytes) {
        this.maxGetTrytes = maxGetTrytes;
    }

    @Override
    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    @Parameter(names = {"--max-body-length"}, description = APIConfig.Descriptions.MAX_BODY_LENGTH)
    protected void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    @Override
    public String getRemoteAuth() {
        return remoteAuth;
    }

    @Parameter(names = {"--remote-auth"}, description = APIConfig.Descriptions.REMOTE_AUTH)
    protected void setRemoteAuth(String remoteAuth) {
        this.remoteAuth = remoteAuth;
    }

    @Override
    public int getUdpReceiverPort() {
        return udpReceiverPort;
    }

    @Parameter(names = {"-u", "--udp-reciever-port"}, description = NetworkConfig.Descriptions.UDP_RECIEVER_PORT)
    public void setUdpReceiverPort(int udpReceiverPort) {
        this.udpReceiverPort = udpReceiverPort;
    }

    @Override
    public int getTcpReceiverPort() {
        return tcpReceiverPort;
    }

    @Parameter(names = {"-t", "--tcp-reciever-port"}, description = NetworkConfig.Descriptions.TCP_RECIEVER_PORT)
    protected void setTcpReceiverPort(int tcpReceiverPort) {
        this.tcpReceiverPort = tcpReceiverPort;
    }

    @Override
    public double getPRemoveRequest() {
        return pRemoveRequest;
    }

    @Parameter(names = {"--p-remove-request"}, description = NetworkConfig.Descriptions.P_REMOVE_REQUEST)
    protected void setpRemoveRequest(double pRemoveRequest) {
        this.pRemoveRequest = pRemoveRequest;
    }

    @Override
    public int getSendLimit() {
        return sendLimit;
    }

    @Parameter(names = {"--send-limit"}, description = NetworkConfig.Descriptions.SEND_LIMIT)
    protected void setSendLimit(int sendLimit) {
        this.sendLimit = sendLimit;
    }

    @Override
    public int getMaxPeers() {
        return maxPeers;
    }

    @Parameter(names = {"--max-peers"}, description = NetworkConfig.Descriptions.MAX_PEERS)
    protected void setMaxPeers(int maxPeers) {
        this.maxPeers = maxPeers;
    }

    @Override
    public boolean isDnsRefresherEnabled() {
        return dnsRefresherEnabled;
    }

    @Parameter(names = {"--dns--refresher"}, description = NetworkConfig.Descriptions.DNS_REFRESHER_ENABLED)
    protected void setDnsRefresherEnabled(boolean dnsRefresherEnabled) {
        this.dnsRefresherEnabled = dnsRefresherEnabled;
    }

    @Override
    public boolean isDnsResolutionEnabled() {
        return dnsResolutionEnabled;
    }

    @Parameter(names = {"--dns-resolution"}, description = NetworkConfig.Descriptions.DNS_RESOLUTION_ENABLED)
    protected void setDnsResolutionEnabled(boolean dnsResolutionEnabled) {
        this.dnsResolutionEnabled = dnsResolutionEnabled;
    }

    @Override
    public List<String> getNeighbors() {
        return neighbors;
    }

    @Parameter(names = {"-n", "--neighbors"}, variableArity = true, description = NetworkConfig.Descriptions.NEIGHBORS)
    protected void setNeighbors(List<String> neighbors) {
        this.neighbors = neighbors;
    }

//  For Jackson file serializer
    protected void setNeighbors(String neighbors) {
        this.neighbors = IotaUtils.splitStringToImmutableList(neighbors, SPLIT_STRING_TO_LIST_REGEX);
    }

    @Override
    public boolean isTestnet() {
        return testnet;
    }

    @Parameter(names = {TESTNET_FLAG}, description = Config.Descriptions.TESTNET)
    protected void setTestnet(boolean testnet) {
        this.testnet = testnet;
    }

    @Override
    public String getIxiDir() {
        return ixiDir;
    }

    @Parameter(names = {"--ixi-dir"}, description = IXIConfig.Descriptions.IXI_DIR)
    protected void setIxiDir(String ixiDir) {
        this.ixiDir = ixiDir;
    }

    @Override
    public String getDbPath() {
        return dbPath;
    }

    @Parameter(names = {"--db-path"}, description = DbConfig.Descriptions.DB_PATH)
    protected void setDbPath(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public String getDbLogPath() {
        return dbLogPath;
    }

    @Parameter(names = {"--db-log-path"}, description = DbConfig.Descriptions.DB_LOG_PATH)
    protected void setDbLogPath(String dbLogPath) {
        this.dbLogPath = dbLogPath;
    }

    @Override
    public int getDbCacheSize() {
        return dbCacheSize;
    }

    @Parameter(names = {"--db-cache-size"}, description = DbConfig.Descriptions.DB_CACHE_SIZE)
    protected void setDbCacheSize(int dbCacheSize) {
        this.dbCacheSize = dbCacheSize;
    }

    @Override
    public String getMainDb() {
        return mainDb;
    }

    @Parameter(names = {"--db"}, description = DbConfig.Descriptions.MAIN_DB)
    protected void setMainDb(String mainDb) {
        this.mainDb = mainDb;
    }

    @Override
    public boolean isExport() {
        return export;
    }


    @Parameter(names = {"--export"}, description = DbConfig.Descriptions.EXPORT)
    protected void setExport(boolean export) {
        this.export = export;
    }

    @Override
    public boolean isRevalidate() {
        return revalidate;
    }

    @Parameter(names = {"--revalidate"}, description = DbConfig.Descriptions.REVALIDATE)
    protected void setRevalidate(boolean revalidate) {
        this.revalidate = revalidate;
    }

    @Override
    public boolean isRescanDb() {
        return rescanDb;
    }

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

    @Parameter(names = {"--p-reply-random"}, description = ProtocolConfig.Descriptions.P_REPLY_RANDOM_TIP)
    protected void setpReplyRandomTip(double pReplyRandomTip) {
        this.pReplyRandomTip = pReplyRandomTip;
    }

    @Override
    public double getPDropTransaction() {
        return pDropTransaction;
    }

    @Parameter(names = {"--p-drop-transaction"}, description = ProtocolConfig.Descriptions.P_DROP_TRANSACTION)
    protected void setpDropTransaction(double pDropTransaction) {
        this.pDropTransaction = pDropTransaction;
    }

    @Override
    public double getpSelectMilestoneChild() {
        return pSelectMilestoneChild;
    }

    @Parameter(names = {"--p-select-milestone"}, description = ProtocolConfig.Descriptions.P_SELECT_MILESTONE)
    protected void setpSelectMilestoneChild(double pSelectMilestoneChild) {
        this.pSelectMilestoneChild = pSelectMilestoneChild;
    }

    @Override
    public double getPSendMilestone() {
        return pSendMilestone;
    }

    @Parameter(names = {"--p-send-milestone"}, description = ProtocolConfig.Descriptions.P_SEND_MILESTONE)
    protected void setpSendMilestone(double pSendMilestone) {
        this.pSendMilestone = pSendMilestone;
    }

    @Override
    public double getPPropagateRequest() {
        return pPropagateRequest;
    }

    @Parameter(names = {"--p-propagate-request"}, description = ProtocolConfig.Descriptions.P_PROPAGATE_REQUEST)
    protected void setpPropagateRequest(double pPropagateRequest) {
        this.pPropagateRequest = pPropagateRequest;
    }

    @Override
    public int getMinimumWeightMagnitude() {
       return Defaults.MWM;
    }


    @Override
    public long getSnapshotTime() {
        return Defaults.GLOBAL_SNAPSHOT_TIME;
    }

    @Override
    //TODO maybe change string to file. Experiment with Jackson before
    public String getSnapshotFile() {
        return Defaults.SNAPSHOT_FILE;
    }

    @Override
    //TODO maybe change string to file. Experiment with Jackson before
    public String getSnapshotSignatureFile() {
        return Defaults.SNAPSHOT_SIG_FILE;
    }

    @Override
    public String getPreviousEpochSpentAddressesFile() {
        return Defaults.PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT;
    }

    @Override
    public String getPreviousEpochSpentAddressesSigFile () {
        return Defaults.PREVIOUS_EPOCH_SPENT_ADDRESSES_SIG;
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

    @Parameter(names = "--zmq-enabled", description = ZMQConfig.Descriptions.ZMQ_ENABLED)
    protected void setZmqEnabled(boolean zmqEnabled) {
        this.zmqEnabled = zmqEnabled;
    }

    @Override
    public int getZmqPort() {
        return zmqPort;
    }

    @Parameter(names = "--zmq-port", description = ZMQConfig.Descriptions.ZMQ_PORT)
    protected void setZmqPort(int zmqPort) {
        this.zmqPort = zmqPort;
    }

    @Override
    public int getZmqThreads() {
        return zmqThreads;
    }

    @Parameter(names = "--zmq-threads", description = ZMQConfig.Descriptions.ZMQ_PORT)
    protected void setZmqThreads(int zmqThreads) {
        this.zmqThreads = zmqThreads;
    }

    @Override
    public String getZmqIpc() {
        return zmqIpc;
    }

    @Parameter(names = "--zmq-ipc", description = ZMQConfig.Descriptions.ZMQ_IPC)
    protected void setZmqIpc(String zmqIpc) {
        this.zmqIpc = zmqIpc;
    }

    @Override
    public int getQSizeNode() {
        return qSizeNode;
    }

    @Parameter(names = "--queue-size", description = ProtocolConfig.Descriptions.Q_SIZE_NODE)
    protected void setQSizeNode(int qSizeNode) {
        this.qSizeNode = qSizeNode;
    }

    @Override
    public double getPDropCacheEntry() {
        return pDropCacheEntry;
    }

    @Parameter(names = "--p-drop-cache", description = ProtocolConfig.Descriptions.P_DROP_CACHE_ENTRY)
    protected void setpDropCacheEntry(double pDropCacheEntry) {
        this.pDropCacheEntry = pDropCacheEntry;
    }

    @Override
    public int getCacheSizeBytes() {
        return cacheSizeBytes;
    }

    @Parameter(names = "--cache-size", description = ProtocolConfig.Descriptions.P_CACHE_SIZE_BYTES)
    protected void setCacheSizeBytes(int cacheSizeBytes) {
        this.cacheSizeBytes = cacheSizeBytes;
    }

    @Override
    public String getCoordinator() {
        return Defaults.COORDINATOR_ADDRESS;
    }

    @Override
    public boolean isValidateTestnetMilestoneSig() {
        return true;
    }

    @Override
    public int getMaxDepth() {
        return maxDepth;
    }

    @Parameter(names = "--max-depth", description = TipSelConfig.Descriptions.MAX_DEPTH)
    protected void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    @Parameter(names = "--alpha", description = TipSelConfig.Descriptions.ALPHA)
    protected void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getBelowMaxDepthTransactionLimit() {
        return maxAnalyzedTransactions;
    }

    @Parameter(names = "--max-analyzed-transactions", description = TipSelConfig.Descriptions.BELOW_MAX_DEPTH_TRANSACTION_LIMIT)
    protected void setBelowMaxDepthTransactionLimit(int maxAnalyzedTransactions) {
        this.maxAnalyzedTransactions = maxAnalyzedTransactions;
    }

    //TODO change to private after refactoring ReplicatorSourceProcessor
    public interface Defaults {
        //API
        int API_PORT = 14265;
        String API_HOST = "localhost";
        int MAX_FIND_TRANSACTIONS = 100_000;
        int MAX_REQUEST_LIST = 1_000;
        int MAX_GET_TRYTES = 10_000;
        int MAX_BODY_LENGTH = 1_000_000;
        String REMOTE_AUTH = "";

        //Network
        int UDP_RECIEVER_PORT = 14600;
        int TCP_RECIEVER_PORT = 15600;
        double P_REMOVE_REQUEST = 0.01d;
        int SEND_LIMIT = -1;
        int MAX_PEERS = 0;
        boolean DNS_REFRESHER_ENABLED = true;
        boolean DNS_RESOLUTION_ENABLED = true;

        //ixi
        String IXI_DIR = "ixi";

        //DB
        String DB_PATH = "mainnetdb";
        String DB_LOG_PATH = "mainnet.log";
        int DB_CACHE_SIZE = 100_000;
        String ROCKS_DB = "rocksdb";
        boolean EXPORT = false;
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

        //Coo
        String COORDINATOR_ADDRESS =
                "KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU";

        //Snapshot
        String SNAPSHOT_FILE = "/snapshotMainnet.txt";
        String SNAPSHOT_SIG_FILE = "/snapshotMainnet.sig";
        String PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT = "/previousEpochsSpentAddresses.txt";
        String PREVIOUS_EPOCH_SPENT_ADDRESSES_SIG = "/previousEpochsSpentAddresses.sig";
        long GLOBAL_SNAPSHOT_TIME = 1517180400;
        int MILESTONE_START_INDEX = 426_550;
        int NUM_KEYS_IN_MILESTONE = 20;
        int MAX_ANALYZED_TXS = 20_000;
    }
}