package com.iota.iri.service.milestone;

import com.iota.iri.TransactionValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotManager;
import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;
import com.iota.iri.utils.log.StatusLogger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Comparator.comparingInt;

/**
 * This class implements the logic for solidifying unsolid milestones.
 *
 * It manages a map of unsolid milestones to collect all milestones that have to be solidified. It then periodically
 * issues checkSolidity calls on the earliest milestone to solidify it.
 *
 * To save resources and make the call a little bit more efficient, we cache the earliest milestone in private class
 * properties so the relatively expensive task of having to search for the earliest milestone in the map only has to be
 * performed after the earliest milestone has become solid or irrelevant for our node.
 */
public class MilestoneSolidifier {
    /**
     * Defines the interval in which solidity checks are issued (in milliseconds).
     */
    private static final int SOLIDIFICATION_INTERVAL = 100;

    /**
     * Defines the maximum amount of transactions that are allowed to get processed while trying to solidify a milestone.
     */
    private static final int SOLIDIFICATION_TRANSACTIONS_LIMIT = 20000;

    /**
     * Defines after how many solidification attempts we increase the {@link #SOLIDIFICATION_TRANSACTIONS_LIMIT}.
     */
    private static final int SOLIDIFICATION_TRANSACTIONS_LIMIT_INCREMENT_INTERVAL = 50;

    /**
     * Defines how often we can at maximum increase the {@link #SOLIDIFICATION_TRANSACTIONS_LIMIT}.
     */
    private static final int SOLIDIFICATION_TRANSACTIONS_LIMIT_MAX_INCREMENT = 5;

    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private final StatusLogger statusLogger = new StatusLogger(LoggerFactory.getLogger(MilestoneSolidifier.class));

    /**
     * Holds a reference to the SnapshotManager which allows us to check if milestones are still relevant.
     */
    private SnapshotManager snapshotManager;

    /**
     * Holds a reference to the TransactionValidator which allows us to issue solidity checks.
     */
    private TransactionValidator transactionValidator;

    /**
     * List of unsolid milestones where we collect the milestones that shall be solidified.
     */
    private ConcurrentHashMap<Hash, Integer> unsolidMilestones = new ConcurrentHashMap<>();

    /**
     * The earliest unsolid milestone hash which is the one that is actively tried to get solidified.
     */
    private Hash earliestUnsolidMilestoneHash = null;

    /**
     * The earliest unsolid milestone index which is the one that is actively tried to get solidified.
     */
    private int earliestUnsolidMilestoneIndex = Integer.MAX_VALUE;

    /**
     * Holds the amount of solidity checks issued for the earliest milestone.
     */
    private int earliestUnsolidMilestoneSolidificationAttempts = 0;

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
     * @param snapshotManager SnapshotManager instance that is used by the node
     * @param transactionValidator TransactionValidator instance that is used by the node
     */
    public MilestoneSolidifier(SnapshotManager snapshotManager, TransactionValidator transactionValidator) {
        this.snapshotManager = snapshotManager;
        this.transactionValidator = transactionValidator;
    }

