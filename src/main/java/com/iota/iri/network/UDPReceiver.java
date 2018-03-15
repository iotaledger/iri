package com.iota.iri.network;

import com.iota.iri.network.exec.StripedExecutor;
import com.iota.iri.utils.Quiet;
import com.iota.iri.utils.textutils.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.iota.iri.network.Node.TRANSACTION_PACKET_SIZE;

/**
 * Created by paul on 4/16/17.
 */
public class UDPReceiver {
    private static final Logger log = LoggerFactory.getLogger(UDPReceiver.class);

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final int port;
    private final Thread receivingThread;
    private final StripedExecutor.StripeManager stripeManager;
    private final NeighborManager neighborManager;
    private final StripedExecutor<Neighbor, byte[]> stripedExecutor;

    private DatagramSocket socket;

    public UDPReceiver(final int port, NeighborManager neighborManager, StripedExecutor stripedExecutor) {
        this.port = port;
        this.neighborManager = neighborManager;
        this.stripedExecutor = stripedExecutor;
        receivingThread = new Thread(spawnReceiverThread(), "UDPReceiver");
        receivingThread.setDaemon(true);
        stripeManager = new StripedExecutor.StripeManager(4, "UDPReceiver");
    }


    public void init() throws Exception {
        socket = new DatagramSocket(port);
        neighborManager.setUDPDatagramSocket(socket);
        log.info("Accepting connections on udp port {} ...", port);
        receivingThread.start();
    }


    private static class Stats {

        final Map<InetSocketAddress, int[]> addressContacts = new LinkedHashMap<>();

        long received = 0L;
        long totalReceived = 0L;

        long processed = 0L;
        long dropped = 0L;
        long unknownPackets = 0L;
        long malformedPackets = 0L;

        public void track(InetSocketAddress address) {
            addressContacts.computeIfAbsent(address, k -> new int[]{0})[0]++;
        }

        public void loop() {
            // A TON OF STATS!
            if (received++ == 20000) {
                totalReceived += received;
                received = 0;
                log.info("{} tot {} proc  {} drop  {} unk  {} mal",
                        Format.leftpad(totalReceived, 6),
                        Format.leftpad(processed, 6),
                        Format.leftpad(dropped, 3),
                        Format.leftpad(unknownPackets, 6),
                        Format.leftpad(malformedPackets, 3));

                // lets see who is spamming us
                if (totalReceived % 100_000 == 0) {
                    addressContacts.entrySet().stream()
                            .sorted(Comparator.comparingInt(ob -> -1 * ob.getValue()[0]))
                            .limit(10)
                            .forEach(entry -> log.info("Top sender: {} packets from {}", entry.getValue()[0], entry.getKey()));
                }
            }
        }
    }

    private Runnable spawnReceiverThread() {
        return () -> {
            log.info("Starting ... ");

            final DatagramPacket receivingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);
            final Stats stats = new Stats();

            while (!shuttingDown.get()) {
                receivingPacket.setLength(TRANSACTION_PACKET_SIZE);
                try {
                    socket.receive(receivingPacket);
                    stats.loop();

                    if (receivingPacket.getLength() != TRANSACTION_PACKET_SIZE) {
                        stats.malformedPackets++;

                    } else {
                        InetSocketAddress address = (InetSocketAddress) receivingPacket.getSocketAddress();
                        stats.track(address);

                        String check = address.toString();
                        Neighbor neighbor = neighborManager.findFirst(UDPNeighbor.class, n -> n.addressMatches(check));
                        if (neighbor != null) {
                            byte[] data = receivingPacket.getData().clone();
                            stripedExecutor.submitStripe(stripeManager.stripe(), () -> stripedExecutor.process(neighbor, data));
                            stats.processed++;
                        } else {
                            stats.unknownPackets++;
                        }
                    }
                } catch (RejectedExecutionException e) {
                    //no free thread, packet dropped
                    stats.dropped++;
                } catch (Exception e) {
                    if (!shuttingDown.get()) {
                        log.error("Exception", e);
                    }
                }
            }
            log.info("Stopped");
        };
    }

    public void shutdown() throws InterruptedException {
        log.info("Shutting down ... ");
        shuttingDown.set(true);
        Quiet.close(socket);
        try {
            receivingThread.join(6000L);
        } catch (Exception e) {
            // ignore
        }
    }
}

