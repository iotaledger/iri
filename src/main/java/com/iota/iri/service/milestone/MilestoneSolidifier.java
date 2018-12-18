package com.iota.iri.service.milestone;

import com.iota.iri.validator.TransactionValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.utils.log.Logger;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements the logic for solidifying unsolid milestones.
 *
 * It manages a map of unsolid milestones to collect all milestones that have to be solidified. It then periodically
 * issues checkSolidity calls on the earliest milestones to solidify them.
 *
 * To save resources and make the call a little bit more efficient, we cache the earliest milestones in a separate map,
 * so the relatively expensive task of having to search for the next earliest milestone in the pool only has to be
 * performed after a milestone has become solid or irrelevant for our node.
 */
public class MilestoneSolidifier {
    /**
     * Defines the amount of milestones that we "simultaneously" try to solidify in one pass.
     */
    private static final int SOLIDIFICATION_QUEUE_SIZE = 10;

    /**
     * Defines the interval in which solidity checks are issued (in milliseconds).
     */
    private static final int SOLIDIFICATION_INTERVAL = 100;

    /**
     * Defines the maximum amount of transactions that are allowed to get processed while trying to solidify a milestone.
     *
     * Note: We want to find the next previous milestone and not get stuck somewhere at the end of the tangle with a
     *       long running {@link TransactionValidator#checkSolidity(Hash, boolean)} call.
     */
    private static final int SOLIDIFICATION_TRANSACTIONS_LIMIT = 50000;

    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger statusLogger = new IntervalLogger(MilestoneSolidifier.class);

    /**
     * Holds a reference to the initial Snapshot which allows us to check if milestones are still relevant.
     */
    private Snapshot initialSnapshot;

    /**
     * Holds a reference to the TransactionValidator which allows us to issue solidity checks.
     */
    private TransactionValidator transactionValidator;

    /**
     * Holds the milestones that were newly added, but not examined yet.
     *
     * Note: This is used to be able to add milestones to the solidifier without having to synchronize the access to the
     *       underlying Maps.
     */
    private Map<Hash, Integer> newlyAddedMilestones = new ConcurrentHashMap<>();

    /**
     * Holds all unsolid milestones that shall be solidified (the transaction hash mapped to its milestone index).
     */
    private Map<Hash, Integer> unsolidMilestonesPool = new ConcurrentHashMap<>();

    /**
     * Holds the milestones that are actively trying to be solidified by the background {@link Thread} (acts as a Queue).
     */
    private Map<Hash, Integer> milestonesToSolidify = new HashMap<>();

    /**
     * Holds and entry that represents the youngest milestone in the {@link #milestonesToSolidify} Map.
     *
     * Note: It is used to check if new milestones that are being added, are older that the currently processed ones and
     *       should replace them in the queue (we solidify from oldest to youngest).
     */
    private Map.Entry<Hash, Integer> youngestMilestoneInQueue = null;

    /**
     * Holds a reference to the {@link ThreadIdentifier} for the solidification thread.
     *
     * Using a {@link ThreadIdentifier} for spawning the thread allows the {@link ThreadUtils} to spawn exactly one
     * thread for this instance even when we call the {@link #start()} method multiple times.
     */
    private final ThreadIdentifier solidificationThreadIdentifier = new ThreadIdentifier("Milestone Solidifier");

    /**
     * Constructor of the class.
     *
     * It simply stores the passed in parameters to be able to access them later on.
     *
     * @param initialSnapshot initial Snapshot instance that is used by the node
     * @param transactionValidator TransactionValidator instance that is used by the node
     */
    public MilestoneSolidifier(Snapshot initialSnapshot, TransactionValidator transactionValidator) {
        this.initialSnapshot = initialSnapshot;
        this.transactionValidator = transactionValidator;
    }

    /**
     * This method allows us to add new milestones to the solidifier.
     *
     * Before adding the given milestone we check if it is currently relevant for our node.
     *
     * Since this method might be called from a performance critical context, we simply add the milestone to a temporary
     * pool, that gets examined later by the background process. This doesn't just speed up the addition of new jobs but
     * also prevents us from having to synchronize the access to the underlying maps.
     *
     * @param milestoneHash Hash of the milestone that shall be solidified
     * @param milestoneIndex index of the milestone that shall be solidified
     */
    public void add(Hash milestoneHash, int milestoneIndex) {
        if (!unsolidMilestonesPool.containsKey(milestoneHash) && !newlyAddedMilestones.containsKey(milestoneHash) &&
                milestoneIndex > initialSnapshot.getIndex()) {

            newlyAddedMilestones.put(milestoneHash, milestoneIndex);
        }
    }

