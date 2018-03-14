package com.iota.iri.scheduledReports;

import com.iota.iri.network.NeighborManager;
import com.iota.iri.network.TCPNeighbor;
import com.iota.iri.network.TCPSink;
import com.iota.iri.utils.ScheduledTask;
import com.iota.iri.utils.textutils.Format;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class TCPNeighborReport extends ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(TCPNeighborReport.class);
    private final NeighborManager neighborManager;

    public TCPNeighborReport(Duration duration, NeighborManager neighborManager) {
        super(duration);
        this.neighborManager = neighborManager;
    }

    public void task() {

        long[] count = {0L};
        long[] totalTx = {0L};
        long[] totalBytes = {0L};
        Duration[] totalDuration = {Duration.ZERO};

        neighborManager.forEach(TCPNeighbor.class, n -> {
            TCPSink.Stats stats = n.getSinkStats();

            if (stats != null) {
                long sent = stats.getPacketsSent();
                long sentFromQueued = stats.getPacketsSentFromQueue();
                count[0]++;
                totalTx[0] += sent;
                totalDuration[0] = totalDuration[0].plus(stats.uptime());
                totalBytes[0] += stats.getPacketsBytesSent();
                // for each TCP neighbor

                Duration uptime = stats.uptime();
                long hours = uptime.toHours();
                uptime = uptime.minusHours(hours);
                long minutes = uptime.toMinutes();
                uptime = uptime.minusMinutes(minutes);
                long seconds = uptime.getSeconds();

                log.info("{}  {} tps {}  {}=" +
                                "{}[sent direct {}  queued {}] " +
                                "{}=[drops busy {}  rate {}  queue {}  nopermit {}  err {}] " +
                                "retried {}",

                        Format.leftpad(n.getHostAddress() + ":" + n.getPort(), 21),
                        Format.leftpad(stats.tps(), 3),
                        Format.leftpad(String.format("%sh:%sm:%ss", hours, minutes, seconds), 12),
                        Format.leftpad(Format.readableNumber(sent), 5),
                        Format.leftpad(Format.readableBytes(stats.getPacketsBytesSent(), false), 5),
                        Format.leftpad(Format.readableNumber(sent - sentFromQueued), 4),
                        Format.leftpad(Format.readableNumber(sentFromQueued), 4),

                        Format.leftpad(Format.readableNumber(stats.getPacketsDropped()), 4),
                        Format.leftpad(Format.readableNumber(stats.getPacketsDroppedBusy()), 4),
                        Format.leftpad(Format.readableNumber(stats.getPacketsDroppedTPSLimited()), 4),
                        Format.leftpad(Format.readableNumber(stats.getPacketsQueued() - stats.getPacketsSentFromQueue()), 4),
                        Format.leftpad(Format.readableNumber(stats.getPacketsDroppedNoPermit()), 4),
                        Format.leftpad(Format.readableNumber(stats.getPermanentErrorsThrown()), 3),

                        Format.leftpad(Format.readableNumber(stats.getPacketsRetried()), 4));
            }
        });

        if (count[0] != 0) {
            log.info("---- {} --- {}/s   sent {}  {}",
                    Format.leftpad("Neighbors " + count[0], 21),
                    Format.leftpad(Format.readableNumber(totalBytes[0] / totalDuration[0].getSeconds()), 5),
                    Format.leftpad(Format.readableNumber(totalTx[0]), 5),
                    Format.leftpad(Format.readableBytes(totalBytes[0], false), 5));
        }
    }
}