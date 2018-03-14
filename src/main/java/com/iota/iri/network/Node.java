package com.iota.iri.network;

import com.iota.iri.Milestone;
import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.scheduledReports.SystemStatsReport;
import com.iota.iri.scheduledReports.TCPNeighborReport;
import com.iota.iri.scheduledTasks.*;
import com.iota.iri.network.exec.SendTPSLimiter;
import com.iota.iri.network.exec.StripedExecutor;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Quiet;
import com.iota.iri.utils.ScheduledTask;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The class node is responsible for managing Thread's connection.
 */
public final class Node {

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    public static final int PORT_BYTES = 10;
    public static final int CRC32_BYTES = 16;
    public static final int TRANSACTION_PACKET_SIZE = 1650;
    public static final Duration SO_TIMEOUT = Duration.ofSeconds(30);

    private final SecureRandom rnd = new SecureRandom();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final Tangle tangle;
    private final FIFOCache<ByteBuffer, Hash> recentSeenBytesFIFOCache;
    private final SendTPSLimiter sendTPSLimiter;

    private final int BROADCAST_QUEUE_SIZE;
    private final int RECV_QUEUE_SIZE;
    private final int REPLY_QUEUE_SIZE;

    private final double P_SELECT_MILESTONE;
    private final double P_DROP_TRANSACTION;
    private final double P_SEND_MILESTONE;
    private final double P_REPLY_RANDOM_TIP;
    private final double P_PROPAGATE_REQUEST;

    private final long SEND_LIMIT;

    private final Configuration configuration;
    private final TipsViewModel tipsViewModel;
    private final TransactionValidator transactionValidator;
    private final Milestone milestone;
    private final TransactionRequester transactionRequester;
    private final MessageQ messageQ;
    private final boolean dnsrefreshing;
    private final NeighborManager neighborManager;


    /**
     * For some reason, there's no constructor in the PriorityBlockingQueue that allow us to create an instance
     * defining just the comparator. The only options are: default constructor, constructor taking the capacity as an
     * argument and constructor taking capacity and Comparator. (There's yet another one taking a collection as
     * argument, but it's irrelevant for this example).
     * Since we need a comparator, we also need to define the inital capacity, in order to use the constructor.
     * Defining it below as 11, which is the same value defined in the {@link PriorityBlockingQueue} class.
     */
    private static final int PRIORITY_BLOCKING_QUEUE_DEFAULT_INITIAL_CAPACITY = 11;
    private final PriorityBlockingQueue<TransactionViewModel> broadcastQueue = createBroadcastWeightQueue();
    private final PriorityBlockingQueue<Pair<TransactionViewModel, Neighbor>> receiveQueue = createReceiveWeightQueueTxPair();
    private final PriorityBlockingQueue<Pair<Hash, Neighbor>> replyQueue = createWeightQueueHashPair();


    private final StripedExecutor stripedExecutor = new StripedExecutor<Neighbor, byte[]>() {
        @Override
        public void process(Neighbor neighbor, byte[] data) {
            preProcessReceivedData(neighbor, data);
        }
    };

    private final StripedExecutor.StripeManager sendUDPPacketStripeManager
            = new StripedExecutor.StripeManager(4, "Node.sendUDPPacket");

    private final StripedExecutor.StripeManager sendTCPPacketStripeManager
            = new StripedExecutor.StripeManager(8, "Node.sendTCPPacket");


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
        this.neighborManager = new NeighborManager(configuration, messageQ);

        P_DROP_TRANSACTION = configuration.doubling(Configuration.DefaultConfSettings.P_DROP_TRANSACTION.name());
        P_SELECT_MILESTONE = configuration.doubling(Configuration.DefaultConfSettings.P_SELECT_MILESTONE_CHILD.name());
        P_SEND_MILESTONE = configuration.doubling(Configuration.DefaultConfSettings.P_SEND_MILESTONE.name());
        P_REPLY_RANDOM_TIP = configuration.doubling(Configuration.DefaultConfSettings.P_REPLY_RANDOM_TIP.name());
        P_PROPAGATE_REQUEST = configuration.doubling(Configuration.DefaultConfSettings.P_PROPAGATE_REQUEST.name());

