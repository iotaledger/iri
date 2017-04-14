package com.iota.iri.service.viewModels;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Milestone;
import com.iota.iri.service.tangle.Tangle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 4/11/17.
 */
public class MilestoneViewModel {
    private final Milestone milestoneModel;
    private static final Map<Integer, MilestoneViewModel> milestones = new ConcurrentHashMap<>();

    private MilestoneViewModel(Milestone milestone) {
        this.milestoneModel = milestone;
    }

    public static void clear() {
        milestones.clear();
    }

    public MilestoneViewModel(int index, Hash milestoneHash) {
        this.milestoneModel = new Milestone();
        this.milestoneModel.index = index;
        this.milestoneModel.hash = milestoneHash;
    }

    public static MilestoneViewModel get(int index) throws ExecutionException, InterruptedException {
        MilestoneViewModel milestoneViewModel = milestones.get(index);
        if(milestoneViewModel == null && load(index)) {
            milestoneViewModel = milestones.get(index);
        }
        return milestoneViewModel;
    }

    public static boolean load(int index) throws ExecutionException, InterruptedException {
        Milestone milestone = new Milestone();
        milestone.index = index;
        if(Tangle.instance().load(milestone).get()) {
            milestones.put(index, new MilestoneViewModel(milestone));
            return true;
        }
        return false;
    }

    public static MilestoneViewModel first() throws ExecutionException, InterruptedException {
        Object msObj = Tangle.instance().getFirst(Milestone.class).get();
        if(msObj != null && msObj instanceof Milestone) {
            return new MilestoneViewModel((Milestone) msObj);
        }
        return null;
    }

    public static MilestoneViewModel latest() throws ExecutionException, InterruptedException {
        Object msObj = Tangle.instance().getLatest(Milestone.class).get();
        if(msObj != null && msObj instanceof Milestone) {
            return new MilestoneViewModel((Milestone) msObj);
        }
        return null;
    }

    public MilestoneViewModel previous() throws ExecutionException, InterruptedException {
        Object milestone = Tangle.instance().previous(Milestone.class, index()).get();
        if(milestone != null && milestone instanceof Milestone) {
            return new MilestoneViewModel((Milestone) milestone);
        }
        return null;
    }

    public MilestoneViewModel next() throws ExecutionException, InterruptedException {
        Object milestone = Tangle.instance().next(Milestone.class, index()).get();
        if(milestone != null && milestone instanceof Milestone) {
            return new MilestoneViewModel((Milestone) milestone);
        }
        return null;
    }

    public MilestoneViewModel nextWithSnapshot() throws ExecutionException, InterruptedException {
        MilestoneViewModel milestoneViewModel = next();
        while(milestoneViewModel !=null && milestoneViewModel.snapshot() == null) {
            milestoneViewModel = milestoneViewModel.next();
        }
        return milestoneViewModel;
    }

    public static MilestoneViewModel firstWithSnapshot() throws ExecutionException, InterruptedException {
        MilestoneViewModel milestoneViewModel = first();
        while(milestoneViewModel !=null && milestoneViewModel.snapshot() == null) {
            milestoneViewModel = milestoneViewModel.next();
        }
        return milestoneViewModel;
    }

    public static MilestoneViewModel latestWithSnapshot() throws ExecutionException, InterruptedException {
        MilestoneViewModel milestoneViewModel = latest();
        while(milestoneViewModel !=null && milestoneViewModel.snapshot() == null) {
            milestoneViewModel = milestoneViewModel.previous();
        }
        return milestoneViewModel;
    }

    public boolean store() throws ExecutionException, InterruptedException {
        return Tangle.instance().save(milestoneModel).get();
    }

    public Map<Hash, Long> snapshot() {
        return milestoneModel.snapshot;
    }

    public void initSnapshot(Map<Hash, Long> snapshot) {
        if(snapshot == null) {
            milestoneModel.snapshot = new HashMap<>();
        } else {
            milestoneModel.snapshot = new HashMap<>(snapshot);
        }
    }

    public void updateSnapshot() throws ExecutionException, InterruptedException {
        Tangle.instance().update(milestoneModel, "snapshot").get();
    }

    public Hash getHash() {
        return milestoneModel.hash;
    }
    public Integer index() {
        return milestoneModel.index;
    }

    public void delete() {
        Tangle.instance().delete(milestoneModel);
    }

}
