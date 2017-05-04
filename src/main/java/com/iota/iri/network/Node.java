package com.iota.iri.network;

import java.net.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.*;
import com.iota.iri.model.Hash;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;


import java.util.LinkedHashMap;
import java.util.Iterator;

/**
 * The class node is responsible for managing Thread's connection.
 */
public class Node {

    private static final Logger log = LoggerFactory.getLogger(Node.class);


    public  static final int TRANSACTION_PACKET_SIZE = 1653;
    private static final int QUEUE_SIZE = 1000;
    private static final int RECV_QUEUE_SIZE = 1000;
    private static final int PAUSE_BETWEEN_TRANSACTIONS = 1;
    public  static final int REQUEST_HASH_SIZE = 49;
    private static double P_SELECT_MILESTONE;
    private static Node instance = new Node();

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();
    private final ConcurrentSkipListSet<TransactionViewModel> broadcastQueue = weightQueue();
    private final ConcurrentSkipListSet<Triple<TransactionViewModel,Hash,Neighbor>> receiveQueue = weightQueueTriple();


    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);
    private final DatagramPacket tipRequestingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private double P_DROP_TRANSACTION;
    private static final SecureRandom rnd = new SecureRandom();
    private double P_SEND_MILESTONE;

    private LRUHashCache recentSeenHashes = new LRUHashCache(5000);
    private LRUByteCache recentSeenBytes = new LRUByteCache(50000);

    private LRUByteBoolCache recentSeenRequests = new LRUByteBoolCache(50000);


    private static AtomicLong recentSeenBytesMissCount = new AtomicLong(0L);
    private static AtomicLong recentSeenBytesHitCount = new AtomicLong(0L);

    private static AtomicLong recentSeenRequestsMissCount = new AtomicLong(0L);
    private static AtomicLong recentSeenRequestsHitCount = new AtomicLong(0L);

    public void init(double pDropTransaction, double p_SELECT_MILESTONE, double pSendMilestone, String neighborList) throws Exception {
        P_DROP_TRANSACTION = pDropTransaction;
        P_SELECT_MILESTONE = p_SELECT_MILESTONE;
        P_SEND_MILESTONE = pSendMilestone;
        Arrays.stream(neighborList.split(" ")).distinct()
                .filter(s -> !s.isEmpty()).map(Node::uri).map(Optional::get).peek(u -> {
                    if (!"udp".equals(u.getScheme()) && !"tcp".equals(u.getScheme()) || (new InetSocketAddress(u.getHost(), u.getPort()).getAddress() == null)) {
                        log.error("CONFIGURATION ERROR: '{}' is not a valid uri schema or resolvable address.", u);
                    }})
                .filter(u -> ("udp".equals(u.getScheme()) || "tcp".equals(u.getScheme())) && (new InetSocketAddress(u.getHost(), u.getPort()).getAddress()) != null)
                .map(u -> "tcp".equals(u.getScheme())? new TCPNeighbor(new InetSocketAddress(u.getHost(), u.getPort()),true):
                    new UDPNeighbor(new InetSocketAddress(u.getHost(), u.getPort()),true))
                .peek(u -> {
                log.info("-> Adding neighbor : {} ", u.getAddress());
        }).forEach(neighbors::add);

        executor.submit(spawnBroadcasterThread());
        executor.submit(spawnTipRequesterThread());
        executor.submit(spawnNeighborDNSRefresherThread());
        executor.submit(spawnProcessReceivedThread());
        TipsViewModel.loadTipHashes();
        executor.shutdown();
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
                            final String neighborAddress = neighborIpCache.get(hostname);

                            if (neighborAddress == null) {
                                neighborIpCache.put(hostname, ip);
                            } else {
                                if (neighborAddress.equals(ip)) {
                                    log.info("{} seems fine.", hostname);
                                } else {
                                    log.info("CHANGED IP for {}! Updating...", hostname);

                                    uri("udp://" + hostname).ifPresent(uri -> {
                                        removeNeighbor(uri, n.isFlagged());

                                        uri("udp://" + ip).ifPresent(nuri -> {
                                            addNeighbor(nuri, n.isFlagged());
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
        TransactionViewModel receivedTransactionViewModel;

        boolean addressMatch = false;
        for (final Neighbor neighbor : getNeighbors()) {

            if (neighbor instanceof TCPNeighbor) {
                if (senderAddress.toString().contains(neighbor.getHostAddress())) addressMatch = true;
            } else {
                if (neighbor.getAddress().toString().contains(senderAddress.toString())) addressMatch = true;
            }
            if (addressMatch) {
                //Validate transaction
                neighbor.incAllTransactions();
                if (rnd.nextDouble() < P_DROP_TRANSACTION) {
                    //log.info("Randomly dropping transaction. Stand by... ");
                    break;
                }
                try {
                    //check if cached
                    synchronized (recentSeenBytes) {
                        receivedTransactionViewModel = recentSeenBytes.get(ByteBuffer.wrap(receivedData, 0, TransactionViewModel.SIZE));
                        if (((recentSeenBytesMissCount.get() + recentSeenBytesHitCount.get()) % 50000L == 0)) {
                            log.info("RecentSeenBytes cache hit/miss ratio: "+recentSeenBytesHitCount.get()+"/"+recentSeenBytesMissCount.get());
                            recentSeenBytesMissCount.set(0L);
                            recentSeenBytesHitCount.set(0L);
                        }
                    }
                    if (receivedTransactionViewModel == null) {
                        //if not then validate
                        recentSeenBytesMissCount.getAndIncrement();
                        receivedTransactionViewModel = TransactionValidator.validate(receivedData);
                        synchronized (recentSeenBytes) {
                            recentSeenBytes.set(ByteBuffer.wrap(receivedData, 0, TransactionViewModel.SIZE), receivedTransactionViewModel);
                        }
                    }
                    else {
                        recentSeenBytesHitCount.getAndIncrement();
                    }
                    
                } catch (final RuntimeException e) {
                    log.error("Received an Invalid TransactionViewModel. Dropping it...");
                    neighbor.incInvalidTransactions();
                    break;
                }

                //TODO remove - only for metering
                //check if cached
                boolean cached = false;
                synchronized (recentSeenRequests) {
                    cached = recentSeenRequests.get(ByteBuffer.wrap(receivedData, TransactionViewModel.SIZE, TransactionRequester.REQUEST_HASH_SIZE));
                    if (((recentSeenRequestsMissCount.get() + recentSeenRequestsHitCount.get()) % 50000L == 0)) {
                        log.info("recentSeenRequests cache hit/miss ratio: "+recentSeenRequestsHitCount.get()+"/"+recentSeenRequestsMissCount.get());
                        recentSeenRequestsMissCount.set(0L);
                        recentSeenRequestsHitCount.set(0L);
                    }
                }
                if (!cached) {
                    //if not then validate
                    recentSeenRequestsMissCount.getAndIncrement();
                    synchronized (recentSeenBytes) {
                        recentSeenRequests.set(ByteBuffer.wrap(receivedData, TransactionViewModel.SIZE, TransactionRequester.REQUEST_HASH_SIZE), true);
                    }
                }
                else {
                    recentSeenRequestsHitCount.getAndIncrement();
                }

                Hash requestedHash = new Hash(receivedData, TransactionViewModel.SIZE, TransactionRequester.REQUEST_HASH_SIZE);

                //if valid - add to queue (receivedTransactionViewModel, requestedHash, neighbor)
                addReceivedDataToQueue(receivedTransactionViewModel, requestedHash, neighbor);

            }
        }

        if (!addressMatch && Configuration.booling(Configuration.DefaultConfSettings.TESTNET)) {
            // TODO This code is only for testnet/stresstest - remove for mainnet
            String uriString = uriScheme + ":/" + senderAddress.toString();
            log.info("Adding non-tethered neighbor: " + uriString);
            try {
                final URI uri = new URI(uriString);
                // 3rd parameter false (not tcp), 4th parameter true (configured tethering)
                final Neighbor newneighbor;
                if (uriScheme.equals("tcp")) {
                    newneighbor = new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), false);
                } else {
                    newneighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), false);
                }
                if (!getNeighbors().contains(newneighbor)) {
                    getNeighbors().add(newneighbor);
                }
            } catch (URISyntaxException e) {
                log.error("Invalid URI string: " + uriString);
            }
        }
    }

    public void addReceivedDataToQueue(TransactionViewModel receivedTransactionViewModel, Hash requestedHash, Neighbor neighbor) {
        receiveQueue.add(new ImmutableTriple<>(receivedTransactionViewModel,requestedHash,neighbor));
        if (receiveQueue.size() > RECV_QUEUE_SIZE) {
            receiveQueue.pollLast();
        }

    }

    public void processReceivedDataFromQueue() {
        final Triple<TransactionViewModel, Hash, Neighbor> recievedData = receiveQueue.pollFirst();
        if (recievedData != null) {
            processReceivedData(recievedData.getLeft(),recievedData.getMiddle(),recievedData.getRight());
        }
    }

    public void processReceivedData(TransactionViewModel receivedTransactionViewModel, Hash requestedHash, Neighbor neighbor) {

        TransactionViewModel transactionViewModel = null;
        Hash transactionPointer;

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
                stored = receivedTransactionViewModel.store();
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
                receivedTransactionViewModel.update("arrivalTime");
                receivedTransactionViewModel.updateSender(neighbor.getAddress().toString()); //TODO validate this change
//                receivedTransactionViewModel.updateSender(neighbor instanceof TCPNeighbor?
//                        senderAddress.toString(): neighbor.getAddress().toString() );

            } catch (Exception e) {
                log.error("Error updating transactions.", e);
            }
            neighbor.incNewTransactions();
            broadcast(receivedTransactionViewModel);
        }

        //retrieve requested transaction
        if (requestedHash.equals(receivedTransactionViewModel.getHash())) {
            //Random Tip Request
            try {
                if (TransactionRequester.instance().numberOfTransactionsToRequest() > 0) {
                    neighbor.incRandomTransactionRequests();
                    transactionPointer = getRandomTipPointer();
                    transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                } else {
                    //no tx to request, so no random tip will be sent as aa reply.
                    return;
                }
            } catch (Exception e) {
                log.error("Error getting random tip.", e);
            }
        } else {
            //find requested trytes
            try {
                transactionViewModel = TransactionViewModel.find(Arrays.copyOf(requestedHash.bytes(), TransactionRequester.REQUEST_HASH_SIZE));
                log.debug("Requested Hash: " + requestedHash + " \nFound: " + transactionViewModel.getHash());
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
            //TODO remove or optimize "isHashInRequests" to O(1), only used for metering
            //trytes not found
            if (!TransactionRequester.instance().isHashInRequests(requestedHash) && !requestedHash.equals(Hash.NULL_HASH)) {
                //hash isn't in node's request pool
                log.info("not found && not in req queue: {} ", requestedHash);


            }
        }


    }

    private Hash getRandomTipPointer() throws Exception {
        final Hash tip = rnd.nextDouble() < P_SEND_MILESTONE? Milestone.latestMilestone: TipsViewModel.getRandomSolidTipHash();
        return tip == null ? Hash.NULL_HASH: tip;
    }

    public static void sendPacket(DatagramPacket sendingPacket, TransactionViewModel transactionViewModel, Neighbor neighbor) throws Exception {
        synchronized (sendingPacket) {
            System.arraycopy(transactionViewModel.getBytes(), 0, sendingPacket.getData(), 0, TransactionViewModel.SIZE);
            Hash hash = TransactionRequester.instance().transactionToRequest(rnd.nextDouble() < P_SELECT_MILESTONE );
            System.arraycopy(hash != null ? hash.bytes(): transactionViewModel.getHash().bytes(), 0,
                    sendingPacket.getData(), TransactionViewModel.SIZE, REQUEST_HASH_SIZE);
            neighbor.send(sendingPacket);
        }
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
                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(Milestone.latestMilestone);
                    System.arraycopy(transactionViewModel.getBytes(), 0, tipRequestingPacket.getData(), 0, TransactionViewModel.SIZE);
                    System.arraycopy(transactionViewModel.getHash().bytes(), 0, tipRequestingPacket.getData(), TransactionViewModel.SIZE,
                            TransactionRequester.REQUEST_HASH_SIZE);
                            //Hash.SIZE_IN_BYTES);

                    neighbors.forEach(n -> n.send(tipRequestingPacket));

                    long now = System.currentTimeMillis();
                    if ((now - lastTime) > 10000L) {
                        lastTime = now;
                        log.info("toProcess = {} , toBroadcast = {} , toRequest = {} / totalTransactions = {}", getBroadcastQueueSize(),getReceiveQueueSize() ,TransactionRequester.instance().numberOfTransactionsToRequest() , TransactionViewModel.getNumberOfStoredTransactions());
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
                    Node.instance().processReceivedDataFromQueue();
                    Thread.sleep(1);
                } catch (final Exception e) {
                    log.error("Process Received Data Thread Exception:", e);
                }
            }
            log.info("Shutting down Broadcaster Thread");
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

    private static ConcurrentSkipListSet<Triple<TransactionViewModel,Hash,Neighbor>> weightQueueTriple() {
        return new ConcurrentSkipListSet<Triple<TransactionViewModel,Hash,Neighbor>>((transaction1, transaction2) -> {
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
            neighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isConfigured);
        }
        return neighbors.remove(neighbor);
    }

    public boolean addNeighbor(final URI uri, boolean isConfigured) {
        final Neighbor neighbor;
        if (uri.toString().contains("tcp:")) {
            neighbor =  new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isConfigured);
        } else {
            neighbor =  new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isConfigured);
        }
        return !getNeighbors().contains(neighbor) && getNeighbors().add(neighbor);
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
        return Node.instance().neighbors;
    }

    public static Node instance() {
        return instance;
    }

    public int getBroadcastQueueSize() {
        return broadcastQueue.size();
    }

    public int getReceiveQueueSize() {
        return receiveQueue.size();
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
        private LinkedHashMap<ByteBuffer,TransactionViewModel> map;

        public LRUByteCache(int capacity) {
            this.capacity = capacity;
            this.map = new LinkedHashMap<>();
        }

        public TransactionViewModel get(ByteBuffer key) {
            TransactionViewModel value = this.map.get(key);
            if (value == null) {
                value = null;
            } else {
                this.set(key, value);
            }
            return value;
        }

        public void set(ByteBuffer key, TransactionViewModel value) {
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


    public class LRUByteBoolCache {

        private int capacity;
        private LinkedHashMap<ByteBuffer,Boolean> map;

        public LRUByteBoolCache(int capacity) {
            this.capacity = capacity;
            this.map = new LinkedHashMap<>();
        }

        public Boolean get(ByteBuffer key) {
            Boolean value = this.map.get(key);
            if (value == null) {
                value = false;
            } else {
                this.set(key, value);
            }
            return value;
        }

        public void set(ByteBuffer key, Boolean value) {
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
