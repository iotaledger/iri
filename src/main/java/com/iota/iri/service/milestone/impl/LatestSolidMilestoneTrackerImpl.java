package com.iota.iri.service.milestone.impl;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.milestone.MilestoneException;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.LatestSolidMilestoneTracker;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Creates a manager that keeps track of the latest solid milestones and that triggers the application of these
 * milestones and their corresponding balance changes to the latest {@link Snapshot} by incorporating a background
 * worker that periodically checks for new solid milestones.
 * </p>
 * <p>
 * It extends this with a mechanisms to recover from database corruptions by using a backoff strategy that reverts the
 * changes introduced by previous milestones whenever an error is detected until the problem causing milestone was
 * found.
 * </p>
 */
public class LatestSolidMilestoneTrackerImpl implements LatestSolidMilestoneTracker {
    /**
     * Holds the interval (in milliseconds) in which the {@link #trackLatestSolidMilestone()} method gets
     * called by the background worker.
     */
    private static final int RESCAN_INTERVAL = 5000;

    /**
     * Holds the logger of this class (a rate limited logger than doesn't spam the CLI output).
     */
    private static final IntervalLogger log = new IntervalLogger(LatestSolidMilestoneTrackerImpl.class);

    /**
     * Holds the Tangle object which acts as a database interface.
     */
    private Tangle tangle;

    /**
     * The snapshot provider which gives us access to the relevant snapshots that the node uses (for the ledger
     * state).
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the service instance containing the business logic of the milestone package.
     */
    private MilestoneService milestoneService;

    /**
     * Holds a reference to the manager that keeps track of the latest milestone.
     */
    private LatestMilestoneTracker latestMilestoneTracker;

    /**
     * Holds a reference to the service that contains the logic for applying milestones to the ledger state.
     */
    private LedgerService ledgerService;

    /**
     * Holds a reference to the manager of the background worker.
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Latest Solid Milestone Tracker", log.delegate());

    /**
     * Boolean flag that is used to identify the first iteration of the background worker.
     */
    private boolean firstRun = true;

    /**
     * Holds the milestone index of the milestone that caused the repair logic to get started.
     */
    private int errorCausingMilestoneIndex = Integer.MAX_VALUE;

     /**
     * Counter for the backoff repair strategy (see {@link #repairCorruptedMilestone(MilestoneViewModel)}.
     */
    private int repairBackoffCounter = 0;

    /**
     * Uses to clear the request queue once we are fully synced.
     */
    private TransactionRequester transactionRequester;

    /**
     * <p>
     * This method initializes the instance and registers its dependencies.
     * </p>
     * <p>
     * It stores the passed in values in their corresponding private properties.
     * </p>
     * <p>
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:
     * </p>
     *       {@code latestSolidMilestoneTracker = new LatestSolidMilestoneTrackerImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider manager for the snapshots that allows us to retrieve the relevant snapshots of this node
     * @param milestoneService contains the important business logic when dealing with milestones
     * @param ledgerService the manager for
     * @param latestMilestoneTracker the manager that keeps track of the latest milestone
     * @param transactionRequester the manager which keeps and tracks transactions which are requested
     * @return the initialized instance itself to allow chaining
     */
    public LatestSolidMilestoneTrackerImpl init(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneService milestoneService, LedgerService ledgerService,
            LatestMilestoneTracker latestMilestoneTracker, TransactionRequester transactionRequester) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.milestoneService = milestoneService;
        this.ledgerService = ledgerService;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.transactionRequester = transactionRequester;

