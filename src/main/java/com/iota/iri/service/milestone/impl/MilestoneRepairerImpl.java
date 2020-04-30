package com.iota.iri.service.milestone.impl;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.service.milestone.MilestoneException;
import com.iota.iri.service.milestone.MilestoneRepairer;
import com.iota.iri.service.milestone.MilestoneService;

/**
 * Creates a {@link MilestoneRepairer} service to fix corrupted milestone objects.
 */
public class MilestoneRepairerImpl implements MilestoneRepairer {

    /**
     * A {@link MilestoneService} instance for repairing corrupted milestones
     */
    private MilestoneService milestoneService;

    /**
     * Holds the milestone index of the milestone that caused the repair logic to get started.
     */
    private int errorCausingMilestoneIndex = Integer.MAX_VALUE;

    /**
     * Counter for the backoff repair strategy (see {@link #repairCorruptedMilestone(MilestoneViewModel)}.
     */
    private int repairBackoffCounter = 0;

    /**
     * Constructor for a {@link MilestoneRepairer} to be used for resetting corrupted milestone objects
     * @param milestoneService  A {@link MilestoneService} instance to reset corrupted mielstones
     */
    public MilestoneRepairerImpl(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * We simply use the {@link #repairBackoffCounter} as an indicator if a repair routine is running.
     * </p>
     */
    @Override
    public boolean isRepairRunning() {
        return repairBackoffCounter != 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRepairSuccessful(MilestoneViewModel processedMilestone) {
        return processedMilestone.index() > errorCausingMilestoneIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopRepair() {
        repairBackoffCounter = 0;
        errorCausingMilestoneIndex = Integer.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    public void repairCorruptedMilestone(MilestoneViewModel errorCausingMilestone) throws MilestoneException {
        if (repairBackoffCounter++ == 0) {
            errorCausingMilestoneIndex = errorCausingMilestone.index();
        }
        for (int i = errorCausingMilestone.index(); i > errorCausingMilestone.index() - repairBackoffCounter; i--) {
            milestoneService.resetCorruptedMilestone(i);
        }
    }
}