    /**
     * This method adds new milestones to our internal map of unsolid ones.
     *
     * It first checks if the passed in milestone is relevant for our node by checking against the initialSnapshot. If
     * it deems the milestone relevant it checks if the passed in milestone appeared earlier than the current one and
     * updates the interval properties accordingly.
     *
     * @param milestoneHash Hash of the milestone that shall be solidified
     * @param milestoneIndex index of the milestone that shall be solidified
     */
    public void add(Hash milestoneHash, int milestoneIndex) {
        if (milestoneIndex > snapshotManager.getInitialSnapshot().getIndex()) {
            if (milestoneIndex < earliestUnsolidMilestoneIndex) {
                earliestUnsolidMilestoneHash = milestoneHash;
                earliestUnsolidMilestoneIndex = milestoneIndex;
                earliestUnsolidMilestoneSolidificationAttempts = 0;
            }

            unsolidMilestones.put(milestoneHash, milestoneIndex);
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
     * This method contains the logic for the milestone solidification, that gets executed in a separate {@link Thread}.
     *
     * It periodically updates and checks the unsolid milestones by invoking {@link #processSolidificationTask()}.
     *
     * To allow for faster processing it only "waits" for another check if the current solidification task was not
     * finished (otherwise it immediately "continues" with the next one).
     */
    private void milestoneSolidificationThread() {
        while(!Thread.interrupted()) {
            if (processSolidificationTask()) {
                continue;
            }

            ThreadUtils.sleep(SOLIDIFICATION_INTERVAL);
        }
    }

    /**
     * This method returns the earliest seen Milestone from the internal Map of unsolid milestones.
     *
     * If no unsolid milestone was found it returns an Entry with the Hash being null and the index being
     * Integer.MAX_VALUE.
     *
     * @return the Map.Entry holding the earliest milestone or a default Map.Entry(null, Integer.MAX_VALUE)
     */
    private Map.Entry<Hash, Integer> getEarliestUnsolidMilestoneEntry() {
        try {
            return Collections.min(unsolidMilestones.entrySet(), comparingInt(Map.Entry::getValue));
        } catch (NoSuchElementException e) {
            return new AbstractMap.SimpleEntry<>(null, Integer.MAX_VALUE);
        }
    }

    /**
     * This method removes the current earliest Milestone from the map and sets the internal pointers to the next
     * earliest one.
     *
     * It is used to cycle through the unsolid milestones as they become solid or irrelevant for our node.
     */
    private void nextEarliestMilestone() {
        unsolidMilestones.remove(earliestUnsolidMilestoneHash);

        Map.Entry<Hash, Integer> nextEarliestMilestone = getEarliestUnsolidMilestoneEntry();

        earliestUnsolidMilestoneHash = nextEarliestMilestone.getKey();
        earliestUnsolidMilestoneIndex = nextEarliestMilestone.getValue();
        earliestUnsolidMilestoneSolidificationAttempts = 0;
    }

    /**
     * This method performs the actual solidity check on the selected milestone.
     *
     * It first checks if there is a milestone that has to be solidified and then issues the corresponding solidity
     * check. In addition to issuing the solidity checks, it dumps a log message to keep the node operator informed
     * about the progress of solidification.
     *
     * We limit the amount of transactions that may be processed during the solidity check, since we want to solidify
     * from the oldest milestone to the newest one and not "block" the solidification with a very recent milestone that
     * needs to traverse huge chunks of the tangle. If we fail to solidify a milestone for a certain amount of tries, we
     * increase the amount of transactions that may be processed by using an exponential binary backoff strategy. The
     * main goal of this is to give the solidification enough "resources" to discover the previous milestone (if it ever
     * gets stuck because of a very long path to the previous milestone) while at the same time allowing fast solidity
     * checks in "normal conditions".
     *
     * @return true if there are no unsolid milestones that have to be processed or if the earliest milestone is solid
     */
    private boolean isEarliestMilestoneSolid() {
        if (earliestUnsolidMilestoneHash == null) {
            return true;
        }

        if (unsolidMilestones.size() > 1) {
            statusLogger.status("Solidifying milestone #" + earliestUnsolidMilestoneIndex + " [" + unsolidMilestones.size() + " left]");
        }

        try {
            return transactionValidator.checkSolidity(
            earliestUnsolidMilestoneHash,
                true,
                (int) Math.pow(
                    2,
                    Math.min(
                        earliestUnsolidMilestoneSolidificationAttempts++ / SOLIDIFICATION_TRANSACTIONS_LIMIT_INCREMENT_INTERVAL,
                        SOLIDIFICATION_TRANSACTIONS_LIMIT_MAX_INCREMENT
                    ) * SOLIDIFICATION_TRANSACTIONS_LIMIT
                )
            );
        } catch (Exception e) {
            statusLogger.error("Error while solidifying milestone #" + earliestUnsolidMilestoneIndex, e);

            return false;
        }
    }

    /**
     * This method checks if the current unsolid milestone has become solid or irrelevant and advances to the next one
     * if that is the case.
     *
     * It is getting called by the solidification thread in regular intervals.
     *
     * @return true if the current solidification task was successful and the next milestone is due or false otherwise
     */
    private boolean processSolidificationTask() {
        if (earliestUnsolidMilestoneHash != null && (
            earliestUnsolidMilestoneIndex <= snapshotManager.getInitialSnapshot().getIndex() ||
            isEarliestMilestoneSolid()
        )) {
            nextEarliestMilestone();

            return true;
        }

        return false;
    }
}
