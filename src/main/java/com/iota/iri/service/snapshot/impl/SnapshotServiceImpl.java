package com.iota.iri.service.snapshot.impl;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.StateDiffViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotMetaData;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.service.transactionpruning.jobs.MilestonePrunerJob;
import com.iota.iri.service.transactionpruning.jobs.UnconfirmedSubtanglePrunerJob;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.log.ProgressLogger;
import com.iota.iri.utils.log.interval.IntervalProgressLogger;
import com.iota.iri.utils.dag.DAGHelper;
import com.iota.iri.utils.dag.TraversalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Creates a service instance that allows us to access the business logic for {@link Snapshot}s.<br />
 * <br />
 * The service instance is stateless and can be shared by multiple other consumers.<br />
 */
public class SnapshotServiceImpl implements SnapshotService {
    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(SnapshotServiceImpl.class);

    /**
     * If transactions got orphaned we wait this additional time (in seconds) until we consider them orphaned.
     */
    private static final int ORPHANED_TRANSACTION_GRACE_TIME = 3600;

    /**
     * Maximum age in milestones since creation of solid entry points.
     *
     * Since it is possible to artificially keep old solid entry points alive by periodically attaching new transactions
     * to them, we limit the life time of solid entry points and ignore them whenever they become too old. This is a
     * measure against a potential attack vector where somebody might try to blow up the meta data of local snapshots.
     */
    private static final int SOLID_ENTRY_POINT_LIFETIME = 1000;

