package com.iota.iri.service.milestone;

import com.iota.iri.controllers.MilestoneViewModel;

/**
 * Contains the logic for comparing and repairing corrupted milestones. Used by the
 * {@link com.iota.iri.service.milestone.MilestoneSolidifier} to forward transactions to the
 * {@link MilestoneService#resetCorruptedMilestone(int)} method.
 */
public interface MilestoneRepairer {
    /**
     * <p>
     * Checks if we are currently trying to repair a milestone.
     * </p>
     *
     * @return {@code true} if we are trying to repair a milestone and {@code false} otherwise
     */
    boolean isRepairRunning();

    /**
     * <p>
     * Checks if we successfully repaired the corrupted milestone.
     * </p>
     * <p>
     * To determine if the repair routine was successful we check if the processed milestone has a higher index than the
     * one that initially could not get applied to the ledger.
     * </p>
     *
     * @param processedMilestone the currently processed milestone
     * @return {@code true} if we advanced to a milestone following the corrupted one and {@code false} otherwise
     */
    boolean isRepairSuccessful(MilestoneViewModel processedMilestone);

    /**
     * <p>
     * Tries to actively repair the ledger by reverting the milestones preceding the given milestone.
     * </p>
     * <p>
     * It gets called when a milestone could not be applied to the ledger state because of problems like "inconsistent
     * balances". While this should theoretically never happen (because milestones are by definition "consistent"), it
     * can still happen because IRI crashed or got stopped in the middle of applying a milestone or if a milestone
     * was processed in the wrong order.
     * </p>
     * <p>
     * Every time we call this method the internal repairBackoffCounter is incremented which causes the next
     * call of this method to repair an additional milestone. This means that whenever we face an error we first try to
     * reset only the last milestone, then the two last milestones, then the three last milestones (and so on ...) until
     * the problem was fixed.
     * </p>
     * <p>
     * To be able to tell when the problem is fixed and the repairBackoffCounter can be reset, we store the
     * milestone index that caused the problem the first time we call this method.
     * </p>
     *
     * @param errorCausingMilestone the milestone that failed to be applied
     * @throws MilestoneException if we failed to reset the corrupted milestone
     */
    void repairCorruptedMilestone(MilestoneViewModel errorCausingMilestone) throws MilestoneException;

    /**
     * <p>
     * Resets the internal variables that are used to keep track of the repair process.
     * </p>
     * <p>
     * It gets called whenever we advance to a milestone that has a higher milestone index than the milestone that
     * initially caused the repair routine to kick in (see {@link #repairCorruptedMilestone(MilestoneViewModel)}.
     * </p>
     */
    void stopRepair();
}
