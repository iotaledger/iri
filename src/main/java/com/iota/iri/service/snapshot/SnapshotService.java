package com.iota.iri.service.snapshot;

import java.util.Map;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.transactionpruning.TransactionPruner;

/**
 * <p>
 * Represents the service for snapshots that contains the relevant business logic for modifying {@link Snapshot}s and
 * generating new local {@link Snapshot}s.
 * </p>
 * This class is stateless and does not hold any domain specific models.
 */
public interface SnapshotService {
    /**
     * </p>
     * This method applies the balance changes that are introduced by future milestones to the current Snapshot.
     * </p>
     * <p>
     * It iterates over the milestone indexes starting from the current index to the target index and applies all found
     * milestone balances. If it can not find a milestone for a certain index it keeps track of these skipped
     * milestones, which allows us to revert the changes even if the missing milestone was received and processed in the
     * mean time. If the application of changes fails, we restore the state of the snapshot to the one it had before the
     * application attempt so this method only modifies the Snapshot if it succeeds.
     * </p>
     * <p>
     * Note: the changes done by this method can be reverted by using {@link #rollBackMilestones(Snapshot, int)}.
     * </p>
     * 
     * @param snapshot the Snapshot that shall get modified
     * @param targetMilestoneIndex the index of the milestone that should be applied
     * @throws SnapshotException if something goes wrong while applying the changes
     */
    void replayMilestones(Snapshot snapshot, int targetMilestoneIndex) throws SnapshotException;

    /**
     * <p>
     * This method rolls back the latest milestones until it reaches the state that the snapshot had before applying
     * the milestone indicated by the given parameter.
     * </p>
     * <p>
     * When rolling back the milestones we take previously skipped milestones into account, so this method should give
     * the correct result, even if the missing milestones were received and processed in the mean time. If the rollback
     * fails, we restore the state of the snapshot to the one it had before the rollback attempt so this method only
     * modifies the Snapshot if it succeeds.
     * </p>
     * <p>
     * Note: this method is used to reverse the changes introduced by {@link #replayMilestones(Snapshot, int)}
     * </p>
     * @param snapshot the Snapshot that shall get modified
     * @param targetMilestoneIndex the index of the milestone that should be rolled back (including all following
     *                             milestones that were applied)
     * @throws SnapshotException if something goes wrong while reverting the changes
     */
    void rollBackMilestones(Snapshot snapshot, int targetMilestoneIndex) throws SnapshotException;

    /**
     * <p>
     * This method takes a "full" local snapshot according to the configuration of the node.
     * </p>
     * <p>
     * It first determines the necessary configuration parameters and which milestone to use as a reference. It then
     * generates the local {@link Snapshot}, issues the the required {@link TransactionPruner} jobs and writes the
     * resulting {@link Snapshot} to the disk.
     * </p>
     * <p>
     * After persisting the local snapshot on the hard disk of the node, it updates the {@link Snapshot} instances used
     * by the {@code snapshotProvider} to reflect the newly created {@link Snapshot}.
     * </p>
     * 
     * @param milestoneSolidifier milestone tracker that allows us to retrieve information about the known milestones
     * @param transactionPruner manager for the pruning jobs that takes care of cleaning up the old data that
     * @param snapshotUntillIndex The last milestone we keep, everything before gets snapshotted. 
     *                            If we can't find the milestone of this index, we attempt to look back further until we do
     * @return The Snapshot we ended up making
     * @throws SnapshotException if anything goes wrong while creating the local snapshot
     */
    Snapshot takeLocalSnapshot(MilestoneSolidifier milestoneSolidifier, TransactionPruner transactionPruner,
                           int snapshotUntillIndex) throws SnapshotException;

    /**
     * <p>
     * This method is called when the node is cleaning up data. 
     * The newSnapshot is obtained from taking a successful local snapshot.
     * No data should be pruned if pruning is disabled on this node.
     * <p>
     * 
     * @param transactionPruner manager for the pruning jobs that takes care of cleaning up the old data that
     * @param pruningMilestoneIndex The index of the milestone we will prune until (excluding)
     * @throws SnapshotException if anything goes wrong while pruning
     */
    void pruneSnapshotData(TransactionPruner transactionPruner, int pruningMilestoneIndex)
            throws SnapshotException;
    
    /**
     * <p>
     * This method generates a local snapshot of the full ledger state at the given milestone.
     * </p>
     * <p>
     * The generated {@link Snapshot} contains the balances and meta data plus the derived values like the solid entry
     * points and all seen milestones, that were issued after the snapshot and can therefore be used to generate the
     * local snapshot files.
     * </p>
     * 
     * @param milestoneSolidifier milestone tracker that allows us to retrieve information about the known milestones
     * @param targetMilestone milestone that is used as a reference point for the snapshot
     * @return a local snapshot of the full ledger state at the given milestone
     * @throws SnapshotException if anything goes wrong while generating the local snapshot
     */
    Snapshot generateSnapshot(MilestoneSolidifier milestoneSolidifier, MilestoneViewModel targetMilestone) throws
            SnapshotException;

    /**
     * <p>
     * This method generates the solid entry points for a snapshot that belong to the given milestone.
     * </p>
     * <p>
     * A solid entry point is a confirmed transaction that had non-orphaned approvers during the time of the snapshot
     * creation and therefore a connection to the most recent part of the tangle. The solid entry points allow us to
     * stop the solidification process without having to go all the way back to the genesis.
     * </p>
     * 
     * @param targetMilestone milestone that is used as a reference point for the snapshot
     * @return a map of solid entry points associating their hash to the milestone index that confirmed them
     * @throws SnapshotException if anything goes wrong while generating the solid entry points
     */
    Map<Hash, Integer> generateSolidEntryPoints(MilestoneViewModel targetMilestone) throws SnapshotException;

    /**
     * <p>
     * This method generates the map of seen milestones that happened after the given target milestone.
     * </p>
     * <p>
     * The map contains the hashes of the milestones associated to their milestone index and is used to allow nodes
     * that use local snapshot files to bootstrap their nodes, to faster request the missing milestones when syncing the
     * very first time.
     * </p>
     * 
     * @param milestoneSolidifier milestone tracker that allows us to retrieve information about the known milestones
     * @param targetMilestone milestone that is used as a reference point for the snapshot
     * @return a map of solid entry points associating their hash to the milestone index that confirmed them
     * @throws SnapshotException if anything goes wrong while generating the solid entry points
     */
    Map<Hash, Integer> generateSeenMilestones(MilestoneSolidifier milestoneSolidifier,
                                              MilestoneViewModel targetMilestone) throws SnapshotException;
}
