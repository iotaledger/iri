package com.iota.iri.network;

import com.iota.iri.hash.Curl;
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

    private static final UDPReceiver instance = new UDPReceiver();
    private final DatagramPacket receivingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE],
            TRANSACTION_PACKET_SIZE);
    private final int NUM_PROCESSING_THREADS = Runtime.getRuntime().availableProcessors();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private DatagramSocket socket;
    private Thread receivingThread;
    private final Thread[] processingThreads = new Thread[NUM_PROCESSING_THREADS];
    private volatile AtomicBoolean[] processingFlags = new AtomicBoolean[NUM_PROCESSING_THREADS];;
    private final byte[][] bytesToProcess = new byte[NUM_PROCESSING_THREADS][TRANSACTION_PACKET_SIZE];
    private final SocketAddress[] socketAddresses = new SocketAddress[NUM_PROCESSING_THREADS];

    public void init(int port) throws Exception {

        socket = new DatagramSocket(port);
        log.info("UDP replicator is accepting connections on udp port " + port);

        receivingThread = new Thread(spawnReceiverThread(), "UDP receiving thread");
        for(int i = 0; i < NUM_PROCESSING_THREADS; i++) {
            processingFlags[i] = new AtomicBoolean(false);
            processingThreads[i] = new Thread(spawnProcessingThread(i));
            processingThreads[i].start();
        }
        receivingThread.start();
    }

    private Runnable spawnReceiverThread() {
        return () -> {


            log.info("Spawning Receiver Thread");

            while (!shuttingDown.get()) {

                try {
                    socket.receive(receivingPacket);

                    if (receivingPacket.getLength() == TRANSACTION_PACKET_SIZE) {
                        submitPacketToProcessor(receivingPacket.getData(), receivingPacket.getSocketAddress());
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

    private void submitPacketToProcessor (byte[] bytes, SocketAddress socketAddress) {
        int index;
        boolean canProcess = false;
        for(index = NUM_PROCESSING_THREADS; index-- > 0;) {
            if(!processingFlags[index].get()) {
                canProcess = true;
                break;
            }
        }
        if(canProcess) {
            synchronized (bytesToProcess[index]) {
                System.arraycopy(bytes, 0, bytesToProcess[index], 0, TRANSACTION_PACKET_SIZE);
                socketAddresses[index] = socketAddress;
                processingFlags[index].set(true);
            }
        }
    }

    private Runnable spawnProcessingThread(int index) {
        return () -> {
            Curl curl = new Curl();
            while(!shuttingDown.get()) {
                if(processingFlags[index].get()) {
                    synchronized (bytesToProcess[index]) {
                    }
                    Node.instance().processReceivedData(bytesToProcess[index], socketAddresses[index], "udp", curl);
                    processingFlags[index].set(false);
                }
                try {
                    Thread.sleep(0);
                } catch (InterruptedException e) {
                    log.error("Processing Thread interrupted. ", e);
                }
            }
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
        for(Thread thread: processingThreads) {
            thread.join();
        }
        receivingThread.join(6000L);
    }

    public static UDPReceiver instance() {
        return instance;
    }
}
