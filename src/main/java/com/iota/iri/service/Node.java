package com.iota.iri.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.iota.iri.model.Hash;
import com.iota.iri.service.storage.ReplicatorSinkPool;
import com.iota.iri.service.viewModels.TipsViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Bundle;
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

    public void init() throws Exception {

        socket = new DatagramSocket(Configuration.integer(DefaultConfSettings.TANGLE_RECEIVER_PORT));

        Arrays.stream(Configuration.string(DefaultConfSettings.NEIGHBORS).split(" ")).distinct()
        .filter(s -> !s.isEmpty()).map(Node::uri).map(Optional::get).peek(u -> {
            if (!"udp".equals(u.getScheme())) {
                log.warn("WARNING: '{}' is not a valid udp:// uri schema.", u);
            }
        }).filter(u -> "udp".equals(u.getScheme()))
        .map(u -> new Neighbor(new InetSocketAddress(u.getHost(), u.getPort()),false,true)).peek(u -> {
            if (Configuration.booling(DefaultConfSettings.DEBUG)) {
                log.debug("-> Adding neighbor : {} ", u.getAddress());
            }
        }).forEach(neighbors::add);

        Arrays.stream(Configuration.string(DefaultConfSettings.NEIGHBORS).split(" ")).distinct()
        .filter(s -> !s.isEmpty()).map(Node::uri).map(Optional::get).peek(u -> {
            if (!"tcp".equals(u.getScheme())) {
                log.warn("WARNING: '{}' is not a valid tcp:// uri schema.", u);
            }
        }).filter(u -> "tcp".equals(u.getScheme()))
        .map(u -> new Neighbor(new InetSocketAddress(u.getHost(), u.getPort()),true,true)).peek(u -> {
            if (Configuration.booling(DefaultConfSettings.DEBUG)) {
                log.debug("-> Adding neighbor : {} ", u.getAddress());
            }
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

            final Curl curl = new Curl();
            final int[] receivedTransactionTrits = new int[TransactionViewModel.TRINARY_SIZE];
            final byte[] requestedTransaction = new byte[Hash.SIZE_IN_BYTES];

            log.info("Spawning Receiver Thread");

            final SecureRandom rnd = new SecureRandom();
            long randomTipBroadcastCounter = 1;

            while (!shuttingDown.get()) {

                try {
                    socket.receive(receivingPacket);

                    if (receivingPacket.getLength() == TRANSACTION_PACKET_SIZE) {

                        for (final Neighbor neighbor : neighbors) {
                            if (neighbor.getAddress().equals(receivingPacket.getSocketAddress())) {
                                try {
                                    neighbor.incAllTransactions();
                                    final TransactionViewModel receivedTransactionViewModel = new TransactionViewModel(receivingPacket.getData(), receivedTransactionTrits, curl);
                                    long timestamp = (int) Converter.longValue(receivedTransactionViewModel.trits(), TransactionViewModel.TIMESTAMP_TRINARY_OFFSET, 27);
                                    if (timestamp > TIMESTAMP_THRESHOLD) {
                                        //if ((pointer = StorageTransactions.instance().storeTransaction(receivedTransactionViewModel.getHash(), receivedTransactionViewModel, false)) != 0L) {
                                        if(receivedTransactionViewModel.store().get()) {
                                            receivedTransactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                                            receivedTransactionViewModel.update("arrivalTime");
                                            neighbor.incNewTransactions();
                                            broadcast(receivedTransactionViewModel);
                                        }

                                        byte[] transactionPointer = Hash.NULL_HASH.bytes();
                                        System.arraycopy(receivingPacket.getData(), TransactionViewModel.SIZE, requestedTransaction, 0, TransactionViewModel.HASH_SIZE);

                                        TransactionViewModel transactionViewModel;

                                        if (Arrays.equals(requestedTransaction, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES)
                                                && (Milestone.latestMilestoneIndex > 0)
                                                && (Milestone.latestMilestoneIndex == Milestone.latestSolidSubtangleMilestoneIndex)) {
                                            //
                                            if (randomTipBroadcastCounter % 60 == 0) {
                                                byte[] mBytes = Milestone.latestMilestone.bytes();
                                                if (!Arrays.equals(mBytes, Hash.NULL_HASH.bytes())) {
                                                    transactionPointer = mBytes;
                                                }
                                            } else if (randomTipBroadcastCounter % 48 == 0) {
                                                byte[] mBytes = Milestone.latestMilestone.bytes();
                                                if (!Arrays.equals(mBytes, Hash.NULL_HASH.bytes())) {
                                                    transactionPointer = mBytes;

                                                    final TransactionViewModel milestoneTx = TransactionViewModel.fromHash(transactionPointer);
                                                    final Bundle bundle = new Bundle(milestoneTx.getBundleHash());
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
                                                //final String[] tips = StorageTransactions.instance().tips().stream().map(Hash::toString).toArray(size -> new String[size]);
                                                final Hash rndTipHash = tips[rnd.nextInt(tips.length)];

                                                transactionPointer = rndTipHash.bytes();
                                            }
                                            randomTipBroadcastCounter++;

                                        } else {
                                            transactionPointer = requestedTransaction;
                                        }
                                        transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                                        if (!Arrays.equals(transactionPointer, Hash.NULL_HASH.bytes())
                                                && transactionPointer != Hash.NULL_HASH.bytes()) {
                                            synchronized (sendingPacket) {
                                                System.arraycopy(transactionViewModel.getBytes(), 0, sendingPacket.getData(), 0, TransactionViewModel.SIZE);
                                                ScratchpadViewModel.instance().transactionToRequest(sendingPacket.getData(), TransactionViewModel.SIZE);
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
                                    ScratchpadViewModel.instance().transactionToRequest(sendingPacket.getData(),
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
                    System.arraycopy(transactionViewModel.getHash(), 0, tipRequestingPacket.getData(), TransactionViewModel.SIZE,
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
                    if (transaction1.getHash()[i] != transaction2.getHash()[i]) {
                        return transaction2.getHash()[i] - transaction1.getHash()[i];
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
