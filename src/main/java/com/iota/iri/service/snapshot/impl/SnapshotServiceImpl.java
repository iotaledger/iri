package com.iota.iri.service.snapshot.impl;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.StateDiffViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.*;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.service.transactionpruning.jobs.MilestonePrunerJob;
import com.iota.iri.service.transactionpruning.jobs.UnconfirmedSubtanglePrunerJob;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.dag.DAGHelper;
import com.iota.iri.utils.dag.TraversalException;
import com.iota.iri.utils.log.ProgressLogger;
import com.iota.iri.utils.log.interval.IntervalProgressLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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
    private final IotaConfig config;

    /**
     * Implements the snapshot service. See interface for more information.
     * @param tangle acts as a database interface.
     * @param snapshotProvider gives us access to the relevant snapshots.
     * @param config configuration with snapshot specific settings.
     */
    public SnapshotServiceImpl(Tangle tangle, SnapshotProvider snapshotProvider, IotaConfig config) {
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
    public void takeLocalSnapshot(LatestMilestoneTracker latestMilestoneTracker, TransactionPruner transactionPruner)
            throws SnapshotException {

        MilestoneViewModel targetMilestone = determineMilestoneForLocalSnapshot(tangle, snapshotProvider, config);

        Snapshot newSnapshot = generateSnapshot(latestMilestoneTracker, targetMilestone);

        if (transactionPruner != null) {
            cleanupExpiredSolidEntryPoints(tangle, snapshotProvider.getInitialSnapshot().getSolidEntryPoints(),
                    newSnapshot.getSolidEntryPoints(), transactionPruner);

            cleanupOldData(config, transactionPruner, targetMilestone);
        }

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
     * It determines the milestone by subtracting the {@link SnapshotConfig#getLocalSnapshotsDepth()} from the latest
     * solid milestone index and retrieving the next milestone before this point.
     * </p>
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
     * @param config important snapshot related configuration parameters
     * @throws SnapshotException if anything goes wrong while persisting the snapshot
     */
    private void persistLocalSnapshot(SnapshotProvider snapshotProvider, Snapshot newSnapshot, SnapshotConfig config)
            throws SnapshotException {

        snapshotProvider.writeSnapshotToDisk(newSnapshot, config.getLocalSnapshotsBasePath());

        snapshotProvider.getLatestSnapshot().lockWrite();
        snapshotProvider.getLatestSnapshot().setInitialHash(newSnapshot.getHash());
        snapshotProvider.getLatestSnapshot().setInitialIndex(newSnapshot.getIndex());
        snapshotProvider.getLatestSnapshot().setInitialTimestamp(newSnapshot.getTimestamp());
        snapshotProvider.getLatestSnapshot().unlockWrite();

        snapshotProvider.getInitialSnapshot().update(newSnapshot);
    }
    
    private boolean isAboveMinMilestone(int fromIndex, int toIndex) {
        return toIndex - fromIndex <= config.getMaxDepth();
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
                if (!Hash.NULL_HASH.equals(hash) && isAboveMinMilestone(milestoneIndex, targetMilestone.index())) {
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
     * @see #isAboveMinMilestone(Tangle, Hash, MilestoneViewModel)
     */
    private void processNewSolidEntryPoints(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneViewModel targetMilestone, Map<Hash, Integer> solidEntryPoints) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [generating solid entry points]", log);
        try {
            Map<Hash, Integer> entryPoints = getSolidEntryPoints(targetMilestone.index(), progressLogger);
            
            // Only is null when we are shutting down / Thread interrupted
            if (entryPoints == null) {
                throw new SnapshotException("No solid entrypoint generation due to thread interruption");
            }
            
            solidEntryPoints.putAll(entryPoints);
            progressLogger.finish();
        } catch (Exception e) {
            progressLogger.abort(e);
            throw new SnapshotException("could not generate the solid entry points for " + targetMilestone, e);
        }
    }
    
    /**
     * Generates entrypoints based on target index down to the maximum depth of the node
     * 
     * @param targetIndex The milestone index we target to generate entrypoints until.
     * @param progressLogger The logger we use to write progress of entrypoint generation
     * @return a map of entrypoints or <code>null</code> when we were interrupted
     * @throws ExceptionWhen we fail to get entry points due to errors generally caused by db interaction
     */
    private Map<Hash, Integer> getSolidEntryPoints(int targetIndex, ProgressLogger progressLogger) throws Exception {
        Map<Hash, Integer> solidEntryPoints = new HashMap<>();
        solidEntryPoints.put(Hash.NULL_HASH, targetIndex);
        
        progressLogger.start(Math.min(targetIndex - snapshotProvider.getInitialSnapshot().getIndex(),
                config.getMaxDepth()));
        
        // Iterate from a reasonable old milestone to the target index to check for solid entry points
        for (int milestoneIndex = targetIndex - config.getMaxDepth(); milestoneIndex <= targetIndex; milestoneIndex++) {
            if (Thread.interrupted()) {
                return null;
            }
            
            MilestoneViewModel milestone = MilestoneViewModel.get(tangle, milestoneIndex);
            if (milestone == null) {
                log.warn("Failed to find milestone {} during entry point analyzation", milestoneIndex);
                return null;
            }
            
            List<Hash> approvees = getMilestoneApprovees(milestoneIndex, milestone);
            for (Hash approvee : approvees) {
                if (Thread.interrupted()) {
                    return null;
                }
                
                if (isSolidEntryPoint(approvee, targetIndex)) {
                    // A solid entry point should only be a tail transaction, otherwise the whole bundle can't be reproduced with a snapshot file
                    TransactionViewModel tvm = TransactionViewModel.fromHash(tangle, approvee);
                    addTailsToSolidEntryPoints(milestoneIndex, solidEntryPoints, tvm);
                }
            }
            progressLogger.progress();
        }
        
        return solidEntryPoints;
    }

    /**
     * isSolidEntryPoint checks whether any direct approver of the given transaction was confirmed 
     * by a milestone which is above the target milestone.
     * 
     * @param txHash The hash we check as an entrypoint
     * @param targetIndex
     * @return if the transaction is considered a solid entrypoint
     * @throws Exception on db error
     */
    private boolean isSolidEntryPoint(Hash txHash, int targetIndex) throws Exception {
        ApproveeViewModel approvers = ApproveeViewModel.load(tangle, txHash);
        if (approvers.getHashes().isEmpty()) {
            return false;
        }
        
        for (Hash approver : approvers.getHashes()) {
            TransactionViewModel tvm = TransactionViewModel.fromHash(tangle, approver);
            if (tvm != null && tvm.snapshotIndex() > targetIndex) {
                // confirmed by a later milestone than targetIndex => solidEntryPoint
                return true;
            }
        }
        
        return false;
    }

    /**
     * getMilestoneApprovees traverses a milestone and collects all tx that were 
     * confirmed by that milestone or higher
     * 
     * @param milestoneIndex
     * @param milestone
     * @return
     * @throws TraversalException
     */
    private  List<Hash> getMilestoneApprovees(int milestoneIndex, MilestoneViewModel milestone) throws TraversalException {
        List<Hash> approvees = new LinkedList<>();
        DAGHelper.get(tangle).traverseApprovees(milestone.getHash(),
                currentTransaction -> currentTransaction.snapshotIndex() == milestoneIndex,
                currentTransaction -> {
                    approvees.add(currentTransaction.getHash());
                });
        return approvees;
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
