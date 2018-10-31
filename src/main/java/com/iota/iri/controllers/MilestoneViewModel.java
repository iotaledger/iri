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

public class MilestoneViewModel {
    private final Milestone milestone;
    private static final Map<Integer, MilestoneViewModel> milestones = new ConcurrentHashMap<>();

    private MilestoneViewModel(final Milestone milestone) {
        this.milestone = milestone;
    }

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

    public MilestoneViewModel(final int index, final Hash milestoneHash) {
        this.milestone = new Milestone();
        this.milestone.index = new IntegerIndex(index);
        milestone.hash = milestoneHash;
    }

    public static MilestoneViewModel get(Tangle tangle, int index) throws Exception {
        MilestoneViewModel milestoneViewModel = milestones.get(index);
        if(milestoneViewModel == null && load(tangle, index)) {
            milestoneViewModel = milestones.get(index);
        }
        return milestoneViewModel;
    }

    public static boolean load(Tangle tangle, int index) throws Exception {
        Milestone milestone = (Milestone) tangle.load(Milestone.class, new IntegerIndex(index));
        if(milestone != null && milestone.hash != null) {
            milestones.put(index, new MilestoneViewModel(milestone));
            return true;
        }
        return false;
    }

    public static MilestoneViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.getFirst(Milestone.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel(milestone);
        }
        return null;
    }

    public static MilestoneViewModel latest(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.getLatest(Milestone.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel(milestone);
        }
        return null;
    }

    public MilestoneViewModel previous(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.previous(Milestone.class, this.milestone.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel((Milestone) milestone);
        }
        return null;
    }

    public MilestoneViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.next(Milestone.class, this.milestone.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel((Milestone) milestone);
        }
        return null;
    }

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

    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(milestone, milestone.index);
    }

    public Hash getHash() {
        return milestone.hash;
    }
    public Integer index() {
        return milestone.index.getValue();
    }

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
