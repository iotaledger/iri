package com.iota.iri.service.milestone.impl;

import com.iota.iri.TransactionValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * This class implements the basic contract of the {@link MilestoneSolidifier} interface.<br />
 * <br />
 * It manages a map of unsolid milestones to collect all milestones that have to be solidified. It then periodically
 * issues checkSolidity calls on the earliest milestones to solidify them.<br />
 * <br />
 * To save resources and make the call a little bit more efficient, we cache the earliest milestones in a separate map,
 * so the relatively expensive task of having to search for the next earliest milestone in the pool only has to be
 * performed after a milestone has become solid or irrelevant for our node.<br />
 */
public class MilestoneSolidifierImpl implements MilestoneSolidifier {
    /**
     * Defines the amount of milestones that we "simultaneously" try to solidify in one pass.<br />
     */
    private static final int SOLIDIFICATION_QUEUE_SIZE = 10;

    /**
     * Defines the interval in which solidity checks are issued (in milliseconds).<br />
     */
    private static final int SOLIDIFICATION_INTERVAL = 100;

    /**
     * Defines the maximum amount of transactions that are allowed to get processed while trying to solidify a
     * milestone.<br />
     * <br />
     * Note: We want to find the next previous milestone and not get stuck somewhere at the end of the tangle with a
     *       long running {@link TransactionValidator#checkSolidity(Hash, boolean)} call.<br />
     */
    private static final int SOLIDIFICATION_TRANSACTIONS_LIMIT = 50000;

    /**
     * Logger for this class allowing us to dump debug and status messages.<br />
     */
    private static final IntervalLogger log = new IntervalLogger(MilestoneSolidifier.class);

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the TransactionValidator which allows us to issue solidity checks.<br />
     */
    private TransactionValidator transactionValidator;

    /**
     * Holds a reference to the manager of the background worker.<br />
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Milestone Solidifier", log.delegate());

    /**
     * Holds the milestones that were newly added, but not examined yet.<br />
     * <br />
     * Note: This is used to be able to add milestones to the solidifier without having to synchronize the access to the
     *       underlying Maps.<br />
     */
    private final Map<Hash, Integer> newlyAddedMilestones = new ConcurrentHashMap<>();

    /**
     * Holds all unsolid milestones that shall be solidified (the transaction hash mapped to its milestone index).<br />
     */
    private final Map<Hash, Integer> unsolidMilestonesPool = new ConcurrentHashMap<>();

    /**
     * Holds the milestones that are actively trying to be solidified by the background {@link Thread} (acts as a
     * Queue).<br />
     */
    private final Map<Hash, Integer> milestonesToSolidify = new HashMap<>();

