package com.iota.iri.network;

import com.iota.iri.conf.NodeConfig;
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

/**
 * Created by paul on 4/16/17.
 */
public class UDPReceiver {
    private static final Logger log = LoggerFactory.getLogger(UDPReceiver.class);

    private final DatagramPacket receivingPacket;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final int port;
    private final Node node;
    private final int packetSize;

    private DatagramSocket socket;

    private final int PROCESSOR_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() * 4 );

    private final ExecutorService processor = new ThreadPoolExecutor(PROCESSOR_THREADS, PROCESSOR_THREADS, 5000L,
                                            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(PROCESSOR_THREADS, true),
                                             new ThreadPoolExecutor.AbortPolicy());

    private Thread receivingThread;

    public UDPReceiver(Node node, NodeConfig config) {
        this.node = node;
        this.port = config.getUdpReceiverPort();
        this.packetSize = config.getTransactionPacketSize();
        this.receivingPacket = new DatagramPacket(new byte[packetSize], packetSize);
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

                if (((processed + dropped) % 50000 == 49999)) {
                    log.info("Receiver thread processed/dropped ratio: "+processed+"/"+dropped);
                    processed = 0;
                    dropped = 0;
                }

                try {
                    socket.receive(receivingPacket);

                    if (node.optimizeNetworkEnabled) {
                        if (receivingPacket.getLength() == node.transactionSize || receivingPacket.getLength() == node.broadcastHashSize || receivingPacket.getLength() == node.requestHashSize) {
                            byte[] bytes = Arrays.copyOf(receivingPacket.getData(), receivingPacket.getLength());
                            SocketAddress address = receivingPacket.getSocketAddress();

                            processor.submit(() -> node.preProcessReceivedOptimizedData(bytes, address, "udp"));
                            processed++;

                            Thread.yield();
                        } else {
                            log.error("Wrong packet size {}, it should be one of {}, {}, and {}",
                                    receivingPacket.getLength(), node.transactionSize, node.broadcastHashSize, node.requestHashSize);
                        }
                    } else {
                        if (receivingPacket.getLength() == packetSize) {

                            byte[] bytes = Arrays.copyOf(receivingPacket.getData(), receivingPacket.getLength());
                            SocketAddress address = receivingPacket.getSocketAddress();

                            processor.submit(() -> node.preProcessReceivedData(bytes, address, "udp"));
                            processed++;

                            Thread.yield();

                        } else {
                            receivingPacket.setLength(packetSize);
                        }
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

    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        processor.shutdown();
        processor.awaitTermination(6, TimeUnit.SECONDS);
        try {
            receivingThread.join(6000L);
        }
        catch (Exception e) {
            // ignore
        }
    }

}
