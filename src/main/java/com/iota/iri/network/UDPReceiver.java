package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.iota.iri.network.Node.TRANSACTION_PACKET_SIZE;

/**
 * Created by paul on 4/16/17.
 */
public class UDPReceiver {
    private static final Logger log = LoggerFactory.getLogger(UDPReceiver.class);

    private static final UDPReceiver instance = new UDPReceiver();
    private final DatagramPacket receivingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private DatagramSocket socket;

    public void init(int port) throws Exception {

        socket = new DatagramSocket(port);
        log.info("UDP replicator is accepting connections on udp port " + port);

        executor.submit(spawnReceiverThread());

        executor.shutdown();
    }

    private Runnable spawnReceiverThread() {
        return () -> {


            log.info("Spawning Receiver Thread");

            final Curl curl = new Curl();
            final byte[] requestedTransaction = new byte[Hash.SIZE_IN_BYTES];
            while (!shuttingDown.get()) {

                try {
                    socket.receive(receivingPacket);

                    if (receivingPacket.getLength() == TRANSACTION_PACKET_SIZE) {
                        Node.instance().processReceivedData(receivingPacket.getData(), receivingPacket.getSocketAddress(), "udp", curl);
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

    public void send(final DatagramPacket packet) {
        try {
            socket.send(packet);
        } catch (IOException e) {
            // ignore
        }
    }

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        executor.shutdown();
        executor.awaitTermination(6, TimeUnit.SECONDS);

    }

    public static UDPReceiver instance() {
        return instance;
    }
}
