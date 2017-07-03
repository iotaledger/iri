package com.iota.iri.network;

import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.iota.iri.Milestone;
import com.iota.iri.TransactionValidator;
import com.iota.iri.utils.Converter;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.storage.Tangle;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

/**
 * The class node is responsible for managing Thread's connection.
 */
public class Node {

    private static final Logger log = LoggerFactory.getLogger(Node.class);


    public  static final int TRANSACTION_PACKET_SIZE = 1650;
    private static final int QUEUE_SIZE = 1000;
    private static final int RECV_QUEUE_SIZE = 1000;
    private static final int REPLY_QUEUE_SIZE = 1000;
    private static final int PAUSE_BETWEEN_TRANSACTIONS = 1;
    public  static final int REQUEST_HASH_SIZE = 46;
    private static double P_SELECT_MILESTONE;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();
    private final ConcurrentSkipListSet<TransactionViewModel> broadcastQueue = weightQueue();
    private final ConcurrentSkipListSet<Pair<TransactionViewModel,Neighbor>> receiveQueue = weightQueueTxPair();
    private final ConcurrentSkipListSet<Pair<Hash,Neighbor>> replyQueue = weightQueueHashPair();


    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);
    private final DatagramPacket tipRequestingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);

    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final Configuration configuration;
    private final Tangle tangle;
    private final TipsViewModel tipsViewModel;
    private final TransactionValidator transactionValidator;
    private final Milestone milestone;
    private final TransactionRequester transactionRequester;
    private final MessageQ messageQ;

    private double P_DROP_TRANSACTION;
    private static final SecureRandom rnd = new SecureRandom();
    private double P_SEND_MILESTONE;
    private double P_REPLY_RANDOM_TIP;
    private double P_PROPAGATE_REQUEST;



    private final LRUHashCache recentSeenHashes = new LRUHashCache(5000);
    private final LRUByteCache recentSeenBytes = new LRUByteCache(15000);

    private static AtomicLong recentSeenBytesMissCount = new AtomicLong(0L);
    private static AtomicLong recentSeenBytesHitCount = new AtomicLong(0L);

    private static long sendLimit = -1;
    private static AtomicLong sendPacketsCounter = new AtomicLong(0L);
    private static AtomicLong sendPacketsTimer = new AtomicLong(0L);

    public static final ConcurrentSkipListSet<String> rejectedAddresses = new ConcurrentSkipListSet<String>();
    private DatagramSocket udpSocket;

    public Node(final Configuration configuration,
                final Tangle tangle,
                final TransactionValidator transactionValidator,
                final TransactionRequester transactionRequester,
                final TipsViewModel tipsViewModel,
                final Milestone milestone,
                final MessageQ messageQ
                ) {
        this.configuration = configuration;
        this.tangle = tangle;
        this.transactionValidator = transactionValidator;
        this.transactionRequester = transactionRequester;
        this.tipsViewModel = tipsViewModel;
        this.milestone = milestone;
        this.messageQ = messageQ;
    }

    public void init() throws Exception {

        P_DROP_TRANSACTION = configuration.doubling(Configuration.DefaultConfSettings.P_DROP_TRANSACTION.name());
        P_SELECT_MILESTONE = configuration.doubling(Configuration.DefaultConfSettings.P_SELECT_MILESTONE_CHILD.name());
        P_SEND_MILESTONE = configuration.doubling(Configuration.DefaultConfSettings.P_SEND_MILESTONE.name());
        P_REPLY_RANDOM_TIP = configuration.doubling(Configuration.DefaultConfSettings.P_REPLY_RANDOM_TIP.name());
        P_PROPAGATE_REQUEST = configuration.doubling(Configuration.DefaultConfSettings.P_PROPAGATE_REQUEST.name());
        sendLimit = (long) ( (configuration.doubling(Configuration.DefaultConfSettings.SEND_LIMIT.name()) * 1000000) / (TRANSACTION_PACKET_SIZE * 8) );

        Arrays.stream(configuration.string(Configuration.DefaultConfSettings.NEIGHBORS).split(" ")).distinct()
                .filter(s -> !s.isEmpty()).map(Node::uri).map(Optional::get).peek(u -> {
                    if (!"udp".equals(u.getScheme()) && !"tcp".equals(u.getScheme()) || (new InetSocketAddress(u.getHost(), u.getPort()).getAddress() == null)) {
                        log.error("CONFIGURATION ERROR: '{}' is not a valid uri schema or resolvable address.", u);
                    }})
                .filter(u -> ("udp".equals(u.getScheme()) || "tcp".equals(u.getScheme())) && (new InetSocketAddress(u.getHost(), u.getPort()).getAddress()) != null)
                .map(u -> "tcp".equals(u.getScheme())? new TCPNeighbor(new InetSocketAddress(u.getHost(), u.getPort()),true):
                    new UDPNeighbor(new InetSocketAddress(u.getHost(), u.getPort()), udpSocket,true))
                .peek(u -> {
                log.info("-> Adding neighbor : {} ", u.getAddress());
                messageQ.publish("-> Adding Neighbor : %s",u.getAddress());
        }).forEach(neighbors::add);

        executor.submit(spawnBroadcasterThread());
        executor.submit(spawnTipRequesterThread());
        executor.submit(spawnNeighborDNSRefresherThread());
        executor.submit(spawnProcessReceivedThread());
        executor.submit(spawnReplyToRequestThread());

        tipsViewModel.loadTipHashes(tangle);
        executor.shutdown();
    }

    public void setUDPSocket(final DatagramSocket socket) {
        this.udpSocket = socket;
    }

    public DatagramSocket getUdpSocket() {
        return udpSocket;
    }

    private final Map<String, String> neighborIpCache = new HashMap<>();
    
    private Runnable spawnNeighborDNSRefresherThread() {
        return () -> {

            log.info("Spawning Neighbor DNS Refresher Thread");

            while (!shuttingDown.get()) {
                int dnsCounter = 0;
                log.info("Checking Neighbors' Ip...");

                try {
                    neighbors.forEach(n -> {
                        final String hostname = n.getAddress().getHostName();
                        checkIp(hostname).ifPresent(ip -> {
                            log.info("DNS Checker: Validating DNS Address '{}' with '{}'", hostname, ip);
                            messageQ.publish("dnscv %s %s", hostname, ip);
                            final String neighborAddress = neighborIpCache.get(hostname);

                            if (neighborAddress == null) {
                                neighborIpCache.put(hostname, ip);
                            } else {
                                if (neighborAddress.equals(ip)) {
                                    log.info("{} seems fine.", hostname);
                                    messageQ.publish("dnscc %s", hostname);
                                } else {
                                    log.info("IP CHANGED for {}! Updating...", hostname);
                                    messageQ.publish("dnscu %s", hostname);
                                    String protocol = (n instanceof TCPNeighbor) ? "tcp://" : "udp://";
                                    String port = ":" + n.getAddress().getPort();

                                    uri(protocol + hostname + port).ifPresent(uri -> {
                                        removeNeighbor(uri, n.isFlagged());

                                        uri(protocol + ip + port).ifPresent(nuri -> {
                                            Neighbor neighbor = newNeighbor(nuri, n.isFlagged());
                                            addNeighbor(neighbor);
                                            neighborIpCache.put(hostname, ip);
                                        });
                                    });
                                }
                            }
                        });
                    });

                    while(dnsCounter++ < 60*30 && !shuttingDown.get()) {
                        Thread.sleep(1000);
                    }
                } catch (final Exception e) {
                    log.error("Neighbor DNS Refresher Thread Exception:", e);
                }
            }
            log.info("Shutting down Neighbor DNS Resolver Thread");
        };
    }

    private Optional<String> checkIp(final String dnsName) {
        
        if (StringUtils.isEmpty(dnsName)) {
            return Optional.empty();
        }
        
        InetAddress inetAddress;
        try {
            inetAddress = java.net.InetAddress.getByName(dnsName);
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
        
        final String hostAddress = inetAddress.getHostAddress();
        
        if (StringUtils.equals(dnsName, hostAddress)) { // not a DNS...
            return Optional.empty();
        }
        
        return Optional.of(hostAddress);
    }
    public void preProcessReceivedData(byte[] receivedData, SocketAddress senderAddress, String uriScheme) {
        TransactionViewModel receivedTransactionViewModel = null;
        Hash receivedTransactionHash = null;


        boolean addressMatch = false;
        for (final Neighbor neighbor : getNeighbors()) {
            addressMatch = neighbor.matches(senderAddress);
            if (addressMatch) {
                //Validate transaction
                neighbor.incAllTransactions();
                if (rnd.nextDouble() < P_DROP_TRANSACTION) {
                    //log.info("Randomly dropping transaction. Stand by... ");
                    break;
                }
                try {

                    //Transaction bytes

                    //final int byteHash = ByteBuffer.wrap(receivedData, 0, TransactionViewModel.SIZE).hashCode();
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    digest.update(receivedData, 0, TransactionViewModel.SIZE);
                    ByteBuffer byteHash = ByteBuffer.wrap(digest.digest());
                    
                    //check if cached
                    synchronized (recentSeenBytes) {
                        receivedTransactionHash = recentSeenBytes.get(byteHash);
                    }

                    if (receivedTransactionHash == null) {
                        //if not, then validate
                        receivedTransactionViewModel = TransactionValidator.validate(receivedData, transactionValidator.getMinWeightMagnitude());
                        receivedTransactionHash = receivedTransactionViewModel.getHash();

                        //if valid - add to receive queue (receivedTransactionViewModel, neighbor)
                        addReceivedDataToReceiveQueue(receivedTransactionViewModel, neighbor);

                        synchronized (recentSeenBytes) {
                            recentSeenBytes.set(byteHash, receivedTransactionHash);
                        }

                        recentSeenBytesMissCount.getAndIncrement();

                    }
                    else {
                        recentSeenBytesHitCount.getAndIncrement();
                    }

                    if (((recentSeenBytesMissCount.get() + recentSeenBytesHitCount.get()) % 50000L == 0)) {
                        log.info("RecentSeenBytes cache hit/miss ratio: "+recentSeenBytesHitCount.get()+"/"+recentSeenBytesMissCount.get());
                        messageQ.publish("hmr %d/%d",recentSeenBytesHitCount.get(), recentSeenBytesMissCount.get());
                        recentSeenBytesMissCount.set(0L);
                        recentSeenBytesHitCount.set(0L);
                    }
                    
                } catch (NoSuchAlgorithmException e) {
                    log.error("MessageDigest: "+e);
                } catch (final RuntimeException e) {
                    log.error(e.getMessage());
                    log.error("Received an Invalid TransactionViewModel. Dropping it...");
                    neighbor.incInvalidTransactions();
                    break;
                }

                //Request bytes

                //add request to reply queue (requestedHash, neighbor)
                Hash requestedHash = new Hash(receivedData, TransactionViewModel.SIZE, TransactionRequester.REQUEST_HASH_SIZE);
                if (requestedHash.equals(receivedTransactionHash)) {
                    //requesting a random tip
                    requestedHash = Hash.NULL_HASH;
                }

                addReceivedDataToReplyQueue(requestedHash, neighbor);

                break;
            }
        }

        if (!addressMatch && configuration.booling(Configuration.DefaultConfSettings.TESTNET)) {
            int maxPeersAllowed = configuration.integer(Configuration.DefaultConfSettings.MAX_PEERS);
            String uriString = uriScheme + ":/" + senderAddress.toString();
            if (Neighbor.getNumPeers() < maxPeersAllowed) {
                log.info("Adding non-tethered neighbor: " + uriString);
                messageQ.publish("antn %s", uriString);
                try {
                    final URI uri = new URI(uriString);
                    // 3rd parameter false (not tcp), 4th parameter true (configured tethering)
                    final Neighbor newneighbor;
                    if (uriScheme.equals("tcp")) {
                        newneighbor = new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), false);
                    } else {
                        newneighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), udpSocket, false);
                    }
                    if (!getNeighbors().contains(newneighbor)) {
                        getNeighbors().add(newneighbor);
                        Neighbor.incNumPeers();
                    }
                } catch (URISyntaxException e) {
                    log.error("Invalid URI string: " + uriString);
                }
            }
            else {                
                if ( rejectedAddresses.size() > 20 ) {
                    // Avoid ever growing list in case of an attack.
                    rejectedAddresses.clear();
                }
                else if ( rejectedAddresses.add(uriString) ) {
                    messageQ.publish("rntn %s %s", uriString,  String.valueOf(maxPeersAllowed));
                    log.info("Refused non-tethered neighbor: " + uriString +
                        " (max-peers = "+ String.valueOf(maxPeersAllowed) + ")");
                }
            }
        }
    }

    public void addReceivedDataToReceiveQueue(TransactionViewModel receivedTransactionViewModel, Neighbor neighbor) {
        receiveQueue.add(new ImmutablePair<>(receivedTransactionViewModel,neighbor));
        if (receiveQueue.size() > RECV_QUEUE_SIZE) {
            receiveQueue.pollLast();
        }

    }

    public void addReceivedDataToReplyQueue(Hash requestedHash, Neighbor neighbor) {
        replyQueue.add(new ImmutablePair<>(requestedHash,neighbor));
        if (replyQueue.size() > REPLY_QUEUE_SIZE) {
            replyQueue.pollLast();
        }
    }


    public void processReceivedDataFromQueue() {
        final Pair<TransactionViewModel, Neighbor> receivedData = receiveQueue.pollFirst();
        if (receivedData != null) {
            processReceivedData(receivedData.getLeft(),receivedData.getRight());
        }
    }

    public void replyToRequestFromQueue() {
        final Pair<Hash, Neighbor> receivedData = replyQueue.pollFirst();
        if (receivedData != null) {
            replyToRequest(receivedData.getLeft(),receivedData.getRight());
        }
    }

    public void processReceivedData(TransactionViewModel receivedTransactionViewModel, Neighbor neighbor) {

        boolean cached = false;
        boolean stored = false;

        //store new transaction
        try {
            //first check if Hash seen recently
            synchronized (recentSeenHashes) {
                cached = recentSeenHashes.get(receivedTransactionViewModel.getHash());
            }
            if (cached) {
                stored = false;
            } else {
                //if not, store tx. & update recentSeenHashes
                stored = receivedTransactionViewModel.store(tangle);
                messageQ.publish("tx %s %s %d %s %d %d %d %s %s %s",
                        receivedTransactionViewModel.getHash(),
                        receivedTransactionViewModel.getAddressHash(),
                        receivedTransactionViewModel.value(),
                        receivedTransactionViewModel.getTagValue(),
                        receivedTransactionViewModel.getTimestamp(),
                        receivedTransactionViewModel.getCurrentIndex(),
                        receivedTransactionViewModel.lastIndex(),
                        receivedTransactionViewModel.getBundleHash(),
                        receivedTransactionViewModel.getTrunkTransactionHash(),
                        receivedTransactionViewModel.getBranchTransactionHash()
                );
                synchronized (recentSeenHashes) {
                    recentSeenHashes.set(receivedTransactionViewModel.getHash(), true);
                }
            }
        } catch (Exception e) {
            log.error("Error accessing persistence store.", e);
            neighbor.incInvalidTransactions();
        }

        //if new, then broadcast to all neighbors
        if(stored) {
            receivedTransactionViewModel.setArrivalTime(System.currentTimeMillis());
            try {
                transactionValidator.updateStatus(receivedTransactionViewModel);
                receivedTransactionViewModel.updateSender(neighbor.getAddress().toString());
                receivedTransactionViewModel.update(tangle, "arrivalTime|sender");

            } catch (Exception e) {
                log.error("Error updating transactions.", e);
            }
            neighbor.incNewTransactions();
            broadcast(receivedTransactionViewModel);
        }

    }

    public void replyToRequest(Hash requestedHash, Neighbor neighbor) {

        TransactionViewModel transactionViewModel = null;
        Hash transactionPointer;

        //retrieve requested transaction
        if (requestedHash.equals(Hash.NULL_HASH)) {
            //Random Tip Request
            try {
                if (transactionRequester.numberOfTransactionsToRequest() > 0 && rnd.nextDouble() < P_REPLY_RANDOM_TIP) {
                    neighbor.incRandomTransactionRequests();
                    transactionPointer = getRandomTipPointer();
                    transactionViewModel = TransactionViewModel.fromHash(tangle, transactionPointer);
                } else {
                    //no tx to request, so no random tip will be sent as a reply.
                    return;
                }
            } catch (Exception e) {
                log.error("Error getting random tip.", e);
            }
        } else {
            //find requested trytes
            try {
                //transactionViewModel = TransactionViewModel.find(Arrays.copyOf(requestedHash.bytes(), TransactionRequester.REQUEST_HASH_SIZE));
                transactionViewModel = TransactionViewModel.fromHash(tangle, new Hash(requestedHash.bytes(), 0, TransactionRequester.REQUEST_HASH_SIZE));
                //log.debug("Requested Hash: " + requestedHash + " \nFound: " + transactionViewModel.getHash());
            } catch (Exception e) {
                log.error("Error while searching for transaction.", e);
            }
        }

        if (transactionViewModel != null && transactionViewModel.getType() == TransactionViewModel.FILLED_SLOT) {
            //send trytes back to neighbor
            try {
                sendPacket(sendingPacket, transactionViewModel, neighbor);

            } catch (Exception e) {
                log.error("Error fetching transaction to request.", e);
            }
        } else {
            //trytes not found
            if (!requestedHash.equals(Hash.NULL_HASH) && rnd.nextDouble() < P_PROPAGATE_REQUEST) {
                //request is an actual transaction and missing in request queue add it.
                try {
                    transactionRequester.requestTransaction(requestedHash,false);

                } catch (Exception e) {
                    log.error("Error adding transaction to request.", e);
                }

            }
        }

    }

    private Hash getRandomTipPointer() throws Exception {
        Hash tip = rnd.nextDouble() < P_SEND_MILESTONE? milestone.latestMilestone: tipsViewModel.getRandomSolidTipHash();
        return tip == null ? Hash.NULL_HASH: tip;
    }

    public void sendPacket(DatagramPacket sendingPacket, TransactionViewModel transactionViewModel, Neighbor neighbor) throws Exception {

        //limit amount of sends per second
        long now = System.currentTimeMillis();
        if ((now - sendPacketsTimer.get()) > 1000L) {
            //reset counter every second
            sendPacketsCounter.set(0);
            sendPacketsTimer.set(now);
        }
        if ( sendLimit >= 0  && sendPacketsCounter.get() > sendLimit) {
            //if exceeded limit - don't send
            //log.info("exceeded limit - don't send - {}",sendPacketsCounter.get());
            return;
        }

        synchronized (sendingPacket) {
            System.arraycopy(transactionViewModel.getBytes(), 0, sendingPacket.getData(), 0, TransactionViewModel.SIZE);
            Hash hash = transactionRequester.transactionToRequest(rnd.nextDouble() < P_SELECT_MILESTONE );
            System.arraycopy(hash != null ? hash.bytes(): transactionViewModel.getHash().bytes(), 0,
                    sendingPacket.getData(), TransactionViewModel.SIZE, REQUEST_HASH_SIZE);
            neighbor.send(sendingPacket);
        }

        sendPacketsCounter.getAndIncrement();
    }

    private Runnable spawnBroadcasterThread() {
        return () -> {

            log.info("Spawning Broadcaster Thread");

            while (!shuttingDown.get()) {

                try {
                    final TransactionViewModel transactionViewModel = broadcastQueue.pollFirst();
                    if (transactionViewModel != null) {

                        for (final Neighbor neighbor : neighbors) {
                            try {
                                sendPacket(sendingPacket, transactionViewModel, neighbor);
                            } catch (final Exception e) {
                                // ignore
                            }
                        }
                    }
                    Thread.sleep(PAUSE_BETWEEN_TRANSACTIONS);
                } catch (final Exception e) {
                    log.error("Broadcaster Thread Exception:", e);
                }
            }
            log.info("Shutting down Broadcaster Thread");
        };
    }

    private Runnable spawnTipRequesterThread() {
        return () -> {

            log.info("Spawning Tips Requester Thread");
            long lastTime = 0;
            while (!shuttingDown.get()) {

                try {
                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, milestone.latestMilestone);
                    System.arraycopy(transactionViewModel.getBytes(), 0, tipRequestingPacket.getData(), 0, TransactionViewModel.SIZE);
                    System.arraycopy(transactionViewModel.getHash().bytes(), 0, tipRequestingPacket.getData(), TransactionViewModel.SIZE,
                            TransactionRequester.REQUEST_HASH_SIZE);
                            //Hash.SIZE_IN_BYTES);

                    neighbors.forEach(n -> n.send(tipRequestingPacket));

                    long now = System.currentTimeMillis();
                    if ((now - lastTime) > 10000L) {
                        lastTime = now;
                        messageQ.publish("RSTAT %d %d %d %d %d",
                                getReceiveQueueSize(), getBroadcastQueueSize() ,
                                transactionRequester.numberOfTransactionsToRequest() ,getReplyQueueSize(),
                                TransactionViewModel.getNumberOfStoredTransactions(tangle));
                        log.info("toProcess = {} , toBroadcast = {} , toRequest = {} , toReply = {} / totalTransactions = {}",
                                getReceiveQueueSize(), getBroadcastQueueSize() ,
                                transactionRequester.numberOfTransactionsToRequest() ,getReplyQueueSize(),
                                TransactionViewModel.getNumberOfStoredTransactions(tangle));
                    }

                    Thread.sleep(5000);
                } catch (final Exception e) {
                    log.error("Tips Requester Thread Exception:", e);
                }
            }
            log.info("Shutting down Requester Thread");
        };
    }

    private Runnable spawnProcessReceivedThread() {
        return () -> {

            log.info("Spawning Process Received Data Thread");

            while (!shuttingDown.get()) {

                try {
                    processReceivedDataFromQueue();
                    Thread.sleep(1);
                } catch (final Exception e) {
                    log.error("Process Received Data Thread Exception:", e);
                }
            }
            log.info("Shutting down Process Received Data Thread");
        };
    }

    private Runnable spawnReplyToRequestThread() {
        return () -> {

            log.info("Spawning Reply To Request Thread");

            while (!shuttingDown.get()) {

                try {
                    replyToRequestFromQueue();
                    Thread.sleep(1);
                } catch (final Exception e) {
                    log.error("Reply To Request Thread Exception:", e);
                }
            }
            log.info("Shutting down Reply To Request Thread");
        };
    }


    private static ConcurrentSkipListSet<TransactionViewModel> weightQueue() {
        return new ConcurrentSkipListSet<>((transaction1, transaction2) -> {
            if (transaction1.weightMagnitude == transaction2.weightMagnitude) {
                for (int i = Hash.SIZE_IN_BYTES; i-- > 0;) {
                    if (transaction1.getHash().bytes()[i] != transaction2.getHash().bytes()[i]) {
                        return transaction2.getHash().bytes()[i] - transaction1.getHash().bytes()[i];
                    }
                }
                return 0;
            }
            return transaction2.weightMagnitude - transaction1.weightMagnitude;
        });
    }
    //TODO generalize these weightQueues
    private static ConcurrentSkipListSet<Pair<Hash,Neighbor>> weightQueueHashPair() {
        return new ConcurrentSkipListSet<Pair<Hash,Neighbor>>((transaction1, transaction2) -> {
            Hash tx1 = transaction1.getLeft();
            Hash tx2 = transaction2.getLeft();

            for (int i = Hash.SIZE_IN_BYTES; i-- > 0;) {
                if (tx1.bytes()[i] != tx2.bytes()[i]) {
                    return tx2.bytes()[i] - tx1.bytes()[i];
                }
            }
            return 0;

        });
    }

    private static ConcurrentSkipListSet<Pair<TransactionViewModel,Neighbor>> weightQueueTxPair() {
        return new ConcurrentSkipListSet<Pair<TransactionViewModel,Neighbor>>((transaction1, transaction2) -> {
            TransactionViewModel tx1 = transaction1.getLeft();
            TransactionViewModel tx2 = transaction2.getLeft();

            if (tx1.weightMagnitude == tx2.weightMagnitude) {
                for (int i = Hash.SIZE_IN_BYTES; i-- > 0;) {
                    if (tx1.getHash().bytes()[i] != tx2.getHash().bytes()[i]) {
                        return tx2.getHash().bytes()[i] - tx1.getHash().bytes()[i];
                    }
                }
                return 0;
            }
            return tx2.weightMagnitude - tx1.weightMagnitude;
        });
    }


    public void broadcast(final TransactionViewModel transactionViewModel) {
        broadcastQueue.add(transactionViewModel);
        if (broadcastQueue.size() > QUEUE_SIZE) {
            broadcastQueue.pollLast();
        }
    }

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        executor.awaitTermination(6, TimeUnit.SECONDS);
    }

    // helpers methods

    public boolean removeNeighbor(final URI uri, boolean isConfigured) {
        Neighbor neighbor;
        if (uri.toString().contains("tcp:")) {
            neighbor = new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isConfigured);
            neighbors.stream().filter(n -> n instanceof TCPNeighbor)
                    .map(n -> ((TCPNeighbor) n))
                    .filter(n -> n.equals(neighbor))
                    .forEach(TCPNeighbor::clear);
        } else {
            neighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), udpSocket, isConfigured);
        }
        return neighbors.remove(neighbor);
    }

    public boolean addNeighbor(Neighbor neighbor) {
        return !getNeighbors().contains(neighbor) && getNeighbors().add(neighbor);
    }

    public Neighbor newNeighbor(final URI uri, boolean isConfigured) {
        final Neighbor neighbor;
        if (uri.toString().contains("tcp:")) {
            neighbor =  new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isConfigured);
        } else {
            neighbor =  new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), udpSocket, isConfigured);
        }
        return neighbor;
    }
    
    public static Optional<URI> uri(final String uri) {
        try {
            return Optional.of(new URI(uri));
        } catch (URISyntaxException e) {
            log.error("Uri {} raised URI Syntax Exception", uri);
        }
        return Optional.empty();
    }

    public int queuedTransactionsSize() {
        return broadcastQueue.size();
    }

    public int howManyNeighbors() {
        return getNeighbors().size();
    }

    public List<Neighbor> getNeighbors() {
        return neighbors;
    }

    public int getBroadcastQueueSize() {
        return broadcastQueue.size();
    }

    public int getReceiveQueueSize() {
        return receiveQueue.size();
    }

    public int getReplyQueueSize() {
        return replyQueue.size();
    }

    public class LRUHashCache {

        private int capacity;
        private LinkedHashMap<Hash,Boolean> map;

        public LRUHashCache(int capacity) {
            this.capacity = capacity;
            this.map = new LinkedHashMap<>();
        }

        public Boolean get(Hash key) {
            Boolean value = this.map.get(key);
            if (value == null) {
                value = false;
            } else {
                this.set(key, value);
            }
            return value;
        }

        public void set(Hash key, Boolean value) {
            if (this.map.containsKey(key)) {
                this.map.remove(key);
            } else if (this.map.size() == this.capacity) {
                Iterator<Hash> it = this.map.keySet().iterator();
                it.next();
                it.remove();
            }
            map.put(key, value);
        }
    }

    public class LRUByteCache {

        private int capacity;
        private LinkedHashMap<ByteBuffer,Hash> map;

        public LRUByteCache(int capacity) {
            this.capacity = capacity;
            this.map = new LinkedHashMap<>();
        }

        public Hash get(ByteBuffer key) {
            Hash value = this.map.get(key);
            if (value == null) {
                value = null;
            } else {
                this.set(key, value);
            }
            return value;
        }

        public void set(ByteBuffer key, Hash value) {
            if (this.map.containsKey(key)) {
                this.map.remove(key);
            } else if (this.map.size() == this.capacity) {
                Iterator<ByteBuffer> it = this.map.keySet().iterator();
                it.next();
                it.remove();
            }
            map.put(key, value);
        }
    }

}
