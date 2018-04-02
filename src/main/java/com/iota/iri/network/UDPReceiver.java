package com.iota.iri.network;

import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.iota.iri.network.Node.TRANSACTION_PACKET_SIZE;

/**
 * Created by paul on 4/16/17.
 */
public class UDPReceiver {
    private static final Logger log = LoggerFactory.getLogger(UDPReceiver.class);

    private final DatagramPacket receivingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
        TRANSACTION_PACKET_SIZE);

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final int port;
    private final Node node;

    private DatagramSocket socket;

    private final int PROCESSOR_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() * 4);

    private final ExecutorService processor = new ThreadPoolExecutor(PROCESSOR_THREADS, PROCESSOR_THREADS, 5000L,
        TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(PROCESSOR_THREADS, true),
        new ThreadPoolExecutor.AbortPolicy());

    private Thread receivingThread;

    public UDPReceiver(final int port, final Node node) {
        this.port = port;
        this.node = node;
    }

    public void init() throws Exception {

        socket = new DatagramSocket(port);
        node.setUDPSocket(socket);
        log.info("UDP replicator is accepting connections on udp port " + port);

        receivingThread = new Thread(spawnReceiverThread(), "UDP receiving thread");
        receivingThread.start();
    }

    private Runnable spawnReceiverThread() {
        return () -> {


            log.info("Spawning Receiver Thread");

            final Sponge curl = SpongeFactory.create(SpongeFactory.Mode.CURLP81);
            final byte[] requestedTransaction = new byte[Hash.SIZE_IN_BYTES];

            int processed = 0, dropped = 0;

            while (!shuttingDown.get()) {

                if (((processed + dropped) % 50000 == 0)) {
                    log.info("Receiver thread processed/dropped ratio: " + processed + "/" + dropped);
                    processed = 0;
                    dropped = 0;
                }

                try {
                    socket.receive(receivingPacket);

                    if (receivingPacket.getLength() == TRANSACTION_PACKET_SIZE) {

                        byte[] bytes = Arrays.copyOf(receivingPacket.getData(), receivingPacket.getLength());
                        SocketAddress address = receivingPacket.getSocketAddress();

                        processor.submit(() -> node.preProcessReceivedData(bytes, address, "udp"));
                        processed++;

                        Thread.yield();

                    } else {
                        receivingPacket.setLength(TRANSACTION_PACKET_SIZE);
                    }
                } catch (final RejectedExecutionException e) {
                    //no free thread, packet dropped
                    dropped++;

                } catch (final Exception e) {
                    log.error("Receiver Thread Exception:", e);
                }
            }
            log.info("Shutting down spawning Receiver Thread");
        };
    }

    public void send(final DatagramPacket packet) {
        try {
            if (socket != null) {
                socket.send(packet);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public void shutdown() {
        shuttingDown.set(true);
        processor.shutdown();
        try {
            processor.awaitTermination(6, TimeUnit.SECONDS);
            receivingThread.join(6000L);
        } catch (Exception e) {
            log.info("interrupted");
            Thread.currentThread().interrupt();
        }
    }

}
