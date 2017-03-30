package com.iota.iri.service;

import java.io.IOException;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.iota.iri.model.Hash;
import com.iota.iri.service.replicator.ReplicatorSinkPool;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.service.viewModels.TipsViewModel;
import com.iota.iri.service.viewModels.TransactionRequester;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;
import com.iota.iri.Neighbor;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.hash.Curl;
import com.iota.iri.utils.Converter;

/**
 * The class node is responsible for managing Thread's connection.
 */
public class Node {

    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private static final Node instance = new Node();

    public  static final int TRANSACTION_PACKET_SIZE = 1650;
    private static final int QUEUE_SIZE = 1000;
    private static final int PAUSE_BETWEEN_TRANSACTIONS = 1;

    private DatagramSocket socket;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();
    private final ConcurrentSkipListSet<TransactionViewModel> queuedTransactionViewModels = weightQueue();

    private final DatagramPacket receivingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);
    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);
    private final DatagramPacket tipRequestingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    
    public static long TIMESTAMP_THRESHOLD = 0L;

    public static void setTIMESTAMP_THRESHOLD(long tIMESTAMP_THRESHOLD) {
        TIMESTAMP_THRESHOLD = tIMESTAMP_THRESHOLD;
    }

    private volatile long randomTipBroadcastCounter = 1;
    private final SecureRandom rnd = new SecureRandom();

    public void init() throws Exception {

        socket = new DatagramSocket(Configuration.integer(DefaultConfSettings.TANGLE_RECEIVER_PORT));

        Arrays.stream(Configuration.string(DefaultConfSettings.NEIGHBORS).split(" ")).distinct()
        .filter(s -> !s.isEmpty()).map(Node::uri).map(Optional::get).peek(u -> {
            if (!"udp".equals(u.getScheme()) && !"tcp".equals(u.getScheme())) {
                log.warn("WARNING: '{}' is not a valid uri schema.", u);
            }
        }).filter(u -> "udp".equals(u.getScheme()) || "tcp".equals(u.getScheme()))
        .map(u -> new Neighbor(new InetSocketAddress(u.getHost(), u.getPort()),"tcp".equals(u.getScheme()),true)).peek(u -> {
            //if (Configuration.booling(DefaultConfSettings.DEBUG)) {
                log.info("-> Adding neighbor : {} ", u.getAddress());
            //}
        }).forEach(neighbors::add);
        
        
        executor.submit(spawnReceiverThread());
        executor.submit(spawnBroadcasterThread());
        executor.submit(spawnTipRequesterThread());
        executor.submit(spawnNeighborDNSRefresherThread());

        executor.shutdown();
    }

    private Map<String, String> neighborIpCache = new HashMap<>();
    
    private Runnable spawnNeighborDNSRefresherThread() {
        return () -> {

            log.info("Spawning Neighbor DNS Refresher Thread");

            while (!shuttingDown.get()) {
                log.info("Checking Neighbors' Ip...");

                try {
                    neighbors.forEach(n -> {
                        final String hostname = n.getAddress().getHostName();
                        checkIp(hostname).ifPresent(ip -> {
                            log.info("DNS Checker: Validating DNS Address '{}' with '{}'", hostname, ip);
                            final String neighborAddress = neighborIpCache.get(hostname);
                            
                            if (neighborAddress == null) {
                                neighborIpCache.put(neighborAddress, ip);
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

                    Thread.sleep(1000*60*30);
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
    
    private Runnable spawnReceiverThread() {
        return () -> {


            log.info("Spawning Receiver Thread");

            final Curl curl = new Curl();
            final int[] receivedTransactionTrits = new int[TransactionViewModel.TRINARY_SIZE];
            final byte[] requestedTransaction = new byte[Hash.SIZE_IN_BYTES];
            while (!shuttingDown.get()) {

                try {
                    socket.receive(receivingPacket);

                    if (receivingPacket.getLength() == TRANSACTION_PACKET_SIZE) {
                        processReceivedData(receivingPacket.getData(), receivingPacket.getSocketAddress(), curl, receivedTransactionTrits, requestedTransaction);
                    } else {
                        receivingPacket.setLength(TRANSACTION_PACKET_SIZE);
                    }
                } catch (final Exception e) {
                    log.error("Receiver Thread Exception:", e);
                }
            }
            log.info("Shutting down spawning Receiver Thread");
        };
    }

    public void processReceivedData(byte[] receivedData, SocketAddress senderAddress, Curl curl, int[] receivedTransactionTrits, byte[] requestedTransaction) throws Exception {
        long timestamp;
        TransactionViewModel receivedTransactionViewModel, transactionViewModel;
        Hash transactionPointer;

        for (final Neighbor neighbor : neighbors) {
            boolean addressMatch = false;
            if (neighbor.isTcpip()) {
                if (senderAddress.toString().contains(neighbor.getHostAddress())) addressMatch = true;
            }
            else {
                if (neighbor.getAddress().equals(senderAddress)) addressMatch = true;
            }
            if (addressMatch==true) {
                try {
                    neighbor.incAllTransactions();
                    receivedTransactionViewModel = new TransactionViewModel(receivedData, receivedTransactionTrits, curl);
                    timestamp = receivedTransactionViewModel.getTimestamp();
                    if (timestamp == 0 || timestamp > TIMESTAMP_THRESHOLD) {
                        if(receivedTransactionViewModel.store()) {
                            receivedTransactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                            receivedTransactionViewModel.update("arrivalTime");
                            neighbor.incNewTransactions();
                            broadcast(receivedTransactionViewModel);
                        }
                        System.arraycopy(receivedData, TransactionViewModel.SIZE, requestedTransaction, 0, TransactionViewModel.HASH_SIZE);

                        transactionPointer = getNextTransactionPointer(requestedTransaction);
                        transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                        if (!Arrays.equals(transactionViewModel.getBytes(), TransactionViewModel.NULL_TRANSACTION_BYTES)) {
                            synchronized (sendingPacket) {
                                //log.info(neighbor.getAddress().getHostString() + "Requested TX Hash: " + transactionPointer);
                                System.arraycopy(transactionViewModel.getBytes(), 0, sendingPacket.getData(), 0, TransactionViewModel.SIZE);
                                TransactionRequester.instance().transactionToRequest(sendingPacket.getData(), TransactionViewModel.SIZE);
                                neighbor.send(sendingPacket);
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    log.error("Received an Invalid TransactionViewModel. Dropping it...");
                    neighbor.incInvalidTransactions();
                }
                break;
            }
        }
    }

    private Hash getNextTransactionPointer(byte[] requestedTransaction) throws Exception {
        Hash mBytes, transactionPointer = Hash.NULL_HASH;
        if (Arrays.equals(requestedTransaction, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES)
                && (Milestone.latestMilestoneIndex > 0)
                && (Milestone.latestMilestoneIndex == Milestone.latestSolidSubtangleMilestoneIndex)) {
            //
            if (randomTipBroadcastCounter % 60 == 0) {
                mBytes = Milestone.latestMilestone;
                if (!mBytes.equals(Hash.NULL_HASH)) {
                    transactionPointer = mBytes;
                }
            } else if (randomTipBroadcastCounter % 48 == 0) {
                mBytes = Milestone.latestMilestone;
                if (!mBytes.equals(Hash.NULL_HASH)) {
                    transactionPointer = mBytes;

                    final TransactionViewModel milestoneTx = TransactionViewModel.fromHash(transactionPointer);
                    final BundleViewModel bundle = BundleViewModel.fromHash(milestoneTx.getBundleHash());
                    if (bundle != null) {
                        Collection<List<TransactionViewModel>> tList = bundle.getTransactions();
                        if (tList != null && tList.size() != 0) {
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundle.getTransactions()) {
                                if (bundleTransactionViewModels.size() > 1) {
                                    transactionPointer = bundleTransactionViewModels.get(1).getHash();
                                }
                            }
                        }
                    }
                }
            } else if (randomTipBroadcastCounter % 24 == 0) {
                final Hash[] tips = TipsViewModel.getTipHashes();
                if(tips.length > 0) {
                    transactionPointer = tips[rnd.nextInt(tips.length)];
                }
            }
            randomTipBroadcastCounter++;

        } else {

            transactionPointer = new Hash(Converter.bytes(new Hash(requestedTransaction).trits()));
        }
        return transactionPointer;
    }

    private Runnable spawnBroadcasterThread() {
        return () -> {

            log.info("Spawning Broadcaster Thread");

            while (!shuttingDown.get()) {

                try {
                    final TransactionViewModel transactionViewModel = queuedTransactionViewModels.pollFirst();
                    if (transactionViewModel != null) {

                        for (final Neighbor neighbor : neighbors) {
                            try {
                                synchronized (sendingPacket) {
                                    System.arraycopy(transactionViewModel.getBytes(), 0, sendingPacket.getData(), 0,
                                            TransactionViewModel.SIZE);
                                    TransactionRequester.instance().transactionToRequest(sendingPacket.getData(),
                                            TransactionViewModel.SIZE);
                                    neighbor.send(sendingPacket);
                                }
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

            while (!shuttingDown.get()) {

                try {
                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(Milestone.latestMilestone);
                    System.arraycopy(transactionViewModel.getBytes(), 0, tipRequestingPacket.getData(), 0, TransactionViewModel.SIZE);
                    System.arraycopy(Hash.NULL_HASH.bytes(), 0, tipRequestingPacket.getData(), TransactionViewModel.SIZE,
                            TransactionViewModel.HASH_SIZE);
                            //Hash.SIZE_IN_BYTES);

                    neighbors.forEach(n -> n.send(tipRequestingPacket));

                    Thread.sleep(5000);
                } catch (final Exception e) {
                    log.error("Tips Requester Thread Exception:", e);
                }
            }
            log.info("Shutting down Requester Thread");
        };
    }

    private static ConcurrentSkipListSet<TransactionViewModel> weightQueue() {
        return new ConcurrentSkipListSet<>((transaction1, transaction2) -> {
            if (transaction1.weightMagnitude == transaction2.weightMagnitude) {
                for (int i = 0; i < Hash.SIZE_IN_BYTES; i++) {
                    if (transaction1.getHash().bytes()[i] != transaction2.getHash().bytes()[i]) {
                        return transaction2.getHash().bytes()[i] - transaction1.getHash().bytes()[i];
                    }
                }
                return 0;
            }
            return transaction2.weightMagnitude - transaction1.weightMagnitude;
        });
    }

    public void broadcast(final TransactionViewModel transactionViewModel) {
        ReplicatorSinkPool.instance().broadcast(transactionViewModel);
        queuedTransactionViewModels.add(transactionViewModel);
        if (queuedTransactionViewModels.size() > QUEUE_SIZE) {
            queuedTransactionViewModels.pollLast();
        }
    }

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        executor.shutdown();
        executor.awaitTermination(6, TimeUnit.SECONDS);
    }

    public void send(final DatagramPacket packet) {
        try {
            socket.send(packet);
        } catch (IOException e) {
            // ignore
        }
    }
    
    // helpers methods

    public boolean removeNeighbor(final URI uri, boolean isConfigured) {
        boolean isTcp = false;
        if (uri.toString().contains("tcp:")) {
            isTcp = true;
        }
        return neighbors.remove(new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isTcp, isConfigured));
    }

    public boolean addNeighbor(final URI uri, boolean isConfigured) {
        boolean isTcp = false;
        if (uri.toString().contains("tcp:")) {
            isTcp = true;
        }
        final Neighbor neighbor = new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isTcp, isConfigured);
        if (!Node.instance().getNeighbors().contains(neighbor)) {
            return Node.instance().getNeighbors().add(neighbor);
        }
        return false;
    }
    
    public static Optional<URI> uri(final String uri) {
        try {
            return Optional.of(new URI(uri));
        } catch (URISyntaxException e) {
            log.error("Uri {} raised URI Syntax Exception", uri);
        }
        return Optional.empty();
    }

    public static Node instance() {
        return instance;
    }

    public int queuedTransactionsSize() {
        return queuedTransactionViewModels.size();
    }

    public int howManyNeighbors() {
        return neighbors.size();
    }

    public List<Neighbor> getNeighbors() {
        return neighbors;
    }
    
    private Node() {}
}
