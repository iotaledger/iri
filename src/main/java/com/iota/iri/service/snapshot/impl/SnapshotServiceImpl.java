package com.iota.iri.service.snapshot.impl;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.StateDiffViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotMetaData;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.service.transactionpruning.jobs.MilestonePrunerJob;
import com.iota.iri.service.transactionpruning.jobs.UnconfirmedSubtanglePrunerJob;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.dag.DAGHelper;
import com.iota.iri.utils.dag.TraversalException;
import com.iota.iri.utils.log.ProgressLogger;
import com.iota.iri.utils.log.interval.IntervalProgressLogger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Creates a service instance that allows us to access the business logic for {@link Snapshot}s.
 * </p>
 * <p>
 * The service instance is stateless and can be shared by multiple other consumers.
 * </p>
 */
public class SnapshotServiceImpl implements SnapshotService {
    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(SnapshotServiceImpl.class);

    /**
     * <p>
     * Holds a limit for the amount of milestones we go back in time when generating the solid entry points (to speed up
     * the snapshot creation).
     * </p>
     * <p>
     * Note: Since the snapshot creation is a "continuous" process where we build upon the information gathered during
     *       the creation of previous snapshots, we do not need to analyze all previous milestones but can rely on
     *       slowly gathering the missing information over time. While this might lead to a situation where the very
     *       first snapshots taken by a node might generate snapshot files that can not reliably be used by other nodes
     *       to sync it is still a reasonable trade-off to reduce the load on the nodes. We just assume that anybody who
     *       wants to share his snapshots with the community as a way to bootstrap new nodes will run his snapshot
     *       enabled node for a few hours before sharing his files (this is a problem in very rare edge cases when
     *       having back-referencing transactions anyway).
     * </p>
     */
    private static final int OUTER_SHELL_SIZE = 100;

    /**
     * <p>
     * Maximum age in milestones since creation of solid entry points.
     * </p>
     * <p>
     * Since it is possible to artificially keep old solid entry points alive by periodically attaching new transactions
     * to them, we limit the life time of solid entry points and ignore them whenever they become too old. This is a
     * measure against a potential attack vector where somebody might try to blow up the meta data of local snapshots.
     * </p>
     */
    private static final int SOLID_ENTRY_POINT_LIFETIME = 1000;

    /**
     * Holds the tangle object which acts as a database interface.
     */
    private final Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.
     */
    private final SnapshotProvider snapshotProvider;

    /**
     * Holds the config with important snapshot specific settings.
     */
    private final SnapshotConfig config;

