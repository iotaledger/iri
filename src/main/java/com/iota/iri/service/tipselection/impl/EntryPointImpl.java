package com.iota.iri.service.tipselection.impl;

import com.iota.iri.Milestone;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.EntryPoint;
import com.iota.iri.storage.Tangle;

public class EntryPointImpl implements EntryPoint {

    private final Tangle tangle;
    private final Milestone milestone;
    private final boolean testnet;
    private final int milestoneStartIndex;

    public EntryPointImpl(Tangle tangle, Milestone milestone, boolean testnet, int milestoneStartIndex) {
        this.tangle = tangle;
        this.milestone = milestone;

        //TODO - check if this can be removed
        this.testnet = testnet;
        this.milestoneStartIndex = milestoneStartIndex;
    }

    @Override
    public Hash getEntryPoint(int depth) throws Exception {
        int milestoneIndex = Math.max(milestone.latestSolidSubtangleMilestoneIndex - depth - 1, 0);
        MilestoneViewModel milestoneViewModel =
                MilestoneViewModel.findClosestNextMilestone(tangle, milestoneIndex, testnet, milestoneStartIndex);
        if (milestoneViewModel != null && milestoneViewModel.getHash() != null) {
            return milestoneViewModel.getHash();
        }

        return milestone.latestSolidSubtangleMilestone;
    }
}
