package com.iota.iri.service.snapshot.impl;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.LocalSnapshotService;
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

/**
 * Implements the basic contract of the {@link LocalSnapshotService}.
 */
public class LocalSnapshotServiceImpl implements LocalSnapshotService {
    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(LocalSnapshotServiceImpl.class);

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
     * {@inheritDoc}
     */
    @Override
    public void takeLocalSnapshot(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotConfig config,
            TransactionPruner transactionPruner) throws SnapshotException {

        int targetMilestoneIndex = snapshotProvider.getLatestSnapshot().getIndex() - config.getLocalSnapshotsDepth();

        MilestoneViewModel targetMilestone;
        try {
            targetMilestone = MilestoneViewModel.findClosestPrevMilestone(tangle, targetMilestoneIndex);
        } catch (Exception e) {
            throw new SnapshotException("could not load the target milestone", e);
        }
        if (targetMilestone == null) {
            throw new SnapshotException("missing milestone with an index of " + targetMilestoneIndex + " or lower");
        }

        Snapshot newSnapshot;
        try {
            newSnapshot = generateSnapshot(tangle, snapshotProvider, config, targetMilestone);

            Map<Hash, Integer> oldSolidEntryPoints = snapshotProvider.getInitialSnapshot().getSolidEntryPoints();
            Map<Hash, Integer> newSolidEntryPoints = newSnapshot.getSolidEntryPoints();

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
        } catch (Exception e) {
            throw new SnapshotException("could not generate the snapshot", e);
        }

        try {
            int targetIndex = targetMilestone.index() - config.getLocalSnapshotsPruningDelay();
            int startingIndex = config.getMilestoneStartIndex() + 1;

            if (targetIndex >= startingIndex) {
                transactionPruner.addJob(new MilestonePrunerJob(startingIndex, targetMilestone.index() -
                        config.getLocalSnapshotsPruningDelay()));
            }

        } catch (TransactionPruningException e) {
            throw new SnapshotException("could not add the cleanup job to the transaction pruner", e);
        }

        newSnapshot.writeToDisk(config.getLocalSnapshotsBasePath());

        snapshotProvider.getInitialSnapshot().lockWrite();
        snapshotProvider.getLatestSnapshot().lockWrite();

        snapshotProvider.getInitialSnapshot().update(newSnapshot);

        snapshotProvider.getLatestSnapshot().setInitialHash(snapshotProvider.getInitialSnapshot().getHash());
        snapshotProvider.getLatestSnapshot().setInitialIndex(snapshotProvider.getInitialSnapshot().getIndex());
        snapshotProvider.getLatestSnapshot().setInitialTimestamp(snapshotProvider.getInitialSnapshot().getTimestamp());

        snapshotProvider.getInitialSnapshot().unlockWrite();
        snapshotProvider.getLatestSnapshot().unlockWrite();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Snapshot generateSnapshot(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotConfig config,
            MilestoneViewModel targetMilestone) throws SnapshotException {

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
            int distanceFromInitialSnapshot = Math.abs(snapshotProvider.getInitialSnapshot().getIndex() - targetMilestone.index());
            int distanceFromLatestSnapshot = Math.abs(snapshotProvider.getLatestSnapshot().getIndex() - targetMilestone.index());

            if (distanceFromInitialSnapshot <= distanceFromLatestSnapshot) {
                snapshot = new SnapshotImpl(snapshotProvider.getInitialSnapshot());

                snapshot.replayMilestones(targetMilestone.index(), tangle);
            } else {
                snapshot = new SnapshotImpl(snapshotProvider.getLatestSnapshot());

                snapshot.rollBackMilestones(targetMilestone.index() + 1, tangle);
            }
        } finally {
            snapshotProvider.getInitialSnapshot().unlockRead();
            snapshotProvider.getLatestSnapshot().unlockRead();
        }

        snapshot.setSolidEntryPoints(generateSolidEntryPoints(tangle, snapshotProvider, targetMilestone));
        snapshot.setSeenMilestones(generateSeenMilestones(tangle, config, targetMilestone));

        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Hash, Integer> generateSolidEntryPoints(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneViewModel targetMilestone) throws SnapshotException {

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
    public Map<Hash, Integer> generateSeenMilestones(Tangle tangle, SnapshotConfig config,
            MilestoneViewModel targetMilestone) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [processing seen milestones]", log)
                .start(config.getLocalSnapshotsDepth());

        HashMap<Hash, Integer> seenMilestones = new HashMap<>();
        try {
            MilestoneViewModel seenMilestone = targetMilestone;
            while ((seenMilestone = MilestoneViewModel.findClosestNextMilestone(tangle, seenMilestone.index()))
                    != null) {

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

        snapshotProvider.getInitialSnapshot().getSolidEntryPoints().forEach((hash, milestoneIndex) -> {
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

                nextMilestone = MilestoneViewModel.findClosestPrevMilestone(tangle, currentMilestone.index());

                progressLogger.progress();
            }

            progressLogger.finish();
        } catch (Exception e) {
            progressLogger.abort(e);

            throw new SnapshotException("could not generate the solid entry points for " + targetMilestone, e);
        }
    }
}
