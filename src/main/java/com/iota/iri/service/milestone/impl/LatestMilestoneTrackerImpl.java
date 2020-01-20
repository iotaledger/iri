package com.iota.iri.service.milestone.impl;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.milestone.MilestoneException;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Creates a tracker that automatically detects new milestones by incorporating a background worker that periodically
 * checks all transactions that are originating from the coordinator address and that exposes the found latest milestone
 * via getters.
 * <p>
 * </p>
 * It can be used to determine the sync-status of the node by comparing these values against the latest solid
 * milestone.
 * </p>
 */
public class LatestMilestoneTrackerImpl implements LatestMilestoneTracker {
    /**
     * Holds the amount of milestone candidates that will be analyzed per iteration of the background worker.
     */
    private static final int MAX_CANDIDATES_TO_ANALYZE = 5000;

    /**
     * Holds the time (in milliseconds) between iterations of the background worker.
     */
    private static final int RESCAN_INTERVAL = 1000;

    /**
     * Holds the logger of this class (a rate limited logger than doesn't spam the CLI output).
     */
    private static final IntervalLogger log = new IntervalLogger(LatestMilestoneTrackerImpl.class);

    /**
     * Holds the Tangle object which acts as a database interface.
     */
    private final Tangle tangle;

    /**
     * The snapshot provider which gives us access to the relevant snapshots that the node uses (for faster
     * bootstrapping).
     */
    private final SnapshotProvider snapshotProvider;

    /**
     * Service class containing the business logic of the milestone package.
     */
    private final MilestoneService milestoneService;

    /**
     * Holds a reference to the manager that takes care of solidifying milestones.
     */
    private final MilestoneSolidifier milestoneSolidifier;

    /**
     * Holds the coordinator address which is used to filter possible milestone candidates.
     */
    private final Hash coordinatorAddress;

    /**
     * Holds a reference to the manager of the background worker.
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Latest Milestone Tracker", log.delegate());

    /**
     * Holds the milestone index of the latest milestone that we have seen / processed.
     */
    private int latestMilestoneIndex;

    /**
     * Holds the transaction hash of the latest milestone that we have seen / processed.
     */
    private Hash latestMilestoneHash;

    /**
     * A set that allows us to keep track of the candidates that have been seen and added to the {@link
     * #milestoneCandidatesToAnalyze} already.
     */
    private final Set<TransactionViewModel> seenMilestoneCandidates = new HashSet<>();

    /**
     * A list of milestones that still have to be analyzed.
     */
    private final Deque<TransactionViewModel> milestoneCandidatesToAnalyze = new ArrayDeque<>();

    /**
     * A flag that allows us to detect if the background worker is in its first iteration (for different log
     * handling).
     */
    private boolean firstRun = true;

    /**
     * Flag which indicates if this tracker has finished its initial scan of all old milestone candidates.
     */
    private boolean initialized = false;

