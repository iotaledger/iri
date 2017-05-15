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

    public static MilestoneViewModel get(int index) throws Exception {
        MilestoneViewModel milestoneViewModel = milestones.get(index);
        if(milestoneViewModel == null && load(index)) {
            milestoneViewModel = milestones.get(index);
        }
        return milestoneViewModel;
    }

    public static boolean load(int index) throws Exception {
        Milestone milestone = (Milestone) Tangle.instance().load(Milestone.class, new IntegerIndex(index));
        if(milestone != null && milestone.hash != null) {
            milestones.put(index, new MilestoneViewModel(milestone));
            return true;
        }
        return false;
    }

    public static MilestoneViewModel first() throws Exception {
        Pair<Indexable, Persistable> milestonePair = Tangle.instance().getFirst(Milestone.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel(milestone);
        }
        return null;
    }

    public static MilestoneViewModel latest() throws Exception {
        Pair<Indexable, Persistable> milestonePair = Tangle.instance().getLatest(Milestone.class, IntegerIndex.class);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel(milestone);
        }
        return null;
    }

    public MilestoneViewModel previous() throws Exception {
        Pair<Indexable, Persistable> milestonePair = Tangle.instance().previous(Milestone.class, this.milestone.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel((Milestone) milestone);
        }
        return null;
    }

    public MilestoneViewModel next() throws Exception {
        Pair<Indexable, Persistable> milestonePair = Tangle.instance().next(Milestone.class, this.milestone.index);
        if(milestonePair != null && milestonePair.hi != null) {
            Milestone milestone = (Milestone) milestonePair.hi;
            return new MilestoneViewModel((Milestone) milestone);
        }
        return null;
    }

    public MilestoneViewModel nextWithSnapshot() throws Exception {
        MilestoneViewModel milestoneViewModel = next();
        while(milestoneViewModel !=null && !StateDiffViewModel.exists(milestoneViewModel.getHash())) {
            milestoneViewModel = milestoneViewModel.next();
        }
        return milestoneViewModel;
    }

    public static MilestoneViewModel firstWithSnapshot() throws Exception {
        MilestoneViewModel milestoneViewModel = first();
        while(milestoneViewModel !=null && !StateDiffViewModel.exists(milestoneViewModel.getHash())) {
            milestoneViewModel = milestoneViewModel.next();
        }
        return milestoneViewModel;
    }

    public static MilestoneViewModel findClosestPrevMilestone(int index) throws Exception {
        Pair<Indexable, Persistable> milestonePair = Tangle.instance().previous(Milestone.class, new IntegerIndex(index));
        if(milestonePair != null && milestonePair.hi != null) {
            return new MilestoneViewModel((Milestone) milestonePair.hi);
        }
        return null;
    }

    public static MilestoneViewModel findClosestNextMilestone(int index) throws Exception {
        if(index <= 0) {
            return first();
        }
        Pair<Indexable, Persistable> milestonePair = Tangle.instance().next(Milestone.class, new IntegerIndex(index));
        if(milestonePair != null && milestonePair.hi != null) {
            return new MilestoneViewModel((Milestone) milestonePair.hi);
        }
        return null;
    }

    public static MilestoneViewModel latestWithSnapshot() throws Exception {
        MilestoneViewModel milestoneViewModel = latest();
        while(milestoneViewModel !=null && !StateDiffViewModel.exists(milestoneViewModel.getHash())) {
            milestoneViewModel = milestoneViewModel.previous();
        }
        return milestoneViewModel;
    }

    public boolean store() throws Exception {
        return Tangle.instance().save(milestone, milestone.index);
    }

    public Hash getHash() {
        return milestone.hash;
    }
    public Integer index() {
        return milestone.index.getValue();
    }

    public void delete() throws Exception {
        Tangle.instance().delete(Milestone.class, milestone.index);
    }

}
