package com.iota.iri.scheduledReports;

import com.iota.iri.network.NeighborManager;
import com.iota.iri.network.TCPNeighbor;
import com.iota.iri.network.TCPSink;
import com.iota.iri.network.TCPSource;
import com.iota.iri.utils.ScheduledTask;
import com.iota.iri.utils.textutils.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class TCPSinkReport extends ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(TCPSinkReport.class);
    private final NeighborManager neighborManager;

    public TCPSinkReport(Duration duration, NeighborManager neighborManager) {
        super(duration);
        this.neighborManager = neighborManager;
    }

    public void task() {

        long[] count = {0L};
        long[] totalTx = {0L};
        long[] totalBytes = {0L};
        Duration[] totalDuration = {Duration.ZERO};

        neighborManager.forEach(TCPNeighbor.class, n -> {
            TCPSource.Stats stats = n.getSourceStats();

            if (stats != null) {
                count[0]++;
                totalTx[0] += stats.getPacketsRecieved();
                totalDuration[0] = totalDuration[0].plus(stats.uptime());
                totalBytes[0] += stats.getPacketsBytesProcessed();
                // for each TCP neighbor

                Duration uptime = stats.uptime();
                long hours = uptime.toHours();
                uptime = uptime.minusHours(hours);
                long minutes = uptime.toMinutes();
                uptime = uptime.minusMinutes(minutes);
                long seconds = uptime.getSeconds();

                log.info("{} [INBOUND] {} tps {}  {} received {} processed {} bytes{}  badCRC32 {}",
                        Format.leftpad(n.getHostAddress() + ":" + n.getPort(), 21),
                        Format.leftpad(stats.tps(), 3),
                        Format.leftpad(String.format("%sh:%sm:%ss", hours, minutes, seconds), 12),
                        Format.leftpad(Format.readableNumber(stats.getPacketsRecieved()), 5),
                        Format.leftpad(Format.readableNumber(stats.getPacketsProcessed()), 5),
                        Format.leftpad(Format.readableBytes(stats.getPacketsBytesProcessed(), false), 5),
                        Format.leftpad(Format.readableNumber(stats.getPacketsCRCBAD()), 4));
            }
        });

        if (count[0] != 0) {
            log.info("---- {} --- {} tps   received {} tx {} bytes",
                    Format.leftpad("Neighbors " + count[0], 21),
                    Format.leftpad(Format.readableNumber(totalTx[0] / totalDuration[0].getSeconds()), 5),
                    Format.leftpad(Format.readableNumber(totalTx[0]), 5),
                    Format.leftpad(Format.readableBytes(totalBytes[0], false), 5));
        }
    }
}