        SEND_LIMIT = (long) ((configuration.doubling(Configuration.DefaultConfSettings.SEND_LIMIT.name()) * 1000000) / (TRANSACTION_PACKET_SIZE * 8));
        sendTPSLimiter = new SendTPSLimiter(SEND_LIMIT);

        BROADCAST_QUEUE_SIZE = RECV_QUEUE_SIZE = REPLY_QUEUE_SIZE = configuration.integer(Configuration.DefaultConfSettings.Q_SIZE_NODE);


        double P_DROP_CACHE_ENTRY = configuration.doubling(Configuration.DefaultConfSettings.P_DROP_CACHE_ENTRY.name());
        recentSeenBytesFIFOCache = new FIFOCache<>(configuration.integer(Configuration.DefaultConfSettings.CACHE_SIZE_BYTES), P_DROP_CACHE_ENTRY);

        dnsrefreshing = configuration.booling(Configuration.DefaultConfSettings.DNS_RESOLUTION_ENABLED);
        if (dnsrefreshing) {
            log.info("Neighbor DNS Refreshing turned on");
        } else {
            log.info("Ignoring DNS Refreshing ... DNS_RESOLUTION_ENABLED is false");
        }

        log.info("P_DROP_TRANSACTION = {}", P_DROP_TRANSACTION);
        log.info("P_SELECT_MILESTONE = {}", P_SELECT_MILESTONE);
        log.info("P_SEND_MILESTONE = {}", P_SEND_MILESTONE);
        log.info("P_REPLY_RANDOM_TIP = {}", P_REPLY_RANDOM_TIP);
        log.info("P_PROPAGATE_REQUEST = {}", P_PROPAGATE_REQUEST);
        log.info("SEND_LIMIT = {}", SEND_LIMIT);
        log.info("P_DROP_CACHE_ENTRY = {}", P_DROP_CACHE_ENTRY);
    }


    public void init() {
        neighborManager.parseNeighborsConfig();
        // will die automatically when vm exits
        Thread tipsRefresher = new Thread(repeatingScheduledTasks(), "RepeatingScheduledTasks");
        tipsRefresher.setDaemon(true);
        tipsRefresher.start();
        log.info("RepeatingScheduledTasks thread started");
    }

    /**
     * LOOPS EVERY 1/4 SECOND AND RUNS A TASK WHEN IT THE PERIOD HAS ELAPSED SINCE IT WAS LAST RUN
     * Tasks are short-lived -blocking- and they are run in order as added.
     */
    private Runnable repeatingScheduledTasks() {
        List<ScheduledTask> tasks = new ArrayList<>();

        tasks.add(new ScheduledTask(Duration.ofMinutes(4), stripedExecutor::report));
        tasks.add(new TipRequestingPacketsTask(Duration.ofSeconds(5), tangle, milestone, neighborManager, stripedExecutor));
        tasks.add(new SystemStatsReport(Duration.ofSeconds(10), stripedExecutor, messageQ, this, tangle, transactionRequester));
        tasks.add(new DNSRefreshRepeatingJob(Duration.ofMinutes(30), stripedExecutor, configuration, neighborManager, messageQ));
        tasks.add(new TCPNeighborReport(Duration.ofMinutes(2), neighborManager));

        return () -> {
            while (!shuttingDown.get()) {
                for (ScheduledTask task : tasks) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.warn("Exception in repeating serial task " + e.getMessage(), e);
                        if (Thread.currentThread().isInterrupted()) {

                        }
                    }
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        log.info("Interrupted in repeating serial tasks thread", e);
                    }
                }

            }
            log.info("Shutting down repeatingSerialTasks thread");
            tasks.forEach(t -> Quiet.run(t::shutdown));
        };
    }

    public StripedExecutor getStripeTasker() {
        return stripedExecutor;
    }

    public NeighborManager getNeighborManager() {
        return neighborManager;
    }

    private boolean P_REPLY_RANDOM_TIP() {
        return rnd.nextDouble() < P_REPLY_RANDOM_TIP;
    }

    private boolean P_DROP_TRANSACTION() {
        if (P_DROP_TRANSACTION <= 0) {
            return false;
        } else {
            return rnd.nextDouble() < P_DROP_TRANSACTION;
        }
    }

    private boolean P_PROPAGATE_REQUEST() {
        return rnd.nextDouble() < P_PROPAGATE_REQUEST;
    }

    private boolean P_SELECT_MILESTONE() {
        return rnd.nextDouble() < P_SELECT_MILESTONE;
    }

    private boolean P_SEND_MILESTONE() {
        return rnd.nextDouble() < P_SEND_MILESTONE;
    }


    private void addReceivedDataToReceiveQueue(TransactionViewModel receivedTransactionViewModel, Neighbor neighbor) {
        boolean added = receiveQueue.add(new ImmutablePair<>(receivedTransactionViewModel, neighbor));
        if (receiveQueue.size() > RECV_QUEUE_SIZE) {
            log.warn("Receive queue full. Transaction dropped. Consider increasing the Q_SIZE_NODE parameter. Current size: {}", RECV_QUEUE_SIZE);
            receiveQueue.poll();
            added = false;
        }
        if (added) {
            stripedExecutor.submitStripe("receiveQueue", () -> {
                try {
                    Pair<TransactionViewModel, Neighbor> receivedData = receiveQueue.poll();
                    if (receivedData == null) {
                        log.error("Process Received job - null result from receiveQueue.poll()");
                    } else {
                        processReceivedData(receivedData.getLeft(), receivedData.getRight());
                    }
                } catch (final Exception e) {
                    log.error("Process Received Data Thread Exception:", e);
                }
            });
        }
    }


    private void addReceivedDataToReplyQueue(Hash requestedHash, Neighbor neighbor) {
        boolean added = replyQueue.add(new ImmutablePair<>(requestedHash, neighbor));
        if (replyQueue.size() > REPLY_QUEUE_SIZE) {
            log.warn("Reply queue full. Transaction dropped. Consider increasing the Q_SIZE_NODE parameter. Current size: {}", REPLY_QUEUE_SIZE);
            replyQueue.poll();
            added = false;
        }
        if (added) {
            stripedExecutor.submitStripe("replyQueue", () -> {
                try {
                    Pair<Hash, Neighbor> receivedData = replyQueue.poll();
                    if (receivedData == null) {
                        log.error("Reply To Request job - null result from replyQueue.poll()");
                    } else {
                        replyToRequest(receivedData.getLeft(), receivedData.getRight());
                    }
                } catch (final Exception e) {
                    log.error("Reply To Request Thread Exception:", e);
                }
            });
        }
    }

    public void broadcast(final TransactionViewModel transactionViewModel) {
        boolean added = broadcastQueue.add(transactionViewModel);
        if (broadcastQueue.size() > BROADCAST_QUEUE_SIZE) {
            log.warn("Broadcast queue full. Transaction dropped. Consider increasing the Q_SIZE_NODE parameter. Current size: {}", BROADCAST_QUEUE_SIZE);
            broadcastQueue.poll();
            added = false;
        }
        if (added) {
            stripedExecutor.submitStripe("broadcastQueue", () -> {
                try {
                    final TransactionViewModel tvm = broadcastQueue.poll();
                    if (tvm == null) {
                        log.error("broadcast - job : null result from broadcastQueue.poll()");
                    } else {
                        for (final Neighbor neighbor : neighborManager.getNeighbors()) {
                            try {
                                sendPacket(tvm, neighbor);
                            } catch (final Exception e) {
                                // ignore
                            }
                        }
                    }
                } catch (final Exception e) {
                    log.error("Broadcaster Thread Exception:", e);
                }
            });
        }
    }


    public void preProcessReceivedData(Neighbor neighbor, byte[] receivedData) {

        //Validate transaction
        neighbor.incAllTransactions();

        // randomly drop tx
        if (P_DROP_TRANSACTION()) {
            log.info("Randomly dropping transaction. Stand by... ");
            return;
        }

        Hash receivedTransactionHash = null;
        try {
            //Transaction bytes
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(receivedData, 0, TransactionViewModel.SIZE);
            ByteBuffer sha256Digest = ByteBuffer.wrap(digest.digest());

            //check if cached
            synchronized (recentSeenBytesFIFOCache) {
                receivedTransactionHash = recentSeenBytesFIFOCache.get(sha256Digest);
            }

            if (receivedTransactionHash == null) {
                // then validate
                TransactionViewModel receivedTransactionViewModel =
                        new TransactionViewModel(receivedData, Hash.calculate(receivedData, TransactionViewModel.TRINARY_SIZE, SpongeFactory.create(SpongeFactory.Mode.CURLP81)));

                receivedTransactionHash = receivedTransactionViewModel.getHash();

                TransactionValidator.runValidation(receivedTransactionViewModel, transactionValidator.getMinWeightMagnitude());

                synchronized (recentSeenBytesFIFOCache) {
                    recentSeenBytesFIFOCache.put(sha256Digest, receivedTransactionHash);
                }

                //if valid - add to receive queue (receivedTransactionViewModel, neighbor)
                addReceivedDataToReceiveQueue(receivedTransactionViewModel, neighbor);

            }
        } catch (NoSuchAlgorithmException e) {
            log.error("MessageDigest: " + e);
        } catch (final TransactionValidator.StaleTimestampException e) {
            log.debug(e.getMessage());
            try {
                transactionRequester.clearTransactionRequest(receivedTransactionHash);
            } catch (Exception e1) {
                log.error(e1.getMessage());
            }
            neighbor.incInvalidTransactions();
        } catch (final RuntimeException e) {
            log.error(e.getMessage());
            log.error("Received an Invalid TransactionViewModel. Dropping it...");
            neighbor.incInvalidTransactions();
            return;
        }

        //Request bytes
        //add request to reply queue (requestedHash, neighbor)
        Hash requestedHash = new Hash(receivedData, TransactionViewModel.SIZE, TransactionRequester.REQUEST_HASH_SIZE);
        if (requestedHash.equals(receivedTransactionHash)) {
            //requesting a random tip
            requestedHash = Hash.NULL_HASH;
        }

        addReceivedDataToReplyQueue(requestedHash, neighbor);
    }


    private void processReceivedData(TransactionViewModel receivedTransactionViewModel, Neighbor neighbor) {

        boolean stored = false;
        //store new transaction
        try {
            stored = receivedTransactionViewModel.store(tangle);
        } catch (Exception e) {
            log.error("Error accessing persistence store.", e);
            neighbor.incInvalidTransactions();
        }

        //if new, then broadcast to all neighbors
        if (stored) {
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


    private void replyToRequest(Hash requestedHash, Neighbor neighbor) {

        TransactionViewModel transactionViewModel = null;
        Hash transactionPointer;

        //retrieve requested transaction
        if (requestedHash.equals(Hash.NULL_HASH)) {
            //Random Tip Request
            try {
                // 66% chance of getting a reply
                // 70% chance of getting reply as from latest milestone
                if (transactionRequester.numberOfTransactionsToRequest() > 0 && P_REPLY_RANDOM_TIP()) {
                    neighbor.incRandomTransactionRequests();

                    transactionPointer = P_SEND_MILESTONE()
                            ? milestone.getLatestMilestone()
                            : tipsViewModel.getRandomSolidTipHash();

                    if (transactionPointer == null) {
                        transactionPointer = Hash.NULL_HASH;
                    }
                    transactionViewModel = TransactionViewModel.fromHash(tangle, transactionPointer);
                } else {
                    //no tx to request, so no random tip will be sent as a reply.
                    return;
                }
            } catch (Exception e) {
                log.error("Error getting random tip.", e);
            }
        } else {
            //findFirst requested trytes
            try {
                transactionViewModel = TransactionViewModel.fromHash(tangle, new Hash(requestedHash.bytes(), 0, TransactionRequester.REQUEST_HASH_SIZE));
            } catch (Exception e) {
                log.error("Error while searching for transaction.", e);
            }
        }

        if ((transactionViewModel != null) && (transactionViewModel.getType() == TransactionViewModel.FILLED_SLOT)) {
            //send trytes back to neighbor
            try {
                sendPacket(transactionViewModel, neighbor);
            } catch (Exception e) {
                log.error("Error fetching transaction to request.", e);
            }
        } else {
            //trytes not found
            if (!requestedHash.equals(Hash.NULL_HASH) && P_PROPAGATE_REQUEST()) {
                //request is an actual transaction and missing in request queue add it.
                try {
                    transactionRequester.requestTransaction(requestedHash, false);

                } catch (Exception e) {
                    log.error("Error adding transaction to request.", e);
                }

            }
        }
    }


    private void sendPacket(TransactionViewModel transactionViewModel, Neighbor neighbor) throws Exception {
        sendTPSLimiter.runThrower(() -> {
            byte[] packet = new byte[TRANSACTION_PACKET_SIZE];
            System.arraycopy(transactionViewModel.getBytes(), 0, packet, 0, TransactionViewModel.SIZE);

            Hash hash = transactionRequester.transactionToRequest(P_SELECT_MILESTONE());

            System.arraycopy(hash != null ? hash.bytes() : transactionViewModel.getHash().bytes(), 0,
                    packet, TransactionViewModel.SIZE, TransactionRequester.REQUEST_HASH_SIZE);


            String stripe = neighbor instanceof TCPNeighbor
                    ? sendTCPPacketStripeManager.stripe()
                    : sendUDPPacketStripeManager.stripe();
            stripedExecutor.submitStripe(stripe, () -> neighbor.send(packet));
        });
    }


    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        stripedExecutor.shutdown();
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

    public int getBroadcastQueueSize() {
        return broadcastQueue.size();
    }

    public int getReceiveQueueSize() {
        return receiveQueue.size();
    }

    public int getReplyQueueSize() {
        return replyQueue.size();
    }


    //TODO generalize these weightQueues
    private static PriorityBlockingQueue<TransactionViewModel> createBroadcastWeightQueue() {
        return new PriorityBlockingQueue<>(PRIORITY_BLOCKING_QUEUE_DEFAULT_INITIAL_CAPACITY, (transaction1, transaction2) -> {
            if (transaction1.weightMagnitude == transaction2.weightMagnitude) {
                for (int i = Hash.SIZE_IN_BYTES; i-- > 0; ) {
                    if (transaction1.getHash().bytes()[i] != transaction2.getHash().bytes()[i]) {
                        return transaction2.getHash().bytes()[i] - transaction1.getHash().bytes()[i];
                    }
                }
                return 0;
            }
            return transaction2.weightMagnitude - transaction1.weightMagnitude;
        });
    }

    private static PriorityBlockingQueue<Pair<Hash, Neighbor>> createWeightQueueHashPair() {
        return new PriorityBlockingQueue<>(PRIORITY_BLOCKING_QUEUE_DEFAULT_INITIAL_CAPACITY, (transaction1, transaction2) -> {
            Hash tx1 = transaction1.getLeft();
            Hash tx2 = transaction2.getLeft();

            for (int i = Hash.SIZE_IN_BYTES; i-- > 0; ) {
                if (tx1.bytes()[i] != tx2.bytes()[i]) {
                    return tx2.bytes()[i] - tx1.bytes()[i];
                }
            }
            return 0;

        });
    }

    private static PriorityBlockingQueue<Pair<TransactionViewModel, Neighbor>> createReceiveWeightQueueTxPair() {
        return new PriorityBlockingQueue<>(PRIORITY_BLOCKING_QUEUE_DEFAULT_INITIAL_CAPACITY, (transaction1, transaction2) -> {
            TransactionViewModel tx1 = transaction1.getLeft();
            TransactionViewModel tx2 = transaction2.getLeft();

            if (tx1.weightMagnitude == tx2.weightMagnitude) {
                for (int i = Hash.SIZE_IN_BYTES; i-- > 0; ) {
                    if (tx1.getHash().bytes()[i] != tx2.getHash().bytes()[i]) {
                        return tx2.getHash().bytes()[i] - tx1.getHash().bytes()[i];
                    }
                }
                return 0;
            }
            return tx2.weightMagnitude - tx1.weightMagnitude;
        });
    }


    public class FIFOCache<K, V> {

        private final int capacity;
        private final double dropRate;
        private final SecureRandom rnd = new SecureRandom();
        private LinkedHashMap<K, V> map;

        FIFOCache(int capacity, double dropRate) {
            this.capacity = capacity;
            this.dropRate = dropRate;
            this.map = new LinkedHashMap<>();
        }

        public V get(K key) {
            V value = this.map.get(key);
            if (value != null && (rnd.nextDouble() < this.dropRate)) {
                this.map.remove(key);
                return null;
            }
            return value;
        }


        public V put(K key, V value) {
            if (this.map.containsKey(key)) {
                return value;
            }
            if (this.map.size() >= this.capacity) {
                Iterator<K> it = this.map.keySet().iterator();
                it.next();
                it.remove();
            }
            return this.map.put(key, value);
        }
    }

}
