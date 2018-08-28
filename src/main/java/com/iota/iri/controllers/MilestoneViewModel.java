package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.Milestone;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by paul on 4/11/17.
 */
public class MilestoneViewModel {
    /**
     * This value represents the maximum amount of milestone indexes that can be skipped by the coordinator.
     *
     * Note: This is in fact 1 already but, to be able to deal with databases before the adjustment we set this to 50.
     */
    private final static int MAX_MILESTONE_INDEX_GAP = 50;

    /**
     * This value represents the milestone index where the coordinator changed its behaviour and doesn't skip milestones
     * anymore.
     */
    private final static int MILESTONE_GAP_PATCH_INDEX = 650000;

    private final Milestone milestone;
    private static final Map<Integer, MilestoneViewModel> milestones = new ConcurrentHashMap<>();

    private MilestoneViewModel(final Milestone milestone) {
        this.milestone = milestone;
    }

    public static void clear() {
        milestones.clear();
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

    public static MilestoneViewModel findClosestPrevMilestone(Tangle tangle, int index) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.previous(Milestone.class, new IntegerIndex(index));
        if(milestonePair != null && milestonePair.hi != null) {
            return new MilestoneViewModel((Milestone) milestonePair.hi);
        }
        return null;
    }

    /**
     * This method looks for the next milestone after a given index.
     *
     * In contrast to the {@link #next} method we do not rely on the insertion order in the database but actively search
     * for the milestone that was issued next by the coordinator (coo-order preserved).
     *
     * @param tangle Tangle object which acts as a database interface
     * @param index milestone index where the search shall start
     * @return the milestone which follows directly after the given index or null if none was found
     * @throws Exception if anything goes wrong while loading entries from the database
     */
    public static MilestoneViewModel findClosestNextMilestone(Tangle tangle, int index) throws Exception {
        // adjust the max milestone gap according to the index (the coo ensures no gaps after a certain milestone index)
        int maxMilestoneGap = index >= MILESTONE_GAP_PATCH_INDEX ? 1 : MAX_MILESTONE_INDEX_GAP;

        // search for the next milestone following our index
        MilestoneViewModel nextMilestoneViewModel = null;
        int currentIndex = index;
        while(nextMilestoneViewModel == null && ++currentIndex <= index + maxMilestoneGap) {
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

}
