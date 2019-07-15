package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acts as a controller interface for a {@link Milestone} hash object. This controller is used by the
 * {@link com.iota.iri.MilestoneTracker} to manipulate a {@link Milestone} object.
 */
public class MilestoneViewModel {
    private final Milestone milestone;
    private static final Map<Integer, MilestoneViewModel> milestones = new ConcurrentHashMap<>();

    private MilestoneViewModel(final Milestone milestone) {
        this.milestone = milestone;
    }

    /**
     * Removes the contents of the stored {@link Milestone} object set.
     */
    public static void clear() {
        milestones.clear();
    }

    /**
     * This method removes a {@link MilestoneViewModel} from the cache.
     *
     * It is used by the {@link com.iota.iri.service.transactionpruning.TransactionPruner} to remove milestones that
     * were deleted in the database, so that the runtime environment correctly reflects the database state.
     *
     * @param milestoneIndex the index of the milestone
     */
    public static void clear(int milestoneIndex) {
        milestones.remove(milestoneIndex);
    }

    /**
     * Constructor for a {@link Milestone} set controller. This controller is generated from a finalized
     * {@link Milestone} hash identifier, indexing this object to the integer {@link Milestone} index.
     *
     * @param index The finalized numerical index the {@link Milestone} object will be referenced by in the set
     * @param milestoneHash The finalized {@link Hash} identifier for the {@link Milestone} object
     */
    public MilestoneViewModel(final int index, final Hash milestoneHash) {
        this.milestone = new Milestone();
        this.milestone.index = new IntegerIndex(index);
        milestone.hash = milestoneHash;
    }

    /**
     * Fetches an existing {@link MilestoneViewModel} if its index reference can be found in the controller. If the
     * {@link MilestoneViewModel} is null, but the indexed {@link Milestone} object exists in the database, a new
     * controller is created for the {@link Milestone} object.
     *
     * @param tangle The tangle reference for the database
     * @param index The integer index of the {@link Milestone} object that the controller should be returned for
     * @return The {@link MilestoneViewModel} for the indexed {@link Milestone} object
     * @throws Exception Thrown if the database fails to load the indexed {@link Milestone} object
     */
    public static MilestoneViewModel get(Tangle tangle, int index) throws Exception {
        MilestoneViewModel milestoneViewModel = milestones.get(index);
        if(milestoneViewModel == null && load(tangle, index)) {
            milestoneViewModel = milestones.get(index);
        }
        return milestoneViewModel;
    }

    /**
     * Fetches a {@link Milestone} object from the database using its integer index. If the {@link Milestone} and the
     * associated {@link Hash} identifier are not null, a new {@link MilestoneViewModel} is created for the
     * {@link Milestone} object, and it is placed into the <tt>Milestones</tt> set, indexed by the provided integer
     * index.
     *
     * @param tangle The tangle reference for the database
     * @param index The integer index reference for the {@link Milestone} object
     * @return True if the {@link Milestone} object is stored in the <tt>Milestones</tt> set, False if not
     * @throws Exception Thrown if the database fails to load the {@link Milestone} object
     */
    public static boolean load(Tangle tangle, int index) throws Exception {
        Milestone milestone = (Milestone) tangle.load(Milestone.class, new IntegerIndex(index));
        if(milestone != null && milestone.hash != null) {
            milestones.put(index, new MilestoneViewModel(milestone));
            return true;
        }
        return false;
    }