    /**
     * Holds the tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds the config with important snapshot specific settings.<br />
     */
    private SnapshotConfig config;

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
     *       {@code snapshotService = new SnapshotServiceImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param config important snapshot related configuration parameters
     * @return the initialized instance itself to allow chaining
     */
    public SnapshotServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotConfig config) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.config = config;

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replayMilestones(Snapshot snapshot, int targetMilestoneIndex) throws SnapshotException {
        snapshot.lockWrite();

        Snapshot snapshotBeforeChanges = new SnapshotImpl(snapshot);

        try {
            for (int currentMilestoneIndex = snapshot.getIndex() + 1; currentMilestoneIndex <= targetMilestoneIndex;
                 currentMilestoneIndex++) {

                MilestoneViewModel currentMilestone = MilestoneViewModel.get(tangle, currentMilestoneIndex);
                if (currentMilestone != null) {
                    StateDiffViewModel stateDiffViewModel = StateDiffViewModel.load(tangle, currentMilestone.getHash());
                    if(!stateDiffViewModel.isEmpty()) {
                        snapshot.applyStateDiff(new SnapshotStateDiffImpl(stateDiffViewModel.getDiff()));
                    }

                    snapshot.setIndex(currentMilestone.index());
                    snapshot.setHash(currentMilestone.getHash());

                    TransactionViewModel currentMilestoneTransaction = TransactionViewModel.fromHash(tangle,
                            currentMilestone.getHash());

                    if(currentMilestoneTransaction != null &&
                            currentMilestoneTransaction.getType() != TransactionViewModel.PREFILLED_SLOT) {

                        snapshot.setTimestamp(currentMilestoneTransaction.getTimestamp());
                    }
                } else {
                    snapshot.addSkippedMilestone(currentMilestoneIndex);
                }
            }
        } catch (Exception e) {
            snapshot.update(snapshotBeforeChanges);

            throw new SnapshotException("failed to replay the the state of the ledger", e);
        } finally {
            snapshot.unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rollBackMilestones(Snapshot snapshot, int targetMilestoneIndex) throws SnapshotException {
        if(targetMilestoneIndex <= snapshot.getInitialIndex() || targetMilestoneIndex > snapshot.getIndex()) {
            throw new SnapshotException("invalid milestone index");
        }

        snapshot.lockWrite();

        Snapshot snapshotBeforeChanges = new SnapshotImpl(snapshot);

        try {
            boolean rollbackSuccessful = true;
            while (targetMilestoneIndex <= snapshot.getIndex() && rollbackSuccessful) {
                rollbackSuccessful = rollbackLastMilestone(tangle, snapshot);
            }

            if(targetMilestoneIndex < snapshot.getIndex()) {
                throw new SnapshotException("failed to reach the target milestone index when rolling back milestones");
            }
        } catch(SnapshotException e) {
            snapshot.update(snapshotBeforeChanges);

            throw e;
        } finally {
            snapshot.unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void takeLocalSnapshot(LatestMilestoneTracker latestMilestoneTracker, TransactionPruner transactionPruner)
            throws SnapshotException {

        MilestoneViewModel targetMilestone = determineMilestoneForLocalSnapshot(tangle, snapshotProvider, config);

        Snapshot newSnapshot = generateSnapshot(latestMilestoneTracker, targetMilestone);

        cleanupExpiredSolidEntryPoints(tangle, snapshotProvider.getInitialSnapshot().getSolidEntryPoints(),
                newSnapshot.getSolidEntryPoints(), transactionPruner);

        cleanupOldData(config, transactionPruner, targetMilestone);

        persistLocalSnapshot(snapshotProvider, newSnapshot, config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Snapshot generateSnapshot(LatestMilestoneTracker latestMilestoneTracker, MilestoneViewModel targetMilestone)
            throws SnapshotException {

        if (targetMilestone == null) {
            throw new SnapshotException("the target milestone must not be null");
        } else if (targetMilestone.index() > snapshotProvider.getLatestSnapshot().getIndex()) {
            throw new SnapshotException("the snapshot target " + targetMilestone + " was not solidified yet");
        } else if (targetMilestone.index() < snapshotProvider.getInitialSnapshot().getIndex()) {
            throw new SnapshotException("the snapshot target " + targetMilestone + " is too old");
        }

        snapshotProvider.getInitialSnapshot().lockRead();
        snapshotProvider.getLatestSnapshot().lockRead();

        Snapshot snapshot;
        try {
            int distanceFromInitialSnapshot = Math.abs(snapshotProvider.getInitialSnapshot().getIndex() -
                    targetMilestone.index());
            int distanceFromLatestSnapshot = Math.abs(snapshotProvider.getLatestSnapshot().getIndex() -
                    targetMilestone.index());

            if (distanceFromInitialSnapshot <= distanceFromLatestSnapshot) {
                snapshot = new SnapshotImpl(snapshotProvider.getInitialSnapshot());

                replayMilestones(snapshot, targetMilestone.index());
            } else {
                snapshot = new SnapshotImpl(snapshotProvider.getLatestSnapshot());

                rollBackMilestones(snapshot, targetMilestone.index() + 1);
            }
        } finally {
            snapshotProvider.getInitialSnapshot().unlockRead();
            snapshotProvider.getLatestSnapshot().unlockRead();
        }

        snapshot.setSolidEntryPoints(generateSolidEntryPoints(targetMilestone));
        snapshot.setSeenMilestones(generateSeenMilestones(latestMilestoneTracker, targetMilestone));

        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Hash, Integer> generateSolidEntryPoints(MilestoneViewModel targetMilestone) throws SnapshotException {
        Map<Hash, Integer> solidEntryPoints = new HashMap<>();
        solidEntryPoints.put(Hash.NULL_HASH, targetMilestone.index());

        processOldSolidEntryPoints(tangle, snapshotProvider, targetMilestone, solidEntryPoints);
        processNewSolidEntryPoints(tangle, snapshotProvider, targetMilestone, solidEntryPoints);

        return solidEntryPoints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Hash, Integer> generateSeenMilestones(LatestMilestoneTracker latestMilestoneTracker,
            MilestoneViewModel targetMilestone) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [processing seen milestones]", log)
                .start(config.getLocalSnapshotsDepth());

        Map<Hash, Integer> seenMilestones = new HashMap<>();
        try {
            MilestoneViewModel seenMilestone = targetMilestone;
            while ((seenMilestone = MilestoneViewModel.findClosestNextMilestone(tangle, seenMilestone.index(),
                    latestMilestoneTracker.getLatestMilestoneIndex())) != null) {

                seenMilestones.put(seenMilestone.getHash(), seenMilestone.index());

                progressLogger.progress();
            }
        } catch (Exception e) {
            progressLogger.abort(e);

            throw new SnapshotException("could not generate the set of seen milestones", e);
        }

        progressLogger.finish();

        return seenMilestones;
    }

    /**
     * This method reverts the changes caused by the last milestone that was applied to this snapshot.
     *
     * It first checks if we didn't arrive at the initial index yet and then reverts the balance changes that were
     * caused by the last milestone. Then it checks if any milestones were skipped while applying the last milestone and
     * determines the {@link SnapshotMetaData} that this Snapshot had before and restores it.
     *
     * @param tangle Tangle object which acts as a database interface
     * @return true if the snapshot was rolled back or false otherwise
     * @throws SnapshotException if anything goes wrong while accessing the database
     */
    private boolean rollbackLastMilestone(Tangle tangle, Snapshot snapshot) throws SnapshotException {
        if (snapshot.getIndex() == snapshot.getInitialIndex()) {
            return false;
        }

        snapshot.lockWrite();

        try {
            // revert the last balance changes
            StateDiffViewModel stateDiffViewModel = StateDiffViewModel.load(tangle, snapshot.getHash());
            if (!stateDiffViewModel.isEmpty()) {
                SnapshotStateDiffImpl snapshotStateDiff = new SnapshotStateDiffImpl(
                    stateDiffViewModel.getDiff().entrySet().stream().map(
                        hashLongEntry -> new HashMap.SimpleEntry<>(
                            hashLongEntry.getKey(), -1 * hashLongEntry.getValue()
                        )
                    ).collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)
                    )
                );

                if (!snapshotStateDiff.isConsistent()) {
                    throw new SnapshotException("the StateDiff belonging to milestone #" + snapshot.getIndex() +
                            " (" + snapshot.getHash() + ") is inconsistent");
                } else if (!snapshot.patchedState(snapshotStateDiff).isConsistent()) {
                    throw new SnapshotException("failed to apply patch belonging to milestone #" + snapshot.getIndex() +
                            " (" + snapshot.getHash() + ")");
                }

                snapshot.applyStateDiff(snapshotStateDiff);
            }

            // jump skipped milestones
            int currentIndex = snapshot.getIndex() - 1;
            while (snapshot.removeSkippedMilestone(currentIndex)) {
                currentIndex--;
            }

            // check if we arrived at the start
            if (currentIndex <= snapshot.getInitialIndex()) {
                snapshot.setIndex(snapshot.getInitialIndex());
                snapshot.setHash(snapshot.getInitialHash());
                snapshot.setTimestamp(snapshot.getInitialTimestamp());

                return true;
            }

            // otherwise set metadata of the previous milestone
            MilestoneViewModel currentMilestone = MilestoneViewModel.get(tangle, currentIndex);
            snapshot.setIndex(currentMilestone.index());
            snapshot.setHash(currentMilestone.getHash());
            snapshot.setTimestamp(TransactionViewModel.fromHash(tangle, currentMilestone.getHash()).getTimestamp());

            return true;
        } catch (Exception e) {
            throw new SnapshotException("failed to rollback last milestone", e);
        } finally {
            snapshot.unlockWrite();
        }
    }

    /**
     * This method determines the milestone that shall be used for the local snapshot.
     *
     * It determines the milestone by subtracting the {@link SnapshotConfig#getLocalSnapshotsDepth()} from the latest
     * solid milestone index and retrieving the next milestone before this point.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param config important snapshot related configuration parameters
     * @return the target milestone for the local snapshot
     * @throws SnapshotException if anything goes wrong while determining the target milestone for the local snapshot
     */
    private MilestoneViewModel determineMilestoneForLocalSnapshot(Tangle tangle, SnapshotProvider snapshotProvider,
            SnapshotConfig config) throws SnapshotException {

        int targetMilestoneIndex = snapshotProvider.getLatestSnapshot().getIndex() - config.getLocalSnapshotsDepth();

        MilestoneViewModel targetMilestone;
        try {
            targetMilestone = MilestoneViewModel.findClosestPrevMilestone(tangle, targetMilestoneIndex,
                    snapshotProvider.getInitialSnapshot().getIndex());
        } catch (Exception e) {
            throw new SnapshotException("could not load the target milestone", e);
        }
        if (targetMilestone == null) {
            throw new SnapshotException("missing milestone with an index of " + targetMilestoneIndex + " or lower");
        }

        return targetMilestone;
    }

    /**
     * This method creates {@link com.iota.iri.service.transactionpruning.TransactionPrunerJob}s for the expired solid
     * entry points, which removes the unconfirmed subtangles branching off of these transactions.
     *
     * We only clean up these subtangles if the transaction that they are branching off has been cleaned up already by a
     * {@link MilestonePrunerJob}. If the corresponding milestone has not been processed we leave them in the database
     * so we give the node a little bit more time to "use" these transaction for references from future milestones. This
     * is used to correctly reflect the {@link SnapshotConfig#getLocalSnapshotsPruningDelay()}, where we keep old data
     * prior to a snapshot.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param oldSolidEntryPoints solid entry points of the current initial {@link Snapshot}
     * @param newSolidEntryPoints solid entry points of the new initial {@link Snapshot}
     * @param transactionPruner manager for the pruning jobs that takes care of cleaning up the old data that
     */
    private void cleanupExpiredSolidEntryPoints(Tangle tangle, Map<Hash, Integer> oldSolidEntryPoints,
            Map<Hash, Integer> newSolidEntryPoints, TransactionPruner transactionPruner) {

        oldSolidEntryPoints.forEach((transactionHash, milestoneIndex) -> {
            if (!newSolidEntryPoints.containsKey(transactionHash)) {
                try {
                    // only clean up if the corresponding milestone transaction was cleaned up already -> otherwise
                    // let the MilestonePrunerJob do this
                    if (TransactionViewModel.fromHash(tangle, transactionHash).getType() ==
                            TransactionViewModel.PREFILLED_SLOT) {

                        transactionPruner.addJob(new UnconfirmedSubtanglePrunerJob(transactionHash));
                    }
                } catch (Exception e) {
                    log.error("failed to add cleanup job to the transaction pruner", e);
                }
            }
        });
    }

    /**
     * This method creates the {@link com.iota.iri.service.transactionpruning.TransactionPrunerJob}s that are
     * responsible for removing the old data.
     *
     * It first calculates the range of milestones that shall be deleted and then issues a {@link MilestonePrunerJob}
     * for this range (if it is not empty).
     *
     * @param config important snapshot related configuration parameters
     * @param transactionPruner  manager for the pruning jobs that takes care of cleaning up the old data that
     * @param targetMilestone milestone that was used as a reference point for the local snapshot
     * @throws SnapshotException if anything goes wrong while issuing the cleanup jobs
     */
    private void cleanupOldData(SnapshotConfig config, TransactionPruner transactionPruner,
            MilestoneViewModel targetMilestone) throws SnapshotException {

        int targetIndex = targetMilestone.index() - config.getLocalSnapshotsPruningDelay();
        int startingIndex = config.getMilestoneStartIndex() + 1;

        try {
            if (targetIndex >= startingIndex) {
                transactionPruner.addJob(new MilestonePrunerJob(startingIndex, targetIndex));
            }
        } catch (TransactionPruningException e) {
            throw new SnapshotException("could not add the cleanup job to the transaction pruner", e);
        }
    }

    /**
     * This method persists the local snapshot on the disk and updates the instances used by the
     * {@link SnapshotProvider}.
     *
     * It first writes the files to the disk and then updates the two {@link Snapshot}s accordingly.
     *
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param newSnapshot Snapshot that shall be persisted
     * @param config important snapshot related configuration parameters
     * @throws SnapshotException if anything goes wrong while persisting the snapshot
     */
    private void persistLocalSnapshot(SnapshotProvider snapshotProvider, Snapshot newSnapshot, SnapshotConfig config)
            throws SnapshotException {

        snapshotProvider.writeSnapshotToDisk(newSnapshot, config.getLocalSnapshotsBasePath());

        snapshotProvider.getInitialSnapshot().lockWrite();
        snapshotProvider.getLatestSnapshot().lockWrite();

        snapshotProvider.getInitialSnapshot().update(newSnapshot);

        snapshotProvider.getLatestSnapshot().setInitialHash(newSnapshot.getHash());
        snapshotProvider.getLatestSnapshot().setInitialIndex(newSnapshot.getIndex());
        snapshotProvider.getLatestSnapshot().setInitialTimestamp(newSnapshot.getTimestamp());

        snapshotProvider.getInitialSnapshot().unlockWrite();
        snapshotProvider.getLatestSnapshot().unlockWrite();
    }

    /**
     * This method determines if a transaction is orphaned.
     *
     * Since there is no hard definition for when a transaction can be considered to be orphaned, we define orphaned in
     * relation to a referenceTransaction. If the transaction or any of its direct or indirect approvers saw a
     * transaction being attached to it, that arrived after our reference transaction, we consider it "not orphaned".
     *
     * Since we currently use milestones as reference transactions that are sufficiently old, this definition in fact is
     * a relatively safe way to determine if a subtangle "above" a transaction got orphaned.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param transaction transaction that shall be checked
     * @param referenceTransaction transaction that acts as a judge to the other transaction
     * @param processedTransactions transactions that were visited already while trying to determine the orphaned status
     * @return true if the transaction got orphaned and false otherwise
     * @throws SnapshotException if anything goes wrong while determining the orphaned status
     */
    private boolean isOrphaned(Tangle tangle, TransactionViewModel transaction,
            TransactionViewModel referenceTransaction, Set<Hash> processedTransactions) throws SnapshotException {

        long arrivalTime = transaction.getArrivalTime() / 1000L + ORPHANED_TRANSACTION_GRACE_TIME;
        if (arrivalTime > referenceTransaction.getTimestamp()) {
            return false;
        }

        AtomicBoolean nonOrphanedTransactionFound = new AtomicBoolean(false);
        try {
            DAGHelper.get(tangle).traverseApprovers(
                    transaction.getHash(),
                    currentTransaction -> !nonOrphanedTransactionFound.get(),
                    currentTransaction -> {
                        if (arrivalTime > referenceTransaction.getTimestamp()) {
                            nonOrphanedTransactionFound.set(true);
                        }
                    },
                    processedTransactions
            );
        } catch (TraversalException e) {
            throw new SnapshotException("failed to determine orphaned status of " + transaction, e);
        }

        return !nonOrphanedTransactionFound.get();
    }

    /**
     * This method checks if a transaction is a solid entry point for the targetMilestone.
     *
     * A transaction is considered a solid entry point if it has non-orphaned approvers.
     *
     * To check if the transaction has non-orphaned approvers we first check if any of its approvers got confirmed by a
     * future milestone, since this is very cheap. If none of them got confirmed by another milestone we do the more
     * expensive check from {@link #isOrphaned(Tangle, TransactionViewModel, TransactionViewModel, Set)}.
     *
     * Since solid entry points have a limited life time and to prevent potential problems due to temporary errors in
     * the database, we assume that the checked transaction is a solid entry point if any error occurs while determining
     * its status. This is a storage <=> reliability trade off, since the only bad effect of having too many solid entry
     * points) is a bigger snapshot file.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param transactionHash hash of the transaction that shall be checked
     * @param targetMilestone milestone that is used as an anchor for our checks
     * @return true if the transaction is a solid entry point and false otherwise
     */
    private boolean isSolidEntryPoint(Tangle tangle, Hash transactionHash, MilestoneViewModel targetMilestone) {
        Set<TransactionViewModel> unconfirmedApprovers = new HashSet<>();

        try {
            for (Hash approverHash : ApproveeViewModel.load(tangle, transactionHash).getHashes()) {
                TransactionViewModel approver = TransactionViewModel.fromHash(tangle, approverHash);

                if (approver.snapshotIndex() > targetMilestone.index()) {
                    return true;
                } else if (approver.snapshotIndex() == 0) {
                    unconfirmedApprovers.add(approver);
                }
            }

            Set<Hash> processedTransactions = new HashSet<>();
            TransactionViewModel milestoneTransaction = TransactionViewModel.fromHash(tangle, targetMilestone.getHash());
            for (TransactionViewModel unconfirmedApprover : unconfirmedApprovers) {
                if (!isOrphaned(tangle, unconfirmedApprover, milestoneTransaction, processedTransactions)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("failed to determine the solid entry point status for transaction " + transactionHash, e);

            return true;
        }

        return false;
    }

    /**
     * This method analyzes the old solid entry points and determines if they are still not orphaned.
     *
     * It simply iterates through the old solid entry points and checks them one by one. If an old solid entry point
     * is found to still be relevant it is added to the passed in map.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param targetMilestone milestone that is used to generate the solid entry points
     * @param solidEntryPoints map that is used to collect the solid entry points
     */
    private void processOldSolidEntryPoints(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneViewModel targetMilestone, Map<Hash, Integer> solidEntryPoints) {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [analyzing old solid entry points]", log)
                .start(snapshotProvider.getInitialSnapshot().getSolidEntryPoints().size());

        Snapshot initialSnapshot = snapshotProvider.getInitialSnapshot();
        initialSnapshot.getSolidEntryPoints().forEach((hash, milestoneIndex) -> {
            if (!Hash.NULL_HASH.equals(hash) && targetMilestone.index() - milestoneIndex <= SOLID_ENTRY_POINT_LIFETIME
                    && isSolidEntryPoint(tangle, hash, targetMilestone)) {

                solidEntryPoints.put(hash, milestoneIndex);
            }

            progressLogger.progress();
        });

        progressLogger.finish();
    }

    /**
     * This method retrieves the new solid entry points of the snapshot reference given by the target milestone.
     *
     * It iterates over all unprocessed milestones and analyzes their directly and indirectly approved transactions.
     * Every transaction is checked for being a solid entry point and added to the passed in map (if it was found to be
     * one).
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param targetMilestone milestone that is used to generate the solid entry points
     * @param solidEntryPoints map that is used to collect the solid entry points
     * @throws SnapshotException if anything goes wrong while determining the solid entry points
     */
    private void processNewSolidEntryPoints(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneViewModel targetMilestone, Map<Hash, Integer> solidEntryPoints) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [generating solid entry points]", log);

        try {
            progressLogger.start(targetMilestone.index() - snapshotProvider.getInitialSnapshot().getIndex());

            MilestoneViewModel nextMilestone = targetMilestone;
            while (nextMilestone != null && nextMilestone.index() > snapshotProvider.getInitialSnapshot().getIndex() &&
                    progressLogger.getCurrentStep() < progressLogger.getStepCount()) {

                MilestoneViewModel currentMilestone = nextMilestone;
                DAGHelper.get(tangle).traverseApprovees(
                        currentMilestone.getHash(),
                        currentTransaction -> currentTransaction.snapshotIndex() >= currentMilestone.index(),
                        currentTransaction -> {
                            if (isSolidEntryPoint(tangle, currentTransaction.getHash(), targetMilestone)) {
                                solidEntryPoints.put(currentTransaction.getHash(), targetMilestone.index());
                            }
                        }
                );

                solidEntryPoints.put(currentMilestone.getHash(), targetMilestone.index());

                nextMilestone = MilestoneViewModel.findClosestPrevMilestone(tangle, currentMilestone.index(),
                        snapshotProvider.getInitialSnapshot().getIndex());

                progressLogger.progress();
            }

            progressLogger.finish();
        } catch (Exception e) {
            progressLogger.abort(e);

            throw new SnapshotException("could not generate the solid entry points for " + targetMilestone, e);
        }
    }
}
