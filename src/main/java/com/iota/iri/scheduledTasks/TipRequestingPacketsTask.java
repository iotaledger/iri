package com.iota.iri.scheduledTasks;

import com.iota.iri.Milestone;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.NeighborManager;
import com.iota.iri.network.Node;
import com.iota.iri.network.TCPNeighbor;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.exec.StripedExecutor;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class TipRequestingPacketsTask extends ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(TipRequestingPacketsTask.class);

    private final Tangle tangle;
    private final Milestone milestone;
    private final NeighborManager neighborManager;
    private final StripedExecutor stripedExecutor;

    public TipRequestingPacketsTask(Duration duration, Tangle tangle, Milestone milestone, NeighborManager neighborManager, StripedExecutor stripedExecutor) {
        super(duration);
        this.tangle = tangle;
        this.milestone = milestone;
        this.neighborManager = neighborManager;
        this.stripedExecutor = stripedExecutor;
    }

    @Override
    public void task() throws Exception {
        byte[] packet = new byte[Node.TRANSACTION_PACKET_SIZE];

        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, milestone.getLatestMilestone());

        System.arraycopy(transactionViewModel.getBytes(), 0, packet, 0, TransactionViewModel.SIZE);

        System.arraycopy(transactionViewModel.getHash().bytes(), 0, packet, TransactionViewModel.SIZE,
                TransactionRequester.REQUEST_HASH_SIZE);

        neighborManager.forEach(n -> {
            if (n instanceof TCPNeighbor) {
                stripedExecutor.submitStripe("TipRequestingPacketsTaskTCP", () -> n.send(packet));
            } else {
                stripedExecutor.submitStripe("TipRequestingPacketsTaskUDP", () -> n.send(packet));
            }
        });
    }
}