    /**
     * Fetches the first persistable {@link Milestone} object from the database and generates a new
     * {@link MilestoneViewModel} from it. If no {@link Milestone} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link MilestoneViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static MilestoneViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.getFirst(Milestone.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel(milestone);
        }
        return null;
    }

    /**
     * Fetches the most recent persistable {@link Milestone} object from the database and generates a new
     * {@link MilestoneViewModel} from it. If no {@link Milestone} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link MilestoneViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static MilestoneViewModel latest(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.getLatest(Milestone.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel(milestone);
        }
        return null;
    }


    /**
     * Fetches the previously indexed persistable {@link Milestone} object from the database and generates a new
     * {@link MilestoneViewModel} from it. If no {@link Milestone} objects exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link MilestoneViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public MilestoneViewModel previous(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.previous(Milestone.class, this.milestone.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel((Milestone) milestone);
        }
        return null;
    }

    /**
     * Fetches the next indexed persistable {@link Milestone} object from the database and generates a new
     * {@link MilestoneViewModel} from it. If no {@link Milestone} objects exist in the database, it will return null.
     *
     * @param tangle The tangle reference for the database
     * @return The new {@link MilestoneViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public MilestoneViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.next(Milestone.class, this.milestone.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel((Milestone) milestone);
        }
        return null;
    }

    /**
     * Fetches a {@link MilestoneViewModel} for the closest {@link Milestone} object previously indexed in the
     * database. The method starts at the provided index and works backwards through the database to try and find a
     * {@link MilestoneViewModel} for the previous indexes until a non null controller is found.
     *
     * @param tangle The tangle reference for the database
     * @param index The beginning index the method will work backwards from
     * @param minIndex The minimum index that should be found in the database
     * @return The {@link MilestoneViewModel} of the closest found controller previously indexed in the database
     * @throws Exception Thrown if there is a failure to fetch a previous {@link MilestoneViewModel}
     */
    public static MilestoneViewModel findClosestPrevMilestone(Tangle tangle, int index, int minIndex) throws Exception {
        // search for the previous milestone preceding our index
        MilestoneViewModel previousMilestoneViewModel = null;
        int currentIndex = index;
        while(previousMilestoneViewModel == null && --currentIndex >= minIndex) {
            previousMilestoneViewModel = MilestoneViewModel.get(tangle, currentIndex);
        }

        return previousMilestoneViewModel;
    }

    /**
     * This method looks for the next milestone after a given index.
     *
     * In contrast to the {@link #next} method we do not rely on the insertion order in the database but actively search
     * for the milestone that was issued next by the coordinator (coo-order preserved).
     *
     * @param tangle Tangle object which acts as a database interface
     * @param index milestone index where the search shall start
     * @param maxIndex milestone index where the search shall stop
     * @return the milestone which follows directly after the given index or null if none was found
     * @throws Exception if anything goes wrong while loading entries from the database
     */
    public static MilestoneViewModel findClosestNextMilestone(Tangle tangle, int index, int maxIndex) throws Exception {
        // search for the next milestone following our index
        MilestoneViewModel nextMilestoneViewModel = null;
        int currentIndex = index;
        while(nextMilestoneViewModel == null && ++currentIndex <= maxIndex) {
            nextMilestoneViewModel = MilestoneViewModel.get(tangle, currentIndex);
        }

        return nextMilestoneViewModel;
    }

    /**
     * Save the {@link Milestone} object, indexed by its integer index, to the database.
     *
     * @param tangle The tangle reference for the database
     * @return True if the {@link Milestone} object is saved correctly, False if not
     * @throws Exception Thrown if there is an error while saving the {@link Milestone} object
     */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(milestone, milestone.index);
    }

    /**@return  The {@link Hash} identifier of the {@link Milestone} object*/
    public Hash getHash() {
        return milestone.hash;
    }

    /**@return The integer index of the {@link Milestone} object*/
    public Integer index() {
        return milestone.index.getValue();
    }

    /**
     * Removes the {@link Milestone} object from the database.
     *
     * @param tangle The tangle reference for the database
     * @throws Exception Thrown if there is an error removing the {@link Milestone} object
     */
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Milestone.class, milestone.index);
    }

    /**
     * This method creates a human readable string representation of the milestone.
     *
     * It can be used to directly append the milestone in error and debug messages.
     *
     * @return human readable string representation of the milestone
     */
    @Override
    public String toString() {
        return "milestone #" + index() + " (" + getHash().toString() + ")";
    }
}
