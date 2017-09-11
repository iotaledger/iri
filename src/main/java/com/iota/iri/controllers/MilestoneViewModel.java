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
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 4/11/17.
 */
public class MilestoneViewModel {
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

    public MilestoneViewModel nextWithSnapshot(Tangle tangle) throws Exception {
        MilestoneViewModel milestoneViewModel = next(tangle);
        while(milestoneViewModel !=null && !StateDiffViewModel.exists(tangle, milestoneViewModel.getHash())) {
            milestoneViewModel = milestoneViewModel.next(tangle);
        }
        return milestoneViewModel;
    }

    public static MilestoneViewModel firstWithSnapshot(Tangle tangle) throws Exception {
        MilestoneViewModel milestoneViewModel = first(tangle);
        while(milestoneViewModel !=null && !StateDiffViewModel.exists(tangle, milestoneViewModel.getHash())) {
            milestoneViewModel = milestoneViewModel.next(tangle);
        }
        return milestoneViewModel;
    }

    public static MilestoneViewModel findClosestPrevMilestone(Tangle tangle, int index) throws Exception {
        Pair<Indexable, Persistable> milestonePair = tangle.previous(Milestone.class, new IntegerIndex(index));
        if(milestonePair != null && milestonePair.hi != null) {
            return new MilestoneViewModel((Milestone) milestonePair.hi);
        }
        return null;
    }

    public static MilestoneViewModel findClosestNextMilestone(Tangle tangle, int index) throws Exception {
        if(index <= com.iota.iri.Milestone.MILESTONE_START_INDEX) {
            return first(tangle);
        }
        Pair<Indexable, Persistable> milestonePair = tangle.next(Milestone.class, new IntegerIndex(index));
        if(milestonePair != null && milestonePair.hi != null) {
            return new MilestoneViewModel((Milestone) milestonePair.hi);
        }
        return null;
    }

    public static MilestoneViewModel latestWithSnapshot(Tangle tangle) throws Exception {
        MilestoneViewModel milestoneViewModel = latest(tangle);
        while(milestoneViewModel !=null && !StateDiffViewModel.exists(tangle, milestoneViewModel.getHash())) {
            milestoneViewModel = milestoneViewModel.previous(tangle);
        }
        return milestoneViewModel;
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