    /**
     * Implements the snapshot service. See interface for more information.
     * @param tangle acts as a database interface.
     * @param snapshotProvider gives us access to the relevant snapshots.
     * @param config configuration with snapshot specific settings.
     */
    public SnapshotServiceImpl(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotConfig config) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.config = config;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * To increase the performance of this operation, we do not apply every single milestone separately but first
     * accumulate all the necessary changes and then apply it to the snapshot in a single run. This allows us to
     * modify its values without having to create a "copy" of the initial state to possibly roll back the changes if
     * anything unexpected happens (creating a backup of the state requires a lot of memory).
     * </p>
     */
    @Override
    public void replayMilestones(Snapshot snapshot, int targetMilestoneIndex) throws SnapshotException {
        Map<Hash, Long> balanceChanges = new HashMap<>();
        Set<Integer> skippedMilestones = new HashSet<>();
        MilestoneViewModel lastAppliedMilestone = null;

        try {
            for (int currentMilestoneIndex = snapshot.getIndex() + 1; currentMilestoneIndex <= targetMilestoneIndex;
                 currentMilestoneIndex++) {

                MilestoneViewModel currentMilestone = MilestoneViewModel.get(tangle, currentMilestoneIndex);
                if (currentMilestone != null) {
                    StateDiffViewModel stateDiffViewModel = StateDiffViewModel.load(tangle, currentMilestone.getHash());
                    if(!stateDiffViewModel.isEmpty()) {
                        stateDiffViewModel.getDiff().forEach((address, change) -> {
                            balanceChanges.compute(address, (k, balance) -> (balance == null ? 0 : balance) + change);
                        });
                    }

                    lastAppliedMilestone = currentMilestone;
                } else {
                    skippedMilestones.add(currentMilestoneIndex);
                }
            }

            if (lastAppliedMilestone != null) {
                try {
                    snapshot.lockWrite();

                    snapshot.applyStateDiff(new SnapshotStateDiffImpl(balanceChanges));

                    snapshot.setIndex(lastAppliedMilestone.index());
                    snapshot.setHash(lastAppliedMilestone.getHash());

                    TransactionViewModel milestoneTransaction = TransactionViewModel.fromHash(tangle,
                            lastAppliedMilestone.getHash());
                    if(milestoneTransaction.getType() != TransactionViewModel.PREFILLED_SLOT) {
                        snapshot.setTimestamp(milestoneTransaction.getTimestamp());
                    }

                    for (int skippedMilestoneIndex : skippedMilestones) {
                        snapshot.addSkippedMilestone(skippedMilestoneIndex);
                    }
                } finally {
                    snapshot.unlockWrite();
                }
            }
        } catch (Exception e) {
            throw new SnapshotException("failed to replay the state of the ledger", e);
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

        Snapshot snapshotBeforeChanges = snapshot.clone();

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
    public Snapshot takeLocalSnapshot(MilestoneSolidifier milestoneSolidifier, TransactionPruner transactionPruner,
                                  int snapshotUntillIndex) throws SnapshotException {

        MilestoneViewModel targetMilestone = determineMilestoneForLocalSnapshot(tangle, snapshotProvider,
                snapshotUntillIndex);
        Snapshot newSnapshot = generateSnapshot(milestoneSolidifier, targetMilestone);

        if (transactionPruner != null) {
            cleanupExpiredSolidEntryPoints(tangle, snapshotProvider.getInitialSnapshot().getSolidEntryPoints(),
                newSnapshot.getSolidEntryPoints(), transactionPruner);
        }

        persistLocalSnapshot(snapshotProvider, newSnapshot);
        return newSnapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pruneSnapshotData(TransactionPruner transactionPruner, int pruningMilestoneIndex) throws SnapshotException {
        if (transactionPruner != null) {
            cleanupOldData(config, transactionPruner, pruningMilestoneIndex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Snapshot generateSnapshot(MilestoneSolidifier milestoneSolidifier, MilestoneViewModel targetMilestone)
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
                snapshot = snapshotProvider.getInitialSnapshot().clone();

                replayMilestones(snapshot, targetMilestone.index());
            } else {
                snapshot = snapshotProvider.getLatestSnapshot().clone();

                rollBackMilestones(snapshot, targetMilestone.index() + 1);
            }
        } finally {
            snapshotProvider.getInitialSnapshot().unlockRead();
            snapshotProvider.getLatestSnapshot().unlockRead();
        }

        snapshot.setSolidEntryPoints(generateSolidEntryPoints(targetMilestone));
        snapshot.setSeenMilestones(generateSeenMilestones(milestoneSolidifier, targetMilestone));

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
    public Map<Hash, Integer> generateSeenMilestones(MilestoneSolidifier milestoneSolidifier,
                                                     MilestoneViewModel targetMilestone) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [processing seen milestones]", log)
                .start(config.getLocalSnapshotsDepth());

        Map<Hash, Integer> seenMilestones = new HashMap<>();
        try {
            MilestoneViewModel seenMilestone = targetMilestone;
            while ((seenMilestone = MilestoneViewModel.findClosestNextMilestone(tangle, seenMilestone.index(),
                    milestoneSolidifier.getLatestMilestoneIndex())) != null) {

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
     * <p>
     * This method reverts the changes caused by the last milestone that was applied to this snapshot.
     * </p>
     * <p>
     * It first checks if we didn't arrive at the initial index yet and then reverts the balance changes that were
     * caused by the last milestone. Then it checks if any milestones were skipped while applying the last milestone and
     * determines the {@link SnapshotMetaData} that this Snapshot had before and restores it.
     * </p>
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
     * <p>
     * This method determines the milestone that shall be used for the local snapshot.
     * </p>
     * <p>
     * It determines the milestone by finding the closest previous milestone in the database
     * </p>
     * 
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param lowestIndex the determined index of the lowest milestone we can snapshot
     * @return the target milestone for the local snapshot
     * @throws SnapshotException if anything goes wrong while determining the target milestone for the local snapshot
     */
    private MilestoneViewModel determineMilestoneForLocalSnapshot(Tangle tangle, SnapshotProvider snapshotProvider,
            int lowestIndex) throws SnapshotException {
        MilestoneViewModel targetMilestone;
        try {
            targetMilestone = MilestoneViewModel.findClosestPrevMilestone(tangle, lowestIndex,
                    snapshotProvider.getInitialSnapshot().getIndex());
        } catch (Exception e) {
            throw new SnapshotException("could not load the target milestone", e);
        }
        if (targetMilestone == null) {
            throw new SnapshotException("missing milestone with an index of " + lowestIndex + " or lower");
        }

        return targetMilestone;
    }

    /**
     * <p>
     * This method creates {@link com.iota.iri.service.transactionpruning.TransactionPrunerJob}s for the expired solid
     * entry points, which removes the unconfirmed subtangles branching off of these transactions.
     * </p>
     * <p>
     * We only clean up these subtangles if the transaction that they are branching off has been cleaned up already by a
     * {@link MilestonePrunerJob}. If the corresponding milestone has not been processed we leave them in the database
     * so we give the node a little bit more time to "use" these transaction for references from future milestones. This
     * is used to correctly reflect the {@link SnapshotConfig#getLocalSnapshotsPruningDelay()}, where we keep old data
     * prior to a snapshot.
     * </p>
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
     * <p>
     * This method creates the {@link com.iota.iri.service.transactionpruning.TransactionPrunerJob}s that are
     * responsible for removing the old data.
     * </p>
     * <p>
     * It first calculates the range of milestones that shall be deleted and then issues a {@link MilestonePrunerJob}
     * for this range (if it is not empty).
     * </p>
     * 
     * @param config important snapshot related configuration parameters
     * @param transactionPruner  manager for the pruning jobs that takes care of cleaning up the old data that
     * @param targetIndex target milestone we use to prune anything older
     * @throws SnapshotException if anything goes wrong while issuing the cleanup jobs
     */
    private void cleanupOldData(SnapshotConfig config, TransactionPruner transactionPruner,
            int targetIndex) throws SnapshotException {
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
     * <p>
     * This method persists the local snapshot on the disk and updates the instances used by the
     * {@link SnapshotProvider}.
     * </p>
     * <p>
     * It first writes the files to the disk and then updates the two {@link Snapshot}s accordingly.
     * </p>
     * 
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param newSnapshot Snapshot that shall be persisted
     * @throws SnapshotException if anything goes wrong while persisting the snapshot
     */
    private void persistLocalSnapshot(SnapshotProvider snapshotProvider, Snapshot newSnapshot)
            throws SnapshotException {

        snapshotProvider.persistSnapshot(newSnapshot);

        snapshotProvider.getLatestSnapshot().lockWrite();
        snapshotProvider.getLatestSnapshot().setInitialHash(newSnapshot.getHash());
        snapshotProvider.getLatestSnapshot().setInitialIndex(newSnapshot.getIndex());
        snapshotProvider.getLatestSnapshot().setInitialTimestamp(newSnapshot.getTimestamp());
        snapshotProvider.getLatestSnapshot().unlockWrite();

        snapshotProvider.getInitialSnapshot().update(newSnapshot);
    }

    /**
     * <p>
     * This method determines if a transaction is orphaned when none of its approvers is confirmed by a milestone.
     * </p>
     * <p>
     * Since there is no hard definition for when a transaction can be considered to be orphaned, we define orphaned in
     * relation to a referenceTransaction. If the transaction or any of its direct or indirect approvers saw a
     * transaction being attached to it, that arrived after our reference transaction, we consider it "not orphaned".
     * </p>
     * <p>
     * Since we currently use milestones as reference transactions that are sufficiently old, this definition in fact is
     * a relatively safe way to determine if a subtangle "above" a transaction got orphaned.
     * </p>
     * 
     * @param tangle                Tangle object which acts as a database interface
     * @param transaction           transaction that shall be checked
     * @param referenceTransaction  transaction that acts as a judge to the other transaction
     * @param processedTransactions transactions that were visited already while trying to determine the orphaned status
     * @return true if the transaction got orphaned and false otherwise
     * @throws SnapshotException if anything goes wrong while determining the orphaned status
     */
    private boolean isProbablyOrphaned(Tangle tangle, TransactionViewModel transaction,
            TransactionViewModel referenceTransaction, Set<Hash> processedTransactions) throws SnapshotException {

        AtomicBoolean nonOrphanedTransactionFound = new AtomicBoolean(false);
        try {
            DAGHelper.get(tangle).traverseApprovers(
                    transaction.getHash(),
                    currentTransaction -> !nonOrphanedTransactionFound.get(),
                    currentTransaction -> {
                        if (currentTransaction.getArrivalTime() / 1000L > referenceTransaction.getTimestamp()) {
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
     * <p>
     * We determine whether future milestones will approve {@param transactionHash}. This should aid in determining
     * solid entry points.
     * </p>
     * <p>
     * To check if the transaction has non-orphaned approvers we first check if any of its approvers got confirmed by a
     * future milestone, since this is very cheap. If none of them got confirmed by another milestone we do the more
     * expensive check from {@link #isProbablyOrphaned(Tangle, TransactionViewModel, TransactionViewModel, Set)}.
     * </p>
     * <p>
     * Since solid entry points have a limited life time and to prevent potential problems due to temporary errors in
     * the database, we assume that the checked transaction is not orphaned if any error occurs while determining its
     * status, thus adding solid entry points. This is a storage <=> reliability trade off, since the only bad effect of
     * having too many solid entry points) is a bigger snapshot file.
     * </p>
     * 
     * @param tangle          Tangle object which acts as a database interface
     * @param transactionHash hash of the transaction that shall be checked
     * @param targetMilestone milestone that is used as an anchor for our checks
     * @return true if the transaction is a solid entry point and false otherwise
     */
    private boolean isNotOrphaned(Tangle tangle, Hash transactionHash, MilestoneViewModel targetMilestone) {
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
                if (!isProbablyOrphaned(tangle, unconfirmedApprover, milestoneTransaction, processedTransactions)) {
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
     * <p>
     * This method analyzes the old solid entry points and determines if they are still not orphaned.
     * </p>
     * <p>
     * It simply iterates through the old solid entry points and checks them one by one. If an old solid entry point is
     * found to still be relevant it is added to the passed in map.
     * </p>
     *
     * @see #processNewSolidEntryPoints to understand the definition for solid entry points
     * @param tangle           Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param targetMilestone  milestone that is used to generate the solid entry points
     * @param solidEntryPoints map that is used to collect the solid entry points
     */
    private void processOldSolidEntryPoints(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneViewModel targetMilestone, Map<Hash, Integer> solidEntryPoints) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [analyzing old solid entry points]", log)
                .start(snapshotProvider.getInitialSnapshot().getSolidEntryPoints().size());
        try {
            Snapshot initialSnapshot = snapshotProvider.getInitialSnapshot();
            Map<Hash, Integer> orgSolidEntryPoints = initialSnapshot.getSolidEntryPoints();
            for (Map.Entry<Hash, Integer> solidPoint : orgSolidEntryPoints.entrySet()) {
                Hash hash = solidPoint.getKey();
                int milestoneIndex = solidPoint.getValue();
                if (!Hash.NULL_HASH.equals(hash)
                        && targetMilestone.index() - milestoneIndex <= SOLID_ENTRY_POINT_LIFETIME
                        && isNotOrphaned(tangle, hash, targetMilestone)) {
                    TransactionViewModel tvm = TransactionViewModel.fromHash(tangle, hash);
                    addTailsToSolidEntryPoints(milestoneIndex, solidEntryPoints, tvm);
                    solidEntryPoints.put(hash, milestoneIndex);
                }

                progressLogger.progress();
            }
        } catch (Exception e) {
            throw new SnapshotException(
                    "Couldn't process old solid entry point for target milestone " + targetMilestone.index(), e);
        } finally {
            progressLogger.finish();
        }
    }

    /**
     * <p>
     * This method retrieves the new solid entry points of the snapshot reference given by the target milestone.
     * </p>
     * <p>
     * A transaction is considered a solid entry point if it is a bundle tail that can be traversed down from a
     * non-orphaned transaction that was approved by a milestone that is above the last local snapshot. Or if it is a
     * bundle tail of a non-orphaned transaction that was approved by a milestone that is above the last local snapshot.
     *
     * It iterates over all unprocessed milestones and analyzes their directly and indirectly approved transactions.
     * Every transaction is checked for being not orphaned and the appropriate SEP is added to {@param SolidEntryPoints}
     * </p>
     *
     *
     * @param tangle           Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param targetMilestone  milestone that is used to generate the solid entry points
     * @param solidEntryPoints map that is used to collect the solid entry points
     * @throws SnapshotException if anything goes wrong while determining the solid entry points
     * @see #isNotOrphaned(Tangle, Hash, MilestoneViewModel)
     */
    private void processNewSolidEntryPoints(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneViewModel targetMilestone, Map<Hash, Integer> solidEntryPoints) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [generating solid entry points]", log);

        try {
            progressLogger.start(Math.min(targetMilestone.index() - snapshotProvider.getInitialSnapshot().getIndex(),
                    OUTER_SHELL_SIZE));

            MilestoneViewModel nextMilestone = targetMilestone;
            while (nextMilestone != null && nextMilestone.index() > snapshotProvider.getInitialSnapshot().getIndex() &&
                    progressLogger.getCurrentStep() < progressLogger.getStepCount()) {

                MilestoneViewModel currentMilestone = nextMilestone;
                DAGHelper.get(tangle).traverseApprovees(
                        currentMilestone.getHash(),
                        currentTransaction -> currentTransaction.snapshotIndex() >= currentMilestone.index(),
                        currentTransaction -> {
                            if (isNotOrphaned(tangle, currentTransaction.getHash(), targetMilestone)) {
                                addTailsToSolidEntryPoints(targetMilestone.index(), solidEntryPoints,
                                        currentTransaction);
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

    private void addTailsToSolidEntryPoints(int milestoneIndex, Map<Hash, Integer> solidEntryPoints,
            TransactionViewModel currentTransaction) throws TraversalException {
        // if tail
        if (currentTransaction.getCurrentIndex() == 0) {
            solidEntryPoints.put(currentTransaction.getHash(), milestoneIndex);
        } else {
            Set<? extends Hash> tails = DAGHelper.get(tangle).findTails(currentTransaction);
            tails.forEach(tail -> solidEntryPoints.put(tail, milestoneIndex));
        }
    }
}
