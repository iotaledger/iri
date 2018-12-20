package com.iota.iri.service.tipselection.impl;

import com.iota.iri.MilestoneTracker;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Tangle;

/**
 * Implementation of {@link EntryPointSelector} that given a depth {@code N}, returns a N-deep milestone.
 * Meaning <code>milestone(latestSolid - depth)</code>
 * Used as a starting point for the random walk.
 */
public class EntryPointSelectorImpl implements EntryPointSelector {

    private final Tangle tangle;
    private final MilestoneTracker milestoneTracker;

    /**
     * Constructor for Entry Point Selector
     * @param tangle Tangle object which acts as a database interface.
     * @param milestoneTracker  instance of the milestone tracker, used to get latest milestone.
     */
    public EntryPointSelectorImpl(Tangle tangle, MilestoneTracker milestoneTracker) {
        this.tangle = tangle;
        this.milestoneTracker = milestoneTracker;
    }

    @Override
    public Hash getEntryPoint(int depth) throws Exception {
        int milestoneIndex = Math.max(milestoneTracker.latestSolidSubtangleMilestoneIndex - depth - 1,
                milestoneTracker.getMilestoneStartIndex());
        MilestoneViewModel milestoneViewModel = MilestoneViewModel.findClosestNextMilestone(tangle, milestoneIndex,
                milestoneTracker.latestMilestoneIndex);
        if (milestoneViewModel != null && milestoneViewModel.getHash() != null) {
            return milestoneViewModel.getHash();
        }

        return milestoneTracker.latestSolidSubtangleMilestone;
    }
}