    /**
     * This method starts the solidification {@link Thread} that asynchronously solidifies the milestones.
     *
     * This method is thread safe since we use a {@link ThreadIdentifier} to address the {@link Thread}. The
     * {@link ThreadUtils} take care of only launching exactly one {@link Thread} that is not terminated.
     */
    public void start() {
        ThreadUtils.spawnThread(this::milestoneSolidificationThread, solidificationThreadIdentifier);
    }

    /**
     * This method shuts down the solidification thread.
     *
     * It does not actively terminate the thread but sets the isInterrupted flag. Since we use a {@link ThreadIdentifier}
     * to address the {@link Thread}, this method is thread safe.
     */
    public void shutdown() {
        ThreadUtils.stopThread(solidificationThreadIdentifier);
    }

    /**
     * This method takes an entry from the {@link #unsolidMilestonesPool} and adds it to the
     * {@link #milestonesToSolidify} queue.
     *
     * It first checks if the given milestone is already part of the queue and then tries to add it. If the queue is not
     * full yet, the addition to the queue is relatively cheap, because the {@link #youngestMilestoneInQueue} marker can
     * be updated without iterating over all entries. If the queue reached its capacity already, we replace the entry
     * marked by the {@link #youngestMilestoneInQueue} marker and update the marker by recalculating it using
     * {@link #determineYoungestMilestoneInQueue()}.
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
     * This method contains the logic for the milestone solidification, that gets executed in a separate {@link Thread}.
     *
     * It simply executes the necessary steps periodically while waiting a short time to give the nodes the ability to
     * answer to the issued transaction requests.
     */
    private void milestoneSolidificationThread() {
        while(!Thread.currentThread().isInterrupted()) {
            processNewlyAddedMilestones();
            processSolidificationQueue();
            refillSolidificationQueue();

            ThreadUtils.sleep(SOLIDIFICATION_INTERVAL);
        }
    }

    /**
     * This method processes the newly added milestones.
     *
     * We process them lazy to decrease the synchronization requirements and speed up the addition of milestones from
     * outside {@link Thread}s.
     *
     * It simply iterates over the milestones and adds them to the pool. If they are older than the
     * {@link #youngestMilestoneInQueue}, we add the to the solidification queue.
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
     * This method contains the logic for processing the {@link #milestonesToSolidify}.
     *
     * It iterates through the queue and checks if the corresponding milestones are still relevant for our node, or if
     * they could be successfully solidified. If the milestones become solid or irrelevant, we remove them from the
     * pool and the queue and reset the {@link #youngestMilestoneInQueue} marker (if necessary).
     */
    private void processSolidificationQueue() {
        for (Iterator<Map.Entry<Hash, Integer>> iterator = milestonesToSolidify.entrySet().iterator();
                !Thread.currentThread().isInterrupted() && iterator.hasNext();) {

            Map.Entry<Hash, Integer> currentEntry = iterator.next();

            if (currentEntry.getValue() <= initialSnapshot.getIndex() || isSolid(currentEntry)) {
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
     * again after it was processed / emptied before.
     *
     * It first updates the {@link #youngestMilestoneInQueue} marker and then just adds new milestones as long as there
     * is still space in the {@link #milestonesToSolidify} queue.
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
     * This method determines the youngest milestone in the solidification queue.
     *
     * It iterates over all milestones in the Queue and keeps track of the youngest one found (the one with the highest
     * milestone index).
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
     * {@link #milestonesToSolidify} queue yet.
     *
     * It simply iterates over all milestones in the pool and looks for the one with the lowest index, that is not
     * getting actively solidified, yet.
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
     * This method performs the actual solidity check on the selected milestone.
     *
     * It first dumps a log message to keep the node operator informed about the progress of solidification, and then
     * issues the {@link TransactionValidator#checkSolidity(Hash, boolean, int)} call that starts the solidification
     * process.
     *
     * We limit the amount of transactions that may be processed during the solidity check, since we want to solidify
     * from the oldest milestone to the newest one and not "block" the solidification with a very recent milestone that
     * needs to traverse huge chunks of the tangle. The main goal of this is to give the solidification just enough
     * "resources" to discover the previous milestone while at the same time allowing fast solidity checks.
     *
     * @param currentEntry milestone entry that shall be checked
     * @return true if the given milestone is solid or false otherwise
     */
    private boolean isSolid(Map.Entry<Hash, Integer> currentEntry) {
        if (unsolidMilestonesPool.size() > 1) {
            statusLogger.info("Solidifying milestone #" + currentEntry.getValue() +
                    " [" + milestonesToSolidify.size() + " / " + unsolidMilestonesPool.size() + "]");
        }

        try {
            return transactionValidator.checkSolidity(currentEntry.getKey(), true,
                    SOLIDIFICATION_TRANSACTIONS_LIMIT);
        } catch (Exception e) {
            statusLogger.error("Error while solidifying milestone #" + currentEntry.getValue(), e);

            return false;
        }
    }
}
