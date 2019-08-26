package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.LocalSnapshot;
import com.iota.iri.storage.PersistenceProvider;

import java.util.Map;

/**
 * Acts as a controller interface for a {@link LocalSnapshot}.
 */
public class LocalSnapshotViewModel {

    private LocalSnapshot localSnapshot;
    private Hash milestoneHash;

    /**
     * Creates a {@link LocalSnapshot} controller using a {@link Hash} identifier as a reference point. A
     * {@link LocalSnapshot} is loaded from the database using the {@link Hash} reference, and the {@link Hash}
     * identifier is set as the controller reference as well.
     *
     * @param provider      The persistence provider from which to load the {@link LocalSnapshot} from
     * @param milestoneHash The {@link Hash} identifier of the {@link LocalSnapshot} the controller will be created for
     * @return The new {@link LocalSnapshotViewModel}
     * @throws Exception Thrown if there is an error loading the {@link LocalSnapshot} from the database
     */
    public static LocalSnapshotViewModel load(PersistenceProvider provider, Hash milestoneHash) throws Exception {
        return new LocalSnapshotViewModel((LocalSnapshot) provider.get(LocalSnapshot.class, milestoneHash),
                milestoneHash);
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
    public LocalSnapshotViewModel(final Hash milestoneHash, final int milestoneIndex, final long milestoneTimestamp,
            final Map<Hash, Integer> solidEntryPoints, final Map<Hash, Integer> seenMilestones,
            final Map<Hash, Long> ledgerState) {
        this.milestoneHash = milestoneHash;
        this.localSnapshot = new LocalSnapshot();
        this.localSnapshot.milestoneIndex = milestoneIndex;
        this.localSnapshot.milestoneTimestamp = milestoneTimestamp;
        this.localSnapshot.solidEntryPoints = solidEntryPoints;
        this.localSnapshot.numSolidEntryPoints = solidEntryPoints.size();
        this.localSnapshot.seenMilestones = seenMilestones;
        this.localSnapshot.numSeenMilestones = seenMilestones.size();
        this.localSnapshot.ledgerState = ledgerState;
    }

    /**
     * Constructor for a {@link LocalSnapshot} controller using a predefined milestone hash.
     * Usually used in conjunction with a deletion of a previously persisted local snapshot.
     *
     * @param milestoneHash      the hash of the milestone from which the local snapshot was made
     */
    public LocalSnapshotViewModel(final Hash milestoneHash) {
        this.milestoneHash = milestoneHash;
    }

    /**
     * This method checks the {@link com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider} to determine if an object
     * might exist in the database. If it definitively does not exist, it will return False
     *
     * @param provider      The persistence provider from which to check for the {@link LocalSnapshot} for
     * @param milestoneHash The {@link Hash} identifier of the object you are looking for
     * @return True if the key might exist in the database, False if it definitively does not
     * @throws Exception Thrown if there is an error checking the database
     */
    public static boolean maybeExists(PersistenceProvider provider, Hash milestoneHash) throws Exception {
        return provider.mayExist(LocalSnapshot.class, milestoneHash);
    }

    /**
     * Creates a finalized {@link LocalSnapshot} controller. The referenced {@link LocalSnapshot} of this controller and
     * its reference {@link Hash} identifier cannot be modified. If the provided {@link LocalSnapshot} is null, an empty
     * {@link LocalSnapshot} will be created.
     *
     * @param localSnapshot The finalized {@link LocalSnapshot} the controller will be made for
     * @param milestoneHash The finalized {@link Hash} identifier of the controller
     */
    private LocalSnapshotViewModel(final LocalSnapshot localSnapshot, final Hash milestoneHash) {
        this.milestoneHash = milestoneHash;
        this.localSnapshot = localSnapshot == null || localSnapshot.ledgerState == null ? new LocalSnapshot()
                : localSnapshot;
    }

    /** @return True if the {@link LocalSnapshot} is empty, False if there is a variable present */
    public boolean isEmpty() {
        return localSnapshot == null || localSnapshot.ledgerState == null || localSnapshot.ledgerState.size() == 0;
    }

    /** @return The {@link Hash} identifier of the {@link LocalSnapshot} controller */
    public Hash getMilestoneHash() {
        return milestoneHash;
    }

    /** @return The {@link LocalSnapshot} of the controller */
    public LocalSnapshot getLocalSnapshot() {
        return localSnapshot;
    }

    /**
     * Saves the {@link LocalSnapshot} and referencing {@link Hash} identifier to the database.
     *
     * @param provider The persistence provider to use to save the {@link LocalSnapshot}
     * @return True if the {@link LocalSnapshot} was saved correctly, False if not
     * @throws Exception Thrown if there is an error while saving the {@link LocalSnapshot}
     */
    public boolean store(PersistenceProvider provider) throws Exception {
        return provider.save(localSnapshot, milestoneHash);
    }

    /**
     * Deletes the {@link LocalSnapshot} and referencing {@link Hash} identifier from the database.
     *
     * @param provider The persistence provider to use to delete the {@link LocalSnapshot}
     * @throws Exception Thrown if there is an error while removing the {@link LocalSnapshot}
     */
    public void delete(PersistenceProvider provider) throws Exception {
        provider.delete(LocalSnapshot.class, milestoneHash);
    }

}
