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
 * Creates a manager that proactively requests the missing "seen milestones" (defined in the local snapshot file).<br />
 * <br />
 * It simply stores the passed in dependencies in their corresponding properties and then makes a copy of the {@code
 * seenMilestones} of the initial snapshot which will consequently be requested.<br />
 * <br />
 * Once the manager finishes to request all "seen milestones" it will automatically {@link #shutdown()} (when being
 * {@link #start()}ed before).<br />
 */
public class SeenMilestonesRetrieverImpl implements SeenMilestonesRetriever {
    /**
     * Defines how far ahead of the latest solid milestone we are requesting the missing milestones.<br />
     */
    private static final int RETRIEVE_RANGE = 50;

    /**
     * Defines the interval (in milliseconds) in which the background worker will check for new milestones to
     * request.<br />
     */
    private static final int RESCAN_INTERVAL = 1000;

    /**
     * Holds the logger of this class (a rate limited logger than doesn't spam the CLI output).<br />
     */
    private static final IntervalLogger log = new IntervalLogger(SeenMilestonesRetrieverImpl.class);

    /**
     * Tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * The snapshot provider which gives us access to the relevant snapshots to calculate our range.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the {@link TransactionRequester} that allows us to issue requests for the missing
     * milestones.<br />
     */
    private TransactionRequester transactionRequester;

    /**
     * Holds a reference to the manager of the background worker.<br />
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Seen Milestones Retriever", log.delegate());

    /**
     * The list of seen milestones that need to be requested.<br />
     */
    private Map<Hash, Integer> seenMilestones;

    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties and creates a working copy of the
     * seen milestones (which will get processed by the background worker).<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code seenMilestonesRetriever = new SeenMilestonesRetrieverImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots to calculate our range
     * @param transactionRequester allows us to issue requests for the missing milestones
     * @return the initialized instance itself to allow chaining
     */
    public SeenMilestonesRetrieverImpl init(Tangle tangle, SnapshotProvider snapshotProvider,
            TransactionRequester transactionRequester) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionRequester = transactionRequester;

        seenMilestones = new ConcurrentHashMap<>(snapshotProvider.getInitialSnapshot().getSeenMilestones());

        return this;
    }

    /**
     * {@inheritDoc}
     * <br />
     * It simply iterates over the set of seenMilestones and requests them if they are in the range of
     * [genesisMilestone ... latestSolidMilestone + RETRIEVE_RANGE]. Milestones that are older than this range get
     * deleted because they are irrelevant for the ledger state and milestones that are younger than this range get
     * ignored to be processed later.<br />
     * <br />
     * This gives the node enough resources to solidify the next milestones without getting its requests queue filled
     * with milestone requests that will become relevant only much later (this achieves a linear sync speed).<br />
     * <br />
     * Note: If no more seen milestones have to be requested, this manager shuts down automatically.<br />
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
                            !transactionRequester.isTransactionRequested(milestoneHash, true)) {

                        transactionRequester.requestTransaction(milestoneHash, true);
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
