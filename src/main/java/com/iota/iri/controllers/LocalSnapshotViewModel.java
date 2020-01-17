package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.LocalSnapshot;
import com.iota.iri.storage.PersistenceProvider;

import java.util.Map;

/**
 * Acts as a controller interface for a {@link LocalSnapshot}.
 */
public class LocalSnapshotViewModel {

    private final static IntegerIndex LS_KEY = new IntegerIndex(1);
    private LocalSnapshot localSnapshot;

    /**
     * Creates a {@link LocalSnapshot} controller using the {@link LocalSnapshotViewModel#LS_KEY} identifier as a reference point.
     *
     * @param provider      The persistence provider from which to load the {@link LocalSnapshot} from
     * @return The new {@link LocalSnapshotViewModel}
     * @throws Exception Thrown if there is an error loading the {@link LocalSnapshot} from the database
     */
    public static LocalSnapshotViewModel load(PersistenceProvider provider) throws Exception {
        return new LocalSnapshotViewModel((LocalSnapshot) provider.get(LocalSnapshot.class, LS_KEY));
    }

    /**
     * Constructor for a {@link LocalSnapshot} controller using predefined local snapshot data.
     *
     * @param milestoneHash      the hash of the milestone from which the local snapshot was made
     * @param milestoneIndex     the index of the milestone
     * @param milestoneTimestamp the timestamp of the milestone
     * @param solidEntryPoints   the solid entry points for this local snapshot
     * @param seenMilestones     the seen milestones
     * @param ledgerState        the ledger state of the given milestone
     */
    public LocalSnapshotViewModel(Hash milestoneHash, int milestoneIndex, long milestoneTimestamp,
            Map<Hash, Integer> solidEntryPoints, Map<Hash, Integer> seenMilestones,
            Map<Hash, Long> ledgerState) {
        this.localSnapshot = new LocalSnapshot();
        this.localSnapshot.milestoneHash = milestoneHash;
        this.localSnapshot.milestoneIndex = milestoneIndex;
        this.localSnapshot.milestoneTimestamp = milestoneTimestamp;
        this.localSnapshot.solidEntryPoints = solidEntryPoints;
        this.localSnapshot.numSolidEntryPoints = solidEntryPoints.size();
        this.localSnapshot.seenMilestones = seenMilestones;
        this.localSnapshot.numSeenMilestones = seenMilestones.size();
        this.localSnapshot.ledgerState = ledgerState;
    }

    /**
     * Creates a finalized {@link LocalSnapshot} controller. The referenced {@link LocalSnapshot} of this controller
     * cannot be modified. If the provided {@link LocalSnapshot} is null, an empty
     * {@link LocalSnapshot} will be created.
     *
     * @param localSnapshot The finalized {@link LocalSnapshot} the controller will be made for
     */
    private LocalSnapshotViewModel(LocalSnapshot localSnapshot) {
        this.localSnapshot = localSnapshot == null || localSnapshot.ledgerState == null ? new LocalSnapshot()
                : localSnapshot;
    }

    /** @return True if the {@link LocalSnapshot} is empty, false if there is a variable present */
    public boolean isEmpty() {
        return localSnapshot == null || localSnapshot.ledgerState == null || localSnapshot.ledgerState.size() == 0;
    }

    /**
     * Saves the {@link LocalSnapshot} under the {@link LocalSnapshotViewModel#LS_KEY} identifier to the database.
     *
     * @param provider The persistence provider to use to save the {@link LocalSnapshot}
     * @return True if the {@link LocalSnapshot} was saved correctly, False if not
     * @throws Exception Thrown if there is an error while saving the {@link LocalSnapshot}
     */
    public boolean store(PersistenceProvider provider) throws Exception {
        return provider.save(localSnapshot, LS_KEY);
    }

    /**
     * Deletes the {@link LocalSnapshot} under the {@link LocalSnapshotViewModel#LS_KEY} identifier from the database.
     *
     * @param provider The persistence provider to use to delete the {@link LocalSnapshot}
     * @throws Exception Thrown if there is an error while removing the {@link LocalSnapshot}
     */
    public void delete(PersistenceProvider provider) throws Exception {
        provider.delete(LocalSnapshot.class, LS_KEY);
    }

}