    /**
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider manager for the snapshots that allows us to retrieve the relevant snapshots of this node
     * @param milestoneService contains the important business logic when dealing with milestones
     * @param milestoneSolidifier manager that takes care of solidifying milestones
     * @param config configuration object which allows us to determine the important config parameters of the node
     */
    public LatestMilestoneTrackerImpl(Tangle tangle, SnapshotProvider snapshotProvider, MilestoneService milestoneService,
                                      MilestoneSolidifier milestoneSolidifier, IotaConfig config) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.milestoneService = milestoneService;
        this.milestoneSolidifier = milestoneSolidifier;
        this.coordinatorAddress = config.getCoordinator();
    }

    @Override
    public void init() {
        bootstrapLatestMilestoneValue();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * In addition to setting the internal properties, we also issue a log message and publish the change to the ZeroMQ
     * message processor so external receivers get informed about this change.
     * </p>
     */
    @Override
    public void setLatestMilestone(Hash latestMilestoneHash, int latestMilestoneIndex) {
        tangle.publish("lmi %d %d", this.latestMilestoneIndex, latestMilestoneIndex);
        log.delegate().info("Latest milestone has changed from #" + this.latestMilestoneIndex + " to #" +
                latestMilestoneIndex);

        this.latestMilestoneHash = latestMilestoneHash;
        this.latestMilestoneIndex = latestMilestoneIndex;
    }

    @Override
    public int getLatestMilestoneIndex() {
        return latestMilestoneIndex;
    }

    @Override
    public Hash getLatestMilestoneHash() {
        return latestMilestoneHash;
    }

    @Override
    public boolean processMilestoneCandidate(Hash transactionHash) throws MilestoneException {
        try {
            return processMilestoneCandidate(TransactionViewModel.fromHash(tangle, transactionHash));
        } catch (Exception e) {
            throw new MilestoneException("unexpected error while analyzing the transaction " + transactionHash, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean processMilestoneCandidate(TransactionViewModel transaction) throws MilestoneException {
        try {
            if (coordinatorAddress.equals(transaction.getAddressHash()) &&
                    transaction.getCurrentIndex() == 0) {

                int milestoneIndex = milestoneService.getMilestoneIndex(transaction);

                // if the milestone is older than our ledger start point: we already processed it in the past
                if (milestoneIndex <= snapshotProvider.getInitialSnapshot().getIndex()) {
                    return true;
                }

                switch (milestoneService.validateMilestone(transaction, milestoneIndex)) {
                    case VALID:
                        if (milestoneIndex > latestMilestoneIndex) {
                            setLatestMilestone(transaction.getHash(), milestoneIndex);
                        }

                        if (!transaction.isSolid()) {
                            milestoneSolidifier.add(transaction.getHash(), milestoneIndex);
                        }

                        transaction.isMilestone(tangle, snapshotProvider.getInitialSnapshot(), true);
                    break;

                    case INCOMPLETE:
                        return false;

                    case INVALID:
                        // do not re-analyze anymore
                        return true;

                    default:
                        // we can consider the milestone candidate processed and move on w/o farther action
                }
            }

            return true;
        } catch (Exception e) {
            throw new MilestoneException("unexpected error while analyzing the " + transaction, e);
        }
    }

    @Override
    public boolean isInitialScanComplete() {
        return initialized;
    }

    /**
     * {@inheritDoc}
     * <p>
     * We repeatedly call {@link #latestMilestoneTrackerThread()} to actively look for new milestones in our database.
     * This is a bit inefficient and should at some point maybe be replaced with a check on transaction arrival, but
     * this would required adjustments in the whole way IRI handles transactions and is therefore postponed for
     * now.
     * </p>
     */
    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::latestMilestoneTrackerThread, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * <p>
     * This method contains the logic for scanning for new latest milestones that gets executed in a background
     * worker.
     * </p>
     * <p>
     * It first collects all new milestone candidates that need to be analyzed, then analyzes them and finally checks if
     * the initialization is complete. In addition to this scanning logic it also issues regular log messages about the
     * progress of the scanning.
     * </p>
     */
    private void latestMilestoneTrackerThread() {
        try {
            logProgress();
            collectNewMilestoneCandidates();

            // additional log message on the first run to indicate how many milestone candidates we have in total
            if (firstRun) {
                firstRun = false;

                logProgress();
            }

            analyzeMilestoneCandidates();
            checkIfInitializationComplete();
        } catch (MilestoneException e) {
            log.error("error while analyzing the milestone candidates", e);
        }
    }

    /**
     * <p>
     * This method emits a log message about the scanning progress.
     * </p>
     * <p>
     * It only emits a log message if we have more than one {@link #milestoneCandidatesToAnalyze}, which means that the
     * very first call to this method in the "first run" on {@link #latestMilestoneTrackerThread()} will not produce any
     * output (which is the reason why we call this method a second time after we have collected all the
     * candidates in the "first run").
     * </p>
     */
    private void logProgress() {
        if (milestoneCandidatesToAnalyze.size() > 1) {
            log.info("Processing milestone candidates (" + milestoneCandidatesToAnalyze.size() + " remaining) ...");
        }
    }

    /**
     * <p>
     * This method collects the new milestones that have not been "seen" before, by collecting them in the {@link
     * #milestoneCandidatesToAnalyze} queue.
     * </p>
     * <p>
     * We simply request all transaction that are originating from the coordinator address and treat them as potential
     * milestone candidates.
     * </p>
     *
     * @throws MilestoneException if anything unexpected happens while collecting the new milestone candidates
     */
    private void collectNewMilestoneCandidates() throws MilestoneException {
        try {
            List<TransactionViewModel> transactions = AddressViewModel.loadAsSortedList(tangle, coordinatorAddress);
            for (TransactionViewModel tvm : transactions) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                if (tvm != null && seenMilestoneCandidates.add(tvm)) {
                    milestoneCandidatesToAnalyze.addFirst(tvm);
                }
            }
        } catch (Exception e) {
            throw new MilestoneException("failed to collect the new milestone candidates", e);
        }
    }

    /**
     * <p>
     * This method analyzes the milestone candidates by working through the {@link #milestoneCandidatesToAnalyze}
     * queue.
     * </p>
     * <p>
     * We only process {@link #MAX_CANDIDATES_TO_ANALYZE} at a time, to give the caller the option to terminate early
     * and pick up new milestones as fast as possible without being stuck with analyzing the old ones for too
     * long.
     * </p>
     *
     * @throws MilestoneException if anything unexpected happens while analyzing the milestone candidates
     */
    private void analyzeMilestoneCandidates() throws MilestoneException {
        int candidatesToAnalyze = Math.min(milestoneCandidatesToAnalyze.size(), MAX_CANDIDATES_TO_ANALYZE);
        for (int i = 0; i < candidatesToAnalyze; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            TransactionViewModel candidateTransactionViewModel = milestoneCandidatesToAnalyze.pollFirst();
            if(!processMilestoneCandidate(candidateTransactionViewModel)) {
                seenMilestoneCandidates.remove(candidateTransactionViewModel);
            }
        }
    }

    /**
     * <p>
     * This method checks if the initialization is complete.
     * </p>
     * <p>
     * It simply checks if the {@link #initialized} flag is not set yet and there are no more {@link
     * #milestoneCandidatesToAnalyze}. If the initialization was complete, we issue a log message and set the
     * corresponding flag to {@code true}.
     * </p>
     */
    private void checkIfInitializationComplete() {
        if (!initialized && milestoneCandidatesToAnalyze.size() == 0) {
            initialized = true;

            log.info("Processing milestone candidates ... [DONE]").triggerOutput(true);
        }
    }

    /**
     * <p>
     * This method bootstraps this tracker with the latest milestone values that can easily be retrieved without
     * analyzing any transactions (for faster startup).
     * </p>
     * <p>
     * It first sets the latest milestone to the values found in the latest snapshot and then check if there is a younger
     * milestone at the end of our database. While this last entry in the database doesn't necessarily have to be the
     * latest one we know it at least gives a reasonable value most of the times.
     * </p>
     */
    private void bootstrapLatestMilestoneValue() {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();
        setLatestMilestone(latestSnapshot.getHash(), latestSnapshot.getIndex());

        try {
            MilestoneViewModel lastMilestoneInDatabase = MilestoneViewModel.latest(tangle);
            if (lastMilestoneInDatabase != null && lastMilestoneInDatabase.index() > getLatestMilestoneIndex()) {
                setLatestMilestone(lastMilestoneInDatabase.getHash(), lastMilestoneInDatabase.index());
            }
        } catch (Exception e) {
             log.error("unexpectedly failed to retrieve the latest milestone from the database", e);
        }
    }
}
