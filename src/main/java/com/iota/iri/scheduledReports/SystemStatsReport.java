package com.iota.iri.scheduledReports;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.exec.StripedExecutor;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.ScheduledTask;
import com.iota.iri.utils.textutils.Format;
import com.iota.iri.zmq.MessageQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class SystemStatsReport extends ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(SystemStatsReport.class);

    private final StripedExecutor stripedExecutor;
    private final MessageQ messageQ;
    private final Node node;
    private final Tangle tangle;
    private final TransactionRequester transactionRequester;

    private String lastMsg = "";

    public SystemStatsReport(Duration duration, StripedExecutor stripedExecutor, MessageQ messageQ, Node node, Tangle tangle, TransactionRequester transactionRequester) {
        super(duration);
        this.stripedExecutor = stripedExecutor;
        this.messageQ = messageQ;
        this.node = node;
        this.tangle = tangle;
        this.transactionRequester = transactionRequester;
    }

    @Override
    public void task() throws Exception {
        long ejc = stripedExecutor.getJobsQueuedCount();

        int toProcessSum = node.getReceiveQueueSize();
        int toBroadcastSum = node.getBroadcastQueueSize();
        int toRequestSum = transactionRequester.numberOfTransactionsToRequest();
        int toReplySum = node.getReplyQueueSize();
        int totalTXSum = TransactionViewModel.getNumberOfStoredTransactions(tangle);

        messageQ.publish("rstat %d %d %d %d %d", toProcessSum, toBroadcastSum, toRequestSum, toReplySum, totalTXSum);

        if ((toProcessSum + toBroadcastSum + toRequestSum + toReplySum + totalTXSum) != 0) {

            //pass - not necessary to spam the log with zero results
            // filter - not spam the log with same message

            String msg = String.format("Queued= %s , toProcess= %s , toBroadcast= %s , toRequest= %s , toReply= %s / totalStoredTx= %s",
                    Format.leftpad(ejc, 4),
                    Format.leftpad(toProcessSum, 4),
                    Format.leftpad(toBroadcastSum, 4),
                    Format.leftpad(toRequestSum, 4),
                    Format.leftpad(toReplySum, 4),
                    Format.leftpad(totalTXSum, 4));

            if (!lastMsg.equals(msg)) {
                lastMsg = msg;
                log.info(msg);
            }
        }
    }
}