        return this;
    }

    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::latestSolidMilestoneTrackerThread, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * In addition to applying the found milestones to the ledger state it also issues log messages and keeps the
     * {@link LatestMilestoneTracker} in sync (if we happen to process a new latest milestone faster).
     * </p>
     */
    @Override
    public void trackLatestSolidMilestone() throws MilestoneException {
        try {
            int currentSolidMilestoneIndex = snapshotProvider.getLatestSnapshot().getIndex();
            if (currentSolidMilestoneIndex < latestMilestoneTracker.getLatestMilestoneIndex()) {
                MilestoneViewModel nextMilestone;
                while (!Thread.currentThread().isInterrupted() &&
                        (nextMilestone = MilestoneViewModel.get(tangle, currentSolidMilestoneIndex + 1)) != null &&
                        TransactionViewModel.fromHash(tangle, nextMilestone.getHash()).isSolid()) {

                    syncLatestMilestoneTracker(nextMilestone.getHash(), nextMilestone.index());
                    applySolidMilestoneToLedger(nextMilestone);
                    logChange(currentSolidMilestoneIndex);

                    currentSolidMilestoneIndex = snapshotProvider.getLatestSnapshot().getIndex();
                    if(currentSolidMilestoneIndex == latestMilestoneTracker.getLatestMilestoneIndex()){
                        transactionRequester.clearRecentlyRequestedTransactions();
                    }
                }
            } else {
                syncLatestMilestoneTracker(snapshotProvider.getLatestSnapshot().getHash(),
                        currentSolidMilestoneIndex);
            }
        } catch (Exception e) {
            throw new MilestoneException("unexpected error while checking for new latest solid milestones", e);
        }
    }

    /**
     * <p>
     * Contains the logic for the background worker.
     * </p>
     * <p>
     * It simply calls {@link #trackLatestSolidMilestone()} and wraps with a log handler that prevents the {@link
     * MilestoneException} to crash the worker.
     * </p>
     */
    private void latestSolidMilestoneTrackerThread() {
        try {
            if (firstRun) {
                firstRun = false;

                ledgerService.restoreLedgerState();
                logChange(snapshotProvider.getInitialSnapshot().getIndex());
            }

            trackLatestSolidMilestone();
        } catch (Exception e) {
            log.error("error while updating the solid milestone", e);
        }
    }

    /**
     * <p>
     * Applies the given milestone to the ledger.
     * </p>
     * <p>
     * If the application of the milestone fails, we start a repair routine which will revert the milestones preceding
     * our current milestone and consequently try to reapply them in the next iteration of the {@link
     * #trackLatestSolidMilestone()} method (until the problem is solved).
     * </p>
     *
     * @param milestone the milestone that shall be applied to the ledger state
     * @throws Exception if anything unexpected goes wrong while applying the milestone to the ledger
     */
    private void applySolidMilestoneToLedger(MilestoneViewModel milestone) throws Exception {
        if (ledgerService.applyMilestoneToLedger(milestone)) {
            if (isRepairRunning() && isRepairSuccessful(milestone)) {
                stopRepair();
            }
        } else {
            repairCorruptedMilestone(milestone);
        }
    }

    /**
     * <p>
     * Checks if we are currently trying to repair a milestone.
     * </p>
     * <p>
     * We simply use the {@link #repairBackoffCounter} as an indicator if a repair routine is running.
     * </p>
     *
     * @return {@code true} if we are trying to repair a milestone and {@code false} otherwise
     */
    private boolean isRepairRunning() {
        return repairBackoffCounter != 0;
    }

    /**
     * <p>
     * Checks if we successfully repaired the corrupted milestone.
     * </p>
     * <p>
     * To determine if the repair routine was successful we check if the processed milestone has a higher index than the
     * one that initially could not get applied to the ledger.
     * </p>
     *
     * @param processedMilestone the currently processed milestone
     * @return {@code true} if we advanced to a milestone following the corrupted one and {@code false} otherwise
     */
    private boolean isRepairSuccessful(MilestoneViewModel processedMilestone) {
        return processedMilestone.index() > errorCausingMilestoneIndex;
    }

    /**
     * <p>
     * Resets the internal variables that are used to keep track of the repair process.
     * </p>
     * <p>
     * It gets called whenever we advance to a milestone that has a higher milestone index than the milestone that
     * initially caused the repair routine to kick in (see {@link #repairCorruptedMilestone(MilestoneViewModel)}.
     * </p>
     */
    private void stopRepair() {
        repairBackoffCounter = 0;
        errorCausingMilestoneIndex = Integer.MAX_VALUE;
    }

    /**
     * <p>
     * Keeps the {@link LatestMilestoneTracker} in sync with our current progress.
     * </p>
     * <p>
     * Since the {@link LatestMilestoneTracker} scans all old milestones during its startup (which can take a while to
     * finish) it can happen that we see a newer latest milestone faster than this manager.
     * </p>
     * Note: This method ensures that the latest milestone index is always bigger or equals the latest solid milestone
     *       index.
     *
     * @param milestoneHash transaction hash of the milestone
     * @param milestoneIndex milestone index
     */
    private void syncLatestMilestoneTracker(Hash milestoneHash, int milestoneIndex) {
        if(milestoneIndex > latestMilestoneTracker.getLatestMilestoneIndex()) {
            latestMilestoneTracker.setLatestMilestone(milestoneHash, milestoneIndex);
        }
    }

    /**
     * <p>
     * Emits a log message whenever the latest solid milestone changes.
     * </p>
     * <p>
     * It simply compares the current latest milestone index against the previous milestone index and emits the log
     * messages using the {@link #log} and the {@link #messageQ} instances if it differs.
     * </p>
     *
     * @param prevSolidMilestoneIndex the milestone index before the change
     */
    private void logChange(int prevSolidMilestoneIndex) {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();
        int latestMilestoneIndex = latestSnapshot.getIndex();
        Hash latestMilestoneHash = latestSnapshot.getHash();

        if (prevSolidMilestoneIndex != latestMilestoneIndex) {
            log.info("Latest SOLID milestone index changed from #" + prevSolidMilestoneIndex + " to #" + latestMilestoneIndex);

            tangle.publish("lmsi %d %d", prevSolidMilestoneIndex, latestMilestoneIndex);
            tangle.publish("lmhs %s", latestMilestoneHash);
        }
    }

    /**
     * <p>
     * Tries to actively repair the ledger by reverting the milestones preceding the given milestone.
     * </p>
     * <p>
     * It gets called when a milestone could not be applied to the ledger state because of problems like "inconsistent
     * balances". While this should theoretically never happen (because milestones are by definition "consistent"), it
     * can still happen because IRI crashed or got stopped in the middle of applying a milestone or if a milestone
     * was processed in the wrong order.
     * </p>
     * <p>
     * Every time we call this method the internal {@link #repairBackoffCounter} is incremented which causes the next
     * call of this method to repair an additional milestone. This means that whenever we face an error we first try to
     * reset only the last milestone, then the two last milestones, then the three last milestones (and so on ...) until
     * the problem was fixed.
     * </p>
     * <p>
     * To be able to tell when the problem is fixed and the {@link #repairBackoffCounter} can be reset, we store the
     * milestone index that caused the problem the first time we call this method.
     * </p>
     *
     * @param errorCausingMilestone the milestone that failed to be applied
     * @throws MilestoneException if we failed to reset the corrupted milestone
     */
    private void repairCorruptedMilestone(MilestoneViewModel errorCausingMilestone) throws MilestoneException {
        if(repairBackoffCounter++ == 0) {
            errorCausingMilestoneIndex = errorCausingMilestone.index();
        }
        for (int i = errorCausingMilestone.index(); i > errorCausingMilestone.index() - repairBackoffCounter; i--) {
            milestoneService.resetCorruptedMilestone(i);
        }
    }
}
