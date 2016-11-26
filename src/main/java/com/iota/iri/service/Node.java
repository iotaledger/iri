package com.iota.iri.service;

import com.iota.iri.Milestone;
import com.iota.iri.Neighbor;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * The class node is responsible for managing Thread's connection.
 */
public class Node {

	private static final Logger log = LoggerFactory.getLogger(Node.class);

	private static final Node instance = new Node();
	
    private static final int TRANSACTION_PACKET_SIZE = 1650;
    private static final int QUEUE_SIZE = 1000;
    private static final int PAUSE_BETWEEN_TRANSACTIONS = 1;

    public DatagramSocket socket;
    private boolean shuttingDown = false;

    private final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();
    private final ConcurrentSkipListSet<Transaction> queuedTransactions = weigthQueue();

    private final DatagramPacket receivingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);
    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);
    private final DatagramPacket tipRequestingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);

    public void init(final String[] args) throws Exception {

        socket = new DatagramSocket(Integer.parseInt(args[0]));
        
        Arrays.asList(args).stream()
        	.skip(1)
        	.map(API::uri)
        	.map(Optional::get)
        	.peek(u -> {
        		if (!"udp".equals(u.getScheme())) {
                	log.warn("WARNING: '{}' is not a valid udp:// uri schema.", u);
                }	
        	})
        	.filter(u -> "udp".equals(u.getScheme()))
        	.map(u -> new Neighbor(new InetSocketAddress(u.getHost(), u.getPort())))
        	.forEach(n -> {
        		neighbors.add(n);
            });

        spawnReceiverThread();
        spawnBroadcasterThread();
        spawnTipRequesterThread();
    }

	private void spawnReceiverThread() {
		(new Thread(() -> {

        	final Curl curl = new Curl();
            final int[] receivedTransactionTrits = new int[Transaction.TRINARY_SIZE];
            final byte[] requestedTransaction = new byte[Transaction.HASH_SIZE];

            log.info("Spawing Receiver Thread");
            
            while (!shuttingDown) {

                try {
                    socket.receive(receivingPacket);

                    if (receivingPacket.getLength() == TRANSACTION_PACKET_SIZE) {

                        for (final Neighbor neighbor : neighbors) {
                            if (neighbor.getAddress().equals(receivingPacket.getSocketAddress())) {
                                try {

                                    neighbor.numberOfAllTransactions++;
                                    final Transaction receivedTransaction = new Transaction(receivingPacket.getData(), receivedTransactionTrits, curl);
                                    if (Storage.storeTransaction(receivedTransaction.hash, receivedTransaction, false) != 0) {
                                        neighbor.numberOfNewTransactions++;
                                        broadcast(receivedTransaction);
                                    }

                                    final long transactionPointer;
                                    System.arraycopy(receivingPacket.getData(), Transaction.SIZE, requestedTransaction, 0, Transaction.HASH_SIZE);
                                    if (Arrays.equals(requestedTransaction, receivedTransaction.hash)) {
                                        transactionPointer = Storage.transactionPointer(Milestone.latestMilestone.bytes());
                                    } else {
                                        transactionPointer = Storage.transactionPointer(requestedTransaction);
                                    }
                                    if (transactionPointer > Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET) {
                                    	synchronized (sendingPacket) {
                                    		System.arraycopy(Storage.loadTransaction(transactionPointer).bytes, 0, sendingPacket.getData(), 0, Transaction.SIZE);
                                    		Storage.transactionToRequest(sendingPacket.getData(), Transaction.SIZE);
                                    		neighbor.send(sendingPacket);
                                    	}
                                    }
                                } catch (final RuntimeException e) {
                                	log.error("Invalid Transaction Error:", e);
                                    neighbor.numberOfInvalidTransactions++;
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
        }, "Receiver")).start();
	}

	private void spawnBroadcasterThread() {
		(new Thread(() -> {

			log.info("Spawing Broadcaster Thread");
            
            while (!shuttingDown) {

                try {
                    final Transaction transaction = queuedTransactions.pollFirst();
                    if (transaction != null) {
                    	
                        for (final Neighbor neighbor : neighbors) {
                            try {
                            	synchronized (sendingPacket) {
                            		System.arraycopy(transaction.bytes, 0, sendingPacket.getData(), 0, Transaction.SIZE);
                            		Storage.transactionToRequest(sendingPacket.getData(), Transaction.SIZE);
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
        }, "Broadcaster")).start();
	}

	private void spawnTipRequesterThread() {
		(new Thread(() -> {
			
			log.info("Spawing Tips Requester Thread");

            while (!shuttingDown) {

                try {
                    final Transaction transaction = Storage.loadTransaction(Storage.transactionPointer(Milestone.latestMilestone.bytes()));
                    System.arraycopy(transaction.bytes, 0, tipRequestingPacket.getData(), 0, Transaction.SIZE);
                    System.arraycopy(transaction.hash, 0, tipRequestingPacket.getData(), Transaction.SIZE, Transaction.HASH_SIZE);
                    for (final Neighbor neighbor : neighbors) {
                        neighbor.send(tipRequestingPacket);
                    }
                    Thread.sleep(5000);
                } catch (final Exception e) {
                	log.error("Tips Requester Thread Exception:", e);
                }
            }

        }, "Tips Requester")).start();
	}

    private static ConcurrentSkipListSet<Transaction> weigthQueue() {
    	return new ConcurrentSkipListSet<>((transaction1, transaction2) -> {
    		
            if (transaction1.weightMagnitude == transaction2.weightMagnitude) {
                for (int i = 0; i < Transaction.HASH_SIZE; i++) {
                    if (transaction1.hash[i] != transaction2.hash[i]) {
                        return transaction2.hash[i] - transaction1.hash[i];
                    }
                }
                return 0;
            } 

            return transaction2.weightMagnitude - transaction1.weightMagnitude;
        });
	}

	public void broadcast(final Transaction transaction) {
        queuedTransactions.add(transaction);
        if (queuedTransactions.size() > QUEUE_SIZE) {
            queuedTransactions.pollLast();
        }
    }

    public void shutDown() {
        shuttingDown = true;
    }
    
    private Node() {}
    
    // helpers methods
    
    public boolean removeNeighbor(final URI uri) {
    	return neighbors.remove(new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort())));
    }
    
    public static Node instance() {
    	return instance;
    }
    
    public int queuedTransactionsSize() {
		return queuedTransactions.size();
	}
    
    public int howManyNeighbors() {
		return neighbors.size();
	}

	public List<Neighbor> getNeighbors() {
		return neighbors;
	}

	public void send(DatagramPacket packet) {
		try {
			socket.send(packet);
		} catch (IOException e) {
			// ignore
		}
	}
}