    /**
     * Holds and entry that represents the youngest milestone in the {@link #milestonesToSolidify} Map.<br />
     * <br />
     * Note: It is used to check if new milestones that are being added, are older that the currently processed ones and
     *       should replace them in the queue (we solidify from oldest to youngest).<br />
     */
    private Map.Entry<Hash, Integer> youngestMilestoneInQueue = null;

    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code milestoneSolidifier = new MilestoneSolidifierImpl().init(...);}
     *
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots
     * @param transactionValidator TransactionValidator instance that is used by the node
     * @return the initialized instance itself to allow chaining
     */
    public MilestoneSolidifierImpl init(SnapshotProvider snapshotProvider, TransactionValidator transactionValidator) {
        this.snapshotProvider = snapshotProvider;
        this.transactionValidator = transactionValidator;

        return this;
    }

    /**
     * {@inheritDoc}
     * <br />
     * Since this method might be called from a performance critical context, we simply add the milestone to a temporary
     * pool, that gets examined later by the background process. This doesn't just speed up the addition of new jobs but
     * also prevents us from having to synchronize the access to the underlying maps.<br />
     */
    @Override
    public void add(Hash milestoneHash, int milestoneIndex) {
        if (!unsolidMilestonesPool.containsKey(milestoneHash) && !newlyAddedMilestones.containsKey(milestoneHash) &&
                milestoneIndex > snapshotProvider.getInitialSnapshot().getIndex()) {

            newlyAddedMilestones.put(milestoneHash, milestoneIndex);
        }
    }

    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::milestoneSolidificationThread, 0, SOLIDIFICATION_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * This method takes an entry from the {@link #unsolidMilestonesPool} and adds it to the
     * {@link #milestonesToSolidify} queue.<br />
     * <br />
     * It first checks if the given milestone is already part of the queue and then tries to add it. If the queue is not
     * full yet, the addition to the queue is relatively cheap, because the {@link #youngestMilestoneInQueue} marker can
     * be updated without iterating over all entries. If the queue reached its capacity already, we replace the entry
     * marked by the {@link #youngestMilestoneInQueue} marker and update the marker by recalculating it using
     * {@link #determineYoungestMilestoneInQueue()}.<br />
     *
     * @param milestoneEntry entry from the {@link #unsolidMilestonesPool} that shall get added to the queue
     */
    private void addToSolidificationQueue(Map.Entry<Hash, Integer> milestoneEntry) {
        if (milestonesToSolidify.containsKey(milestoneEntry.getKey())) {
            return;
        }

        if (milestonesToSolidify.size() < SOLIDIFICATION_QUEUE_SIZE) {
            milestonesToSolidify.put(milestoneEntry.getKey(), milestoneEntry.getValue());

            if (youngestMilestoneInQueue == null || milestoneEntry.getValue() > youngestMilestoneInQueue.getValue()) {
                youngestMilestoneInQueue = milestoneEntry;
            }
        } else if (milestoneEntry.getValue() < youngestMilestoneInQueue.getValue()) {
            milestonesToSolidify.remove(youngestMilestoneInQueue.getKey());
            milestonesToSolidify.put(milestoneEntry.getKey(), milestoneEntry.getValue());

            determineYoungestMilestoneInQueue();
        }
    }

    /**
     * This method contains the logic for the milestone solidification, that gets executed in a separate
     * {@link Thread}.<br />
     * <br />
     * It simply executes the necessary steps periodically while waiting a short time to give the nodes the ability to
     * answer to the issued transaction requests.<br />
     */
    private void milestoneSolidificationThread() {
        processNewlyAddedMilestones();
        processSolidificationQueue();
        refillSolidificationQueue();
    }

    /**
     * This method processes the newly added milestones.<br />
     * <br />
     * We process them lazy to decrease the synchronization requirements and speed up the addition of milestones from
     * outside {@link Thread}s.<br />
     * <br />
     * It simply iterates over the milestones and adds them to the pool. If they are older than the
     * {@link #youngestMilestoneInQueue}, we add the to the solidification queue.<br />
     */
    private void processNewlyAddedMilestones() {
        for (Iterator<Map.Entry<Hash, Integer>> iterator = newlyAddedMilestones.entrySet().iterator();
                !Thread.currentThread().isInterrupted() && iterator.hasNext();) {

            Map.Entry<Hash, Integer> currentEntry = iterator.next();

            unsolidMilestonesPool.put(currentEntry.getKey(), currentEntry.getValue());

            if (youngestMilestoneInQueue == null || currentEntry.getValue() < youngestMilestoneInQueue.getValue()) {
                addToSolidificationQueue(currentEntry);
            }

            iterator.remove();
        }
    }

    /**
     * This method contains the logic for processing the {@link #milestonesToSolidify}.<br />
     * <br />
     * It iterates through the queue and checks if the corresponding milestones are still relevant for our node, or if
     * they could be successfully solidified. If the milestones become solid or irrelevant, we remove them from the
     * pool and the queue and reset the {@link #youngestMilestoneInQueue} marker (if necessary).<br />
     */
    private void processSolidificationQueue() {
        for (Iterator<Map.Entry<Hash, Integer>> iterator = milestonesToSolidify.entrySet().iterator();
                !Thread.currentThread().isInterrupted() && iterator.hasNext();) {

            Map.Entry<Hash, Integer> currentEntry = iterator.next();

            if (currentEntry.getValue() <= snapshotProvider.getInitialSnapshot().getIndex() || isSolid(currentEntry)) {
                unsolidMilestonesPool.remove(currentEntry.getKey());
                iterator.remove();

                if (youngestMilestoneInQueue != null &&
                        currentEntry.getKey().equals(youngestMilestoneInQueue.getKey())) {

                    youngestMilestoneInQueue = null;
                }
            }
        }
    }

    /**
     * This method takes care of adding new milestones from the pool to the solidification queue, and filling it up
     * again after it was processed / emptied before.<br />
     * <br />
     * It first updates the {@link #youngestMilestoneInQueue} marker and then just adds new milestones as long as there
     * is still space in the {@link #milestonesToSolidify} queue.<br />
     */
    private void refillSolidificationQueue() {
        if(youngestMilestoneInQueue == null && !milestonesToSolidify.isEmpty()) {
            determineYoungestMilestoneInQueue();
        }

        Map.Entry<Hash, Integer> nextSolidificationCandidate;
        while (!Thread.currentThread().isInterrupted() && milestonesToSolidify.size() < SOLIDIFICATION_QUEUE_SIZE &&
                (nextSolidificationCandidate = getNextSolidificationCandidate()) != null) {

            addToSolidificationQueue(nextSolidificationCandidate);
        }
    }

    /**
     * This method determines the youngest milestone in the solidification queue.<br />
     * <br />
     * It iterates over all milestones in the Queue and keeps track of the youngest one found (the one with the highest
     * milestone index).<br />
     */
    private void determineYoungestMilestoneInQueue() {
        youngestMilestoneInQueue = null;
        for (Map.Entry<Hash, Integer> currentEntry : milestonesToSolidify.entrySet()) {
            if (youngestMilestoneInQueue == null || currentEntry.getValue() > youngestMilestoneInQueue.getValue()) {
                youngestMilestoneInQueue = currentEntry;
            }
        }
    }

    /**
     * This method returns the earliest seen Milestone from the unsolid milestones pool, that is not part of the
     * {@link #milestonesToSolidify} queue yet.<br />
     * <br />
     * It simply iterates over all milestones in the pool and looks for the one with the lowest index, that is not
     * getting actively solidified, yet.<br />
     *
     * @return the Map.Entry holding the earliest milestone or null if the pool does not contain any new candidates.
     */
    private Map.Entry<Hash, Integer> getNextSolidificationCandidate() {
        Map.Entry<Hash, Integer> nextSolidificationCandidate = null;
        for (Map.Entry<Hash, Integer> milestoneEntry : unsolidMilestonesPool.entrySet()) {
            if (!milestonesToSolidify.containsKey(milestoneEntry.getKey()) && (nextSolidificationCandidate == null ||
                    milestoneEntry.getValue() < nextSolidificationCandidate.getValue())) {

                nextSolidificationCandidate = milestoneEntry;
            }
        }

        return nextSolidificationCandidate;
    }

    /**
     * This method performs the actual solidity check on the selected milestone.<br />
     * <br />
     * It first dumps a log message to keep the node operator informed about the progress of solidification, and then
     * issues the {@link TransactionValidator#checkSolidity(Hash, boolean, int)} call that starts the solidification
     * process.<br />
     * <br />
     * We limit the amount of transactions that may be processed during the solidity check, since we want to solidify
     * from the oldest milestone to the newest one and not "block" the solidification with a very recent milestone that
     * needs to traverse huge chunks of the tangle. The main goal of this is to give the solidification just enough
     * "resources" to discover the previous milestone while at the same time allowing fast solidity checks.<br />
     *
     * @param currentEntry milestone entry that shall be checked
     * @return true if the given milestone is solid or false otherwise
     */
    private boolean isSolid(Map.Entry<Hash, Integer> currentEntry) {
        if (unsolidMilestonesPool.size() > 1) {
            log.info("Solidifying milestone #" + currentEntry.getValue() +
                    " [" + milestonesToSolidify.size() + " / " + unsolidMilestonesPool.size() + "]");
        }

        try {
            return transactionValidator.checkSolidity(currentEntry.getKey(), true,
                    SOLIDIFICATION_TRANSACTIONS_LIMIT);
        } catch (Exception e) {
            log.error("Error while solidifying milestone #" + currentEntry.getValue(), e);

            return false;
        }
    }
}
