package com.iota.iri.network;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.iota.iri.BundleValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.network.replicator.ReplicatorSinkPool;
import com.iota.iri.service.TipsManager;
import com.iota.iri.controllers.BundleViewModel;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionRequester;
import com.iota.iri.controllers.TransactionViewModel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.hash.Curl;
import com.iota.iri.utils.Converter;

/**
 * The class node is responsible for managing Thread's connection.
 */
public class Node {

    private static final Logger log = LoggerFactory.getLogger(Node.class);


    public  static final int TRANSACTION_PACKET_SIZE = 1650;
    private static final int QUEUE_SIZE = 1000;
    private static final int PAUSE_BETWEEN_TRANSACTIONS = 1;
    private static Node instance = new Node();

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();
    private final ConcurrentSkipListSet<TransactionViewModel> queuedTransactionViewModels = weightQueue();

    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);
    private final DatagramPacket tipRequestingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private static long TIMESTAMP_THRESHOLD = 0L;

    public static void setTIMESTAMP_THRESHOLD(long tIMESTAMP_THRESHOLD) {
        TIMESTAMP_THRESHOLD = tIMESTAMP_THRESHOLD;
    }

    private volatile long randomTipBroadcastCounter = 1;
    private double P_DROP_TRANSACTION;
    private final SecureRandom rnd = new SecureRandom();

    private static long lastFileNumber = 0L;
    private static Object lock = new Object();

    public void init(double pDropTransaction, String neighborList) throws Exception {
        P_DROP_TRANSACTION = pDropTransaction;
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

        executor.shutdown();
    }

    private final Map<String, String> neighborIpCache = new HashMap<>();
    
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
    
    public void processReceivedData(byte[] receivedData, SocketAddress senderAddress, String uriScheme, Curl curl, int[] receivedTransactionTrits, byte[] requestedTransaction) {
        long timestamp;
        TransactionViewModel receivedTransactionViewModel, transactionViewModel;
        Hash transactionPointer;

        boolean addressMatch = false;
        for (final Neighbor neighbor : neighbors) {
            if (neighbor instanceof TCPNeighbor) {
                if (senderAddress.toString().contains(neighbor.getHostAddress())) addressMatch = true;
            }
            else {
                if (neighbor.getAddress().toString().contains(senderAddress.toString())) addressMatch = true;
            }
            if (addressMatch) {
                try {
                    neighbor.incAllTransactions();
                    if(rnd.nextDouble() < P_DROP_TRANSACTION) {
                        //log.info("Randomly dropping transaction. Stand by... ");
                        break;
                    }
                    receivedTransactionViewModel = new TransactionViewModel(receivedData, receivedTransactionTrits, curl);
                    timestamp = receivedTransactionViewModel.getTimestamp();
                    if (timestamp == 0 || timestamp > TIMESTAMP_THRESHOLD) {
                        if(receivedTransactionViewModel.store()) {
                            receivedTransactionViewModel.setArrivalTime(System.currentTimeMillis());
                            receivedTransactionViewModel.update("arrivalTime");
                            receivedTransactionViewModel.updateSender(neighbor instanceof TCPNeighbor?
                                    senderAddress.toString(): neighbor.getAddress().toString() );
                            neighbor.incNewTransactions();
                            broadcast(receivedTransactionViewModel);
                            if (Configuration.booling(DefaultConfSettings.EXPORT)) {
                                try {
                                    PrintWriter writer;
                                    if(!receivedTransactionViewModel.isSolid()) {
                                        Path path = Paths.get("export-solid", String.valueOf(TipsManager.getFileNumber()) + ".tx");
                                        long height = receivedTransactionViewModel.getHeight();
                                        writer = new PrintWriter(path.toString(), "UTF-8");
                                        writer.println(receivedTransactionViewModel.getHash().toString());
                                        writer.println(Converter.trytes(receivedTransactionViewModel.trits()));
                                        writer.println(receivedTransactionViewModel.getSender());                        
                                        writer.println("Height: " + String.valueOf(height));
                                        writer.close();
                                        log.info("Height: " + height);
                                    }
                                } catch (UnsupportedEncodingException | FileNotFoundException e) {
                                    log.error("File export failed", e);
                                } catch (Exception e) {
                                    log.error("Transaction load failed. ", e);
                                }
                            }
                        }
                        System.arraycopy(receivedData, TransactionViewModel.SIZE, requestedTransaction, 0, TransactionRequester.REQUEST_HASH_SIZE);

                        if (Arrays.equals(requestedTransaction, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES)) {
                            transactionPointer = getNextTransactionPointer(requestedTransaction);
                            transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                        } else {
                            transactionViewModel = TransactionViewModel.find(Arrays.copyOf(requestedTransaction, TransactionRequester.REQUEST_HASH_SIZE));
                            log.debug("Requested Hash: " + new Hash(requestedTransaction) + " \nFound: " +
                                    transactionViewModel.getHash());
                        }
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
                } catch (InterruptedException e) {
                    log.error("Interrupted");
                } catch (ExecutionException e) {
                    log.error("Transdaction propagation exception ",e);
                } catch (Exception e) {
                    log.error("Error accessing persistence store.");
                    neighbor.incInvalidTransactions();
                }
                break;
            }            
        }
        if (!addressMatch) {
            // TODO This code is only for testnet/stresstest - remove for mainnet
            String uriString = uriScheme + ":/" + senderAddress.toString();
            log.info("Adding non-tethered neighbor: "+uriString);
            try {
                final URI uri = new URI(uriString);
                // 3rd parameter false (not tcp), 4th parameter true (configured tethering)
                final Neighbor newneighbor;
                if(uriScheme.equals("tcp")) {
                    newneighbor = new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), false);
                } else {
                    newneighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), false);
                }
                if (!getNeighbors().contains(newneighbor)) {
                    getNeighbors().add(newneighbor);
                }
            }
            catch (URISyntaxException e) {
                log.error("Invalid URI string: "+uriString);
            }
        }
    }

    private Hash getNextTransactionPointer(byte[] requestedTransaction) throws Exception {
        Hash mBytes, transactionPointer = Hash.NULL_HASH;
        if (Milestone.latestMilestoneIndex > 0 && Milestone.latestMilestoneIndex == Milestone.latestSolidSubtangleMilestoneIndex) {
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
                    final BundleValidator bundleValidator = new BundleValidator(BundleViewModel.fromHash(milestoneTx.getBundleHash()));
                    Collection<List<TransactionViewModel>> tList = bundleValidator.getTransactions();
                    if (tList != null && tList.size() != 0) {
                        for (final List<TransactionViewModel> bundleTransactionViewModels : bundleValidator.getTransactions()) {
                            if (bundleTransactionViewModels.size() > 1) {
                                transactionPointer = bundleTransactionViewModels.get(1).getHash();
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
                            TransactionRequester.REQUEST_HASH_SIZE);
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
        return queuedTransactionViewModels.size();
    }

    public int howManyNeighbors() {
        return neighbors.size();
    }

    public List<Neighbor> getNeighbors() {
        return neighbors;
    }

    public static Node instance() {
        return instance;
    }
}
