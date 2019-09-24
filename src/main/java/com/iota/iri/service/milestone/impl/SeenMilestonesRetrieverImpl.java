package com.iota.iri.service.milestone.impl;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.milestone.SeenMilestonesRetriever;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Creates a manager that proactively requests the missing "seen milestones" (defined in the local snapshot file).
 * </p>
 * <p>
 * It stores the passed in dependencies in their corresponding properties and then makes a copy of the {@code
 * seenMilestones} of the initial snapshot which will consequently be requested.
 * </p>
 * <p>
 * Once the manager finishes to request all "seen milestones" it will automatically {@link #shutdown()} (when being
 * {@link #start()}ed before).
 * </p>
 */
public class SeenMilestonesRetrieverImpl implements SeenMilestonesRetriever {
    /**
     * Defines how far ahead of the latest solid milestone we are requesting the missing milestones.
     */
    private static final int RETRIEVE_RANGE = 50;

    /**
     * Defines the interval (in milliseconds) in which the background worker will check for new milestones to
     * request.
     */
    private static final int RESCAN_INTERVAL = 1000;

    /**
     * Holds the logger of this class (a rate limited logger than doesn't spam the CLI output).
     */
    private static final IntervalLogger log = new IntervalLogger(SeenMilestonesRetrieverImpl.class);

    /**
     * Tangle object which acts as a database interface.
     */
    private final Tangle tangle;

    /**
     * The snapshot provider which gives us access to the relevant snapshots to calculate our range.
     */
    private final SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the {@link TransactionRequester} that allows us to issue requests for the missing
     * milestones.
     */
    private final TransactionRequester transactionRequester;

    /**
     * Holds a reference to the manager of the background worker.
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Seen Milestones Retriever", log.delegate());

    /**
     * The list of seen milestones that need to be requested.
     */
    private Map<Hash, Integer> seenMilestones;

    /**
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots to calculate our range
     * @param transactionRequester allows us to issue requests for the missing milestones
     */
    public SeenMilestonesRetrieverImpl(Tangle tangle, SnapshotProvider snapshotProvider,
                                            TransactionRequester transactionRequester) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionRequester = transactionRequester;
    }

    @Override
    public void init() {
        seenMilestones = new ConcurrentHashMap<>(snapshotProvider.getInitialSnapshot().getSeenMilestones());
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * It simply iterates over the set of seenMilestones and requests them if they are in the range of
     * [genesisMilestone ... latestSolidMilestone + RETRIEVE_RANGE]. Milestones that are older than this range get
     * deleted because they are irrelevant for the ledger state and milestones that are younger than this range get
     * ignored to be processed later.
     * </p>
     * <p>
     * This gives the node enough resources to solidify the next milestones without getting its requests queue filled
     * with milestone requests that will become relevant only much later (this achieves a linear sync speed).
     * </p>
     * <p>
     * Note: If no more seen milestones have to be requested, this manager shuts down automatically.
     * </p>
     */
    @Override
    public void retrieveSeenMilestones() {
        seenMilestones.forEach((milestoneHash, milestoneIndex) -> {
            try {
                if (milestoneIndex <= snapshotProvider.getInitialSnapshot().getIndex()) {
                    seenMilestones.remove(milestoneHash);
                } else if (milestoneIndex < snapshotProvider.getLatestSnapshot().getIndex() + RETRIEVE_RANGE) {
                    TransactionViewModel milestoneTransaction = TransactionViewModel.fromHash(tangle, milestoneHash);
                    if (milestoneTransaction.getType() == TransactionViewModel.PREFILLED_SLOT &&
                            !transactionRequester.isTransactionRequested(milestoneHash)) {

                        transactionRequester.requestTransaction(milestoneHash);
                    }

                    // the transactionRequester will never drop milestone requests - we can therefore remove it from the
                    // list of milestones to request
                    seenMilestones.remove(milestoneHash);
                }

                log.info("Requesting seen milestones (" + seenMilestones.size() + " left) ...");
            } catch (Exception e) {
                log.error("unexpected error while processing the seen milestones", e);
            }
        });

        if (seenMilestones.isEmpty()) {
            log.info("Requesting seen milestones ... [DONE]");

            shutdown();
        }
    }

    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::retrieveSeenMilestones, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }
}
