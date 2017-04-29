package com.iota.iri.network;

import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.*;
import com.iota.iri.model.Hash;
import com.iota.iri.network.replicator.ReplicatorSinkPool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;
import com.iota.iri.hash.Curl;

/**
 * The class node is responsible for managing Thread's connection.
 */
public class Node {

    private static final Logger log = LoggerFactory.getLogger(Node.class);


    public  static final int TRANSACTION_PACKET_SIZE = 1653;
    private static final int QUEUE_SIZE = 1000;
    private static final int PAUSE_BETWEEN_TRANSACTIONS = 1;
    public  static final int REQUEST_HASH_SIZE = 49;
    private static double P_SELECT_MILESTONE;
    private static Node instance = new Node();

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();
    private final ConcurrentSkipListSet<TransactionViewModel> queuedTransactionViewModels = weightQueue();

    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);
    private final DatagramPacket tipRequestingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private double P_DROP_TRANSACTION;
    private static final SecureRandom rnd = new SecureRandom();
    private double P_SEND_MILESTONE;

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
        TipsViewModel.loadTipHashes();
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
    
    public void processReceivedData(byte[] receivedData, SocketAddress senderAddress, String uriScheme, Curl curl) {
        long timestamp;
        TransactionViewModel receivedTransactionViewModel, transactionViewModel;
        Hash transactionPointer;

        boolean addressMatch = false;
        for (final Neighbor neighbor : getNeighbors()) {
            boolean stored = false;
            if (neighbor instanceof TCPNeighbor) {
                if (senderAddress.toString().contains(neighbor.getHostAddress())) addressMatch = true;
            }
            else {
                if (neighbor.getAddress().toString().contains(senderAddress.toString())) addressMatch = true;
            }
            if (addressMatch) {
                neighbor.incAllTransactions();
                if(rnd.nextDouble() < P_DROP_TRANSACTION) {
                    //log.info("Randomly dropping transaction. Stand by... ");
                    break;
                }
                try {
                    receivedTransactionViewModel = TransactionValidator.validate(receivedData, curl);
                } catch (final RuntimeException e) {
                    log.error("Received an Invalid TransactionViewModel. Dropping it...");
                    neighbor.incInvalidTransactions();
                    break;
                }

                {
                    try {
                        stored = receivedTransactionViewModel.store();
                    } catch (Exception e) {
                        log.error("Error accessing persistence store.", e);
                        neighbor.incInvalidTransactions();
                    }
                    if(stored) {
                        receivedTransactionViewModel.setArrivalTime(System.currentTimeMillis());
                        try {
                            receivedTransactionViewModel.update("arrivalTime");
                            receivedTransactionViewModel.updateSender(neighbor instanceof TCPNeighbor?
                                    senderAddress.toString(): neighbor.getAddress().toString() );
                        } catch (Exception e) {
                            log.error("Error updating transactions.", e);
                        }
                        neighbor.incNewTransactions();
                        broadcast(receivedTransactionViewModel);
                    }
                    Hash requestedHash = new Hash(receivedData, TransactionViewModel.SIZE, TransactionRequester.REQUEST_HASH_SIZE);
                    if (requestedHash.equals(receivedTransactionViewModel.getHash())) {
                        try {
                            if (TransactionRequester.instance().numberOfTransactionsToRequest() > 0) {
                                neighbor.incRandomTransactionRequests();
                                transactionPointer = getRandomTipPointer();
                                transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                            }
                            else {
                                transactionViewModel = null;
                            }
                        } catch (Exception e) {
                            log.error("Error getting random tip.", e);
                            break;
                        }
                    } else {
                        try {
                            transactionViewModel = TransactionViewModel.find(Arrays.copyOf(requestedHash.bytes(), TransactionRequester.REQUEST_HASH_SIZE));
                            log.debug("Requested Hash: " + requestedHash + " \nFound: " + transactionViewModel.getHash());
                        } catch (Exception e) {
                            log.error("Error while searching for transaction.", e);
                            break;
                        }
                    }
                    if (transactionViewModel != null && transactionViewModel.getType() == TransactionViewModel.FILLED_SLOT) {
                        //log.info(neighbor.getAddress().getHostString() + "Requested TX Hash: " + transactionPointer);
                        try {
                            sendPacket(sendingPacket, transactionViewModel, neighbor);
                        } catch (Exception e) {
                            log.error("Error fetching transaction to request.", e);
                        }
                    }
                }
                break;
            }            
        }
        if (!addressMatch && Configuration.booling(Configuration.DefaultConfSettings.TESTNET)) {
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

    private Hash getRandomTipPointer() throws Exception {
        final Hash tip = rnd.nextDouble() < P_SEND_MILESTONE? Milestone.latestMilestone: TipsViewModel.getRandomTipHash();
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
                    final TransactionViewModel transactionViewModel = queuedTransactionViewModels.pollFirst();
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

            while (!shuttingDown.get()) {

                try {
                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(Milestone.latestMilestone);
                    System.arraycopy(transactionViewModel.getBytes(), 0, tipRequestingPacket.getData(), 0, TransactionViewModel.SIZE);
                    System.arraycopy(transactionViewModel.getHash().bytes(), 0, tipRequestingPacket.getData(), TransactionViewModel.SIZE,
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

    public void broadcast(final TransactionViewModel transactionViewModel) {
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
        return getNeighbors().size();
    }

    public List<Neighbor> getNeighbors() {
        return Node.instance().neighbors;
    }

    public static Node instance() {
        return instance;
    }
}
