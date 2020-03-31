package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Tangle;

/**
 * Implementation of {@link EntryPointSelector} that given a depth {@code N}, returns a N-deep milestone.
 * Meaning <code>milestone(latestSolid - depth)</code>
 * Used as a starting point for the random walk.
 */
public class EntryPointSelectorImpl implements EntryPointSelector {

    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final MilestoneSolidifier milestoneSolidifier;

    /**
     * Constructor for Entry Point Selector
     * @param tangle Tangle object which acts as a database interface.
     * @param snapshotProvider accesses snapshots of the ledger state
     * @param milestoneSolidifier  used to get latest milestone.
     */
    public EntryPointSelectorImpl(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneSolidifier milestoneSolidifier) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.milestoneSolidifier = milestoneSolidifier;
    }

    @Override
    public Hash getEntryPoint(int depth) throws Exception {
        int milestoneIndex = Math.max(snapshotProvider.getLatestSnapshot().getIndex() - depth - 1,
                snapshotProvider.getInitialSnapshot().getIndex());
        MilestoneViewModel milestoneViewModel = MilestoneViewModel.findClosestNextMilestone(tangle, milestoneIndex,
                milestoneSolidifier.getLatestMilestoneIndex());
        if (milestoneViewModel != null && milestoneViewModel.getHash() != null) {
            return milestoneViewModel.getHash();
        }

        return snapshotProvider.getLatestSnapshot().getHash();
    }
}
