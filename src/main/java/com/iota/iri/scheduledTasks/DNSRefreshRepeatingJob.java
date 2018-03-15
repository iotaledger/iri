package com.iota.iri.scheduledTasks;

import com.iota.iri.conf.Configuration;
import com.iota.iri.network.Neighbor;
import com.iota.iri.network.NeighborManager;
import com.iota.iri.network.Node;
import com.iota.iri.network.TCPNeighbor;
import com.iota.iri.network.exec.StripedExecutor;
import com.iota.iri.utils.ScheduledTask;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * I have had up to 26 second DNS query wait times because of the volume of Datagram Packets on the network.
 * There must be way to cancel this task or spawn it off to finish when it will.
 * <p>
 * It could be as simple as using a Thread to start the task and then interrupt it ....
 * <p>
 * *Potential deadlock: frozen threads found**
 * <p>
 * > It seems that the following threads have not changed their stack for more than 10 seconds.
 * > These threads are possibly (but not necessarily!) in a deadlock or hung.
 * >
 * >    **Frozen for at least 26m 17s**
 * >
 * > java.net.Inet6AddressImpl.lookupAllHostAddr(String) Inet6AddressImpl.java (native)
 * > java.net.InetAddress$2.lookupAllHostAddr(String) InetAddress.java:928
 * > java.net.InetAddress.getAddressesFromNameService(String, InetAddress) InetAddress.java:1323
 * > java.net.InetAddress.getAllByName0(String, InetAddress, boolean) InetAddress.java:1276
 * > java.net.InetAddress.getAllByName(String, InetAddress) InetAddress.java:1192
 * > java.net.InetAddress.getAllByName(String) InetAddress.java:1126
 * > java.net.InetAddress.getByName(String) InetAddress.java:1076
 * <p>
 * USING THIS METHOD HELPS BECAUSE IT ALLOWS TASKS TO NOT BLOCK EACH OTHER
 */
public class DNSRefreshRepeatingJob extends ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(DNSRefreshRepeatingJob.class);

    private static final AtomicInteger instanceCnt = new AtomicInteger();

    private final StripedExecutor.StripeManager stripeManager
            = new StripedExecutor.StripeManager(4, "DNSRefresh" + instanceCnt.getAndIncrement() + "-");


    private final Map<String, String> neighborIpCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Future<?>> activeDNSRequests = Collections.synchronizedMap(new LinkedHashMap<>());

    private final Configuration configuration;
    private final NeighborManager neighborManager;
    private final MessageQ messageQ;
    private final StripedExecutor<?, ?> stripedExecutor;

    /**
     * hostAddress refers to the address part.
     * hostName refers to the name version of the address ie www.google.com
     */
    public DNSRefreshRepeatingJob(Duration duration,
                                  StripedExecutor<?, ?> stripedExecutor,
                                  Configuration configuration,
                                  NeighborManager neighborManager,
                                  MessageQ messageQ
    ) {
        super(duration);
        this.stripedExecutor = stripedExecutor;
        this.configuration = configuration;
        this.neighborManager = neighborManager;
        this.messageQ = messageQ;
    }

    @Override
    public void task() throws Exception {
        try {
            log.info("Checking Neighbors' Ip...");
            neighborManager.forEach(neighbor -> {
                // this could also potentially block
                String hostName = StringUtils.trimToNull(neighbor.getAddress().getHostName());
                if (hostName != null) {
                    resolveNameToIPAddress(hostName, neighbor);
                }
            });
        } catch (final Exception e) {
            log.error("Neighbor DNS Refresher Thread Exception:", e);
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
        }
    }


    private void resolveNameToIPAddress(final String hostName, final Neighbor neighbor) {
        Future<?> stale = activeDNSRequests.remove(hostName);
        if (stale != null) stale.cancel(true); // not much else we can do ...

        activeDNSRequests.put(hostName, stripedExecutor.submitStripe(stripeManager.stripe(), () -> {
            final String hostAddress;
            try {
                hostAddress = InetAddress.getByName(hostName).getHostAddress();
            } catch (UnknownHostException e) {
                log.warn("DNS lookup produced error: " + e.getMessage());
                return;
            }
            if (hostAddress == null || hostAddress.equals(hostName)) {
                return;
            }
            log.info("DNS Checker: Validating DNS Address '{}' with '{}'", hostName, hostAddress);
            messageQ.publish("dnscv %s %s", hostName, hostAddress);

            String address = neighborIpCache.computeIfAbsent(hostName, key -> hostAddress);
            if (address.equals(hostAddress)) {
                log.info("{} seems fine.", hostName);
                messageQ.publish("dnscc %s", hostName);

            } else if (configuration.booling(Configuration.DefaultConfSettings.DNS_REFRESHER_ENABLED)) {
                log.info("IP CHANGED for {}! Updating...", hostName);
                messageQ.publish("dnscu %s", hostName);
                removeAndReplaceNeighbor(neighbor, hostName, hostAddress);

            } else {
                log.info("IP CHANGED for {}! Skipping... DNS_REFRESHER_ENABLED is false.", hostName);
            }
        }));
    }


    private void makeNewNeighbor(Neighbor neighbor, String hostname, String ip, URI ipAddressBasedURI) {
        Neighbor newNeighbor = neighborManager.newNeighbor(ipAddressBasedURI, neighbor.isConfigured());
        neighborManager.add(newNeighbor);
        neighborIpCache.put(hostname, ip);
    }

    private void removeAndReplaceNeighbor(Neighbor neighbor, String hostName, String hostAddress) {
        String protocol = (neighbor instanceof TCPNeighbor) ? "tcp://" : "udp://";

        String port = ":" + neighbor.getAddress().getPort();

        NeighborManager.uri(protocol + hostName + port).ifPresent(hostnameBasedURI -> {
            neighborManager.removeNeighbor(hostnameBasedURI, neighbor.isConfigured());

            NeighborManager.uri(protocol + hostAddress + port).ifPresent(ipAddressBasedURI -> {
                makeNewNeighbor(neighbor, hostName, hostAddress, ipAddressBasedURI);
            });
        });
    }

    @Override
    public void shutdown() {
        activeDNSRequests.forEach((key, value) -> value.cancel(true));
        super.shutdown();

    }
}
