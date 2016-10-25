package com.iota.iri.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

import com.iota.iri.Milestone;
import com.iota.iri.Neighbor;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;

public class Node {

    static final int TRANSACTION_PACKET_SIZE = 1650;

    static final int QUEUE_SIZE = 1000;
    static final int PAUSE_BETWEEN_TRANSACTIONS = 1;

    static final int REQUESTED_TRANSACTION_OFFSET = Transaction.SIZE;

    public static DatagramSocket socket;
    private static boolean shuttingDown;

    static final List<Neighbor> neighbors = new CopyOnWriteArrayList<>();

    static final ConcurrentSkipListSet<Transaction> queuedTransactions = new ConcurrentSkipListSet<>((transaction1, transaction2) -> {

        if (transaction1.weightMagnitude == transaction2.weightMagnitude) {

            for (int i = 0; i < Transaction.HASH_SIZE; i++) {
                if (transaction1.hash[i] != transaction2.hash[i]) {
                    return transaction2.hash[i] - transaction1.hash[i];
                }
            }
            return 0;

        } else {
            return transaction2.weightMagnitude - transaction1.weightMagnitude;
        }
    });

    static final DatagramPacket receivingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);
    static final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);
    static final DatagramPacket tipRequestingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);

    public static void launch(final String[] args) throws Exception {

        socket = new DatagramSocket(Integer.parseInt(args[0]));

        for (final String arg : args) {

            final URI uri = new URI(arg);
            if (uri.getScheme() != null && uri.getScheme().equals("udp")) {
                neighbors.add(new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort())));
            }
        }

        (new Thread(() -> {

            final int[] receivedTransactionTrits = new int[Transaction.TRINARY_SIZE];
            final Curl curl = new Curl();
            final byte[] requestedTransaction = new byte[Transaction.HASH_SIZE];

            while (!shuttingDown) {

                try {

                    socket.receive(receivingPacket);

                    if (receivingPacket.getLength() == TRANSACTION_PACKET_SIZE) {

                        for (final Neighbor neighbor : neighbors) {

                            if (neighbor.address().equals(receivingPacket.getSocketAddress())) {

                                try {

                                    neighbor.numberOfAllTransactions++;
                                    final Transaction receivedTransaction = new Transaction(receivingPacket.getData(), receivedTransactionTrits, curl);
                                    if (Storage.storeTransaction(receivedTransaction.hash, receivedTransaction, false) != 0) {

                                        neighbor.numberOfNewTransactions++;
                                        broadcast(receivedTransaction);
                                    }

                                    final long transactionPointer;
                                    System.arraycopy(receivingPacket.getData(), REQUESTED_TRANSACTION_OFFSET, requestedTransaction, 0, Transaction.HASH_SIZE);
                                    if (Arrays.equals(requestedTransaction, receivedTransaction.hash)) {
                                        transactionPointer = Storage.transactionPointer(Milestone.latestMilestone.bytes());
                                    } else {
                                        transactionPointer = Storage.transactionPointer(requestedTransaction);
                                    }
                                    if (transactionPointer > Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET) {

                                        System.arraycopy(Storage.loadTransaction(transactionPointer).bytes, 0, sendingPacket.getData(), 0, Transaction.SIZE);
                                        Storage.transactionToRequest(sendingPacket.getData(), REQUESTED_TRANSACTION_OFFSET);
                                        neighbor.send(sendingPacket);
                                    }

                                } catch (final RuntimeException e) {

                                    e.printStackTrace();
                                    neighbor.numberOfInvalidTransactions++;
                                }
                                break;
                            }
                        }

                    } else {
                        receivingPacket.setLength(TRANSACTION_PACKET_SIZE);
                    }

                } catch (final Exception e) {

                    e.printStackTrace();
                }
            }

        }, "Receiver")).start();

        (new Thread(() -> {

            while (!shuttingDown) {

                try {

                    final Transaction transaction = queuedTransactions.pollFirst();
                    if (transaction != null) {

                        for (final Neighbor neighbor : neighbors) {

                            try {

                                System.arraycopy(transaction.bytes, 0, sendingPacket.getData(), 0, Transaction.SIZE);
                                Storage.transactionToRequest(sendingPacket.getData(), REQUESTED_TRANSACTION_OFFSET);
                                neighbor.send(sendingPacket);

                            } catch (final Exception e) {
                            }
                        }
                    }

                    Thread.sleep(PAUSE_BETWEEN_TRANSACTIONS);

                } catch (final Exception e) {

                    e.printStackTrace();
                }
            }

        }, "Broadcaster")).start();

        (new Thread(() -> {

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

                    e.printStackTrace();
                }
            }

        }, "Tips Requester")).start();
    }

    public static void broadcast(final Transaction transaction) {

        queuedTransactions.add(transaction);
        if (queuedTransactions.size() > QUEUE_SIZE) {
            queuedTransactions.pollLast();
        }
    }

    public static void shutDown() {
        shuttingDown = true;
    }
}
