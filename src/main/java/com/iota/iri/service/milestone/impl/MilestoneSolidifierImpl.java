package com.iota.iri.service.milestone.impl;

import com.iota.iri.conf.SolidificationConfig;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.milestone.MilestoneRepairer;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.milestone.MilestoneValidity;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.ASCIIProgressBar;
import com.iota.iri.utils.log.interval.IntervalLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MilestoneSolidifierImpl implements MilestoneSolidifier {
    
    private final static Logger log = LoggerFactory.getLogger(MilestoneSolidifierImpl.class);
    
    private static final IntervalLogger latestMilestoneLogger = new IntervalLogger(MilestoneSolidifierImpl.class);
    
    private static final IntervalLogger latestSolidMilestoneLogger = new IntervalLogger(MilestoneSolidifierImpl.class);
    
    private static final IntervalLogger solidifyLogger = new IntervalLogger(MilestoneSolidifierImpl.class);

    private static final IntervalLogger progressBarLogger = new IntervalLogger(MilestoneSolidifierImpl.class);
    
    
    // Max size fo the solidification queue
    private static final int MAX_SIZE = 10;

    private Map<Hash, Integer> unsolidMilestones = new ConcurrentHashMap<>();
    private Map<Hash, Integer> solidificationQueue = new ConcurrentHashMap<>();
    private Map<Integer, Hash> seenMilestones = new ConcurrentHashMap<>();

    private Map.Entry<Hash, Integer> oldestMilestoneInQueue = null;

    private AtomicInteger latestMilestoneIndex = new AtomicInteger(0);
    private AtomicReference<Hash> latestMilestoneHash = new AtomicReference<>(Hash.NULL_HASH);
    private AtomicInteger latestSolidMilestone = new AtomicInteger(0);

    private TransactionSolidifier transactionSolidifier;
    private LedgerService ledgerService;
    private SnapshotProvider snapshotProvider;
    private Tangle tangle;
    private TransactionRequester transactionRequester;
    private MilestoneService milestoneService;
    private MilestoneRepairer milestoneRepairer;
    private SolidificationConfig config;


    private SyncProgressInfo syncProgressInfo = new SyncProgressInfo();

    /**
     * An indicator for whether or not the solidifier has processed from the first batch of seen milestones
     */
    private AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * A thread to run the milestone solidification thread in
     */
    private Thread milestoneSolidifier = new Thread(this::milestoneSolidificationThread, "Milestone Solidifier");

    /**
     * Constructor for the {@link MilestoneSolidifierImpl}. This class holds milestone objects to be processed for
     * solidification. It also tracks the latest solid milestone object.
     *
     * @param transactionSolidifier     Solidification service for transaction objects
     * @param tangle                    DB reference
     * @param snapshotProvider          SnapshotProvider for fetching local snapshots
     * @param ledgerService             LedgerService for bootstrapping ledger state
     * @param txRequester               TransationRequester to submit missing transactions to
     * @param milestoneService          MilestoneService for resetting corrupted milestones
     * @param config                    Configuration to determine if additional logging will be used
     */
    public MilestoneSolidifierImpl(TransactionSolidifier transactionSolidifier, Tangle tangle,
                                   SnapshotProvider snapshotProvider, LedgerService ledgerService,
                                   TransactionRequester txRequester, MilestoneService milestoneService,
                                   SolidificationConfig config) {
        this.transactionSolidifier = transactionSolidifier;
        this.snapshotProvider = snapshotProvider;
        this.ledgerService = ledgerService;
        this.tangle = tangle;
        this.transactionRequester = txRequester;
        this.milestoneService = milestoneService;
        this.config = config;
        this.milestoneRepairer = new MilestoneRepairerImpl(milestoneService);
    }

    @Override
    public void start() {
        try {
            bootStrapSolidMilestones();
            ledgerService.restoreLedgerState();

            Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();
            setLatestMilestone(latestSnapshot.getHash(), latestSnapshot.getIndex());
            logChange(snapshotProvider.getInitialSnapshot().getIndex());

            syncProgressInfo.setSyncMilestoneStartIndex(snapshotProvider.getInitialSnapshot().getIndex());
            milestoneSolidifier.start();

        } catch (Exception e) {
            log.error("Error starting milestone solidification thread", e);
        }
    }

    private void milestoneSolidificationThread() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                processSolidifyQueue();
                checkLatestSolidMilestone();

                if (getLatestMilestoneIndex() > getLatestSolidMilestoneIndex()) {
                    solidifyLog();
                }

                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.info("Milestone Thread interrupted. Shutting Down.");
            } catch (Exception e) {
                log.error("Error running milestone solidification thread", e);
            }
        }
    }

    @Override
    public void shutdown(){
        milestoneSolidifier.interrupt();
    }

    /**
     * Scan through milestones in the {@link #unsolidMilestones} queue and refill the {@link #solidificationQueue} queue.
     * It then iterates through this queue to determine if the milestone has already been seen and validated. If it is
     * found in the {@link #seenMilestones} queue, then the milestone is removed from the solidification pool. If not,
     * then the {@link #oldestMilestoneInQueue} is updated iterating through whatever milestones are still present in
     * the {@link #solidificationQueue}.
     */
    private void scanMilestonesInQueue() {
        // refill the solidification queue with solidification candidates sorted by index
        if (unsolidMilestones.size() > 0) {
            solidificationQueue.clear();
            unsolidMilestones.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(milestone -> {
                        //If milestone candidate has a lower index than the latest solid milestone remove it from the queues.
                        if (milestone.getValue() < getLatestSolidMilestoneIndex()){
                            removeFromQueues(milestone.getKey());
                        } else {
                            if (solidificationQueue.size() < MAX_SIZE) {
                                solidificationQueue.put(milestone.getKey(), milestone.getValue());
                            }
                        }
                    });
        }

        // update the oldest milestone in queue. First reset oldestMilestoneInQueue before scanning queue.
        oldestMilestoneInQueue = null;
        for (Map.Entry<Hash, Integer> currentEntry : solidificationQueue.entrySet()) {
            if (!seenMilestones.containsKey(currentEntry.getValue())) {
                updateOldestMilestone(currentEntry.getKey(), currentEntry.getValue());
            }
        }
    }

    private void updateOldestMilestone(Hash milestoneHash, int milestoneIndex) {
        if (oldestMilestoneInQueue == null || oldestMilestoneInQueue.getValue() > milestoneIndex) {
            oldestMilestoneInQueue = new AbstractMap.SimpleEntry<>(milestoneHash, milestoneIndex);
        }
    }

    /**
     * Iterates through the {@link #solidificationQueue} to check for validity. Valid transactions are then submitted to
     * the {@link #seenMilestones} mapping if solid, and the {@link TransactionSolidifier} if not. Invalid transactions
     * are removed from the solidification queues.
     */
    private void processSolidifyQueue() throws Exception {
        Iterator<Map.Entry<Hash, Integer>> iterator = solidificationQueue.entrySet().iterator();
        Map.Entry<Hash, Integer> milestone;
        while(!Thread.currentThread().isInterrupted() && iterator.hasNext()) {
            milestone = iterator.next();
            Hash milestoneHash = milestone.getKey();
            int milestoneIndex = milestone.getValue();
            TransactionViewModel milestoneCandidate = TransactionViewModel.fromHash(tangle, milestoneHash);

            MilestoneValidity validity = milestoneService.validateMilestone(milestoneCandidate, milestoneIndex);
            switch(validity) {
                case VALID:
                    milestoneCandidate.isMilestone(tangle, snapshotProvider.getInitialSnapshot(), true);
                    registerNewMilestone(getLatestMilestoneIndex(), milestoneIndex, milestoneHash);
                    if (milestoneCandidate.isSolid()) {
                        removeFromQueues(milestoneHash);
                        addSeenMilestone(milestoneHash, milestoneIndex);
                    } else {
                        transactionSolidifier.addToSolidificationQueue(milestoneHash);
                    }
                    break;
                case INCOMPLETE:
                    transactionSolidifier.addToSolidificationQueue(milestoneHash);
                    break;
                case INVALID:
                    removeFromQueues(milestone.getKey());
            }
        }

        scanMilestonesInQueue();
    }

    /**
     * Tries to solidify the next available milestone index. If successful, the milestone will be removed from the
     * {@link #seenMilestones} queue, and any milestone objects below that index in the {@link #unsolidMilestones} queue
     * will be removed as well.
     */
    private void checkLatestSolidMilestone() {
        try {
            if (getLatestMilestoneIndex() > getLatestSolidMilestoneIndex()) {
                int nextMilestone = getLatestSolidMilestoneIndex() + 1;
                if (seenMilestones.containsKey(nextMilestone)) {
                    TransactionViewModel milestone = TransactionViewModel.fromHash(tangle,
                            seenMilestones.get(nextMilestone));
                    if (milestone.isSolid()) {
                        updateSolidMilestone(getLatestSolidMilestoneIndex());
                        transactionSolidifier.addToPropagationQueue(milestone.getHash());
                    } else {
                        transactionSolidifier.addToSolidificationQueue(milestone.getHash());
                    }
                }
            }
        } catch (Exception e) {
            log.info(e.getMessage());
        }
    }

    private void solidifyLog() {
        if (!unsolidMilestones.isEmpty() && oldestMilestoneInQueue != null) {
            int milestoneIndex = oldestMilestoneInQueue.getValue();
            solidifyLogger.info("Solidifying milestone # " + milestoneIndex + " - [ LSM: " + latestSolidMilestone +
                    " LM: " + latestMilestoneIndex + " ] - [ Remaining: " +
                    (getLatestMilestoneIndex() - getLatestSolidMilestoneIndex()) + " Queued: " +
                    (seenMilestones.size() + unsolidMilestones.size()) + " ]");
        }
    }

    /**
     * Sets the {@link #latestSolidMilestone} index to the snapshot index, and scans through all transactions that are
     * present in the db associated with the coordinator address. It then iterates through these transactions, adding
     * valid transactions to the {@link #seenMilestones} pool, and adding all valid and incomplete milestones to the
     * solidification queue. Once completed the {@link #initialized} flag is set to true.
     *
     * @return  Latest Solid Milestone index
     * @throws Exception
     */
    private void bootStrapSolidMilestones() throws Exception {
        setLatestSolidMilestone(snapshotProvider.getLatestSnapshot().getIndex());
        Set<Hash> milestoneTransactions = AddressViewModel.load(tangle, config.getCoordinator()).getHashes();
        int processed = 0;
        int index;
        for (Hash hash: milestoneTransactions) {
            try {
                processed += 1;
                TransactionViewModel tvm = TransactionViewModel.fromHash(tangle, hash);
                boolean isTail = tvm.getCurrentIndex() == 0;
                if (isTail && (index = milestoneService
                        .getMilestoneIndex(tvm)) > getLatestSolidMilestoneIndex()) {
                    MilestoneValidity validity = milestoneService.validateMilestone(tvm, index);
                    if (validity == MilestoneValidity.VALID) {
                        tvm.isMilestone(tangle, snapshotProvider.getInitialSnapshot(), true);
                        registerNewMilestone(getLatestMilestoneIndex(), index, tvm.getHash());
                    }

                    if (validity != MilestoneValidity.INVALID) {
                        addMilestoneCandidate(hash, index);
                    }
                }
                if (processed % 1000 == 0 || processed % milestoneTransactions.size() == 0){
                    log.info("Bootstrapping milestones: [ " + processed  + " / " + milestoneTransactions.size() + " ]");
                }
            } catch(Exception e) {
                log.error("Error processing existing milestone index", e);
            }
        }
        initialized.set(true);
    }



    private void updateSolidMilestone(int currentSolidMilestoneIndex) throws Exception {
        int nextMilestoneIndex = currentSolidMilestoneIndex + 1;
        MilestoneViewModel nextSolidMilestone = MilestoneViewModel.get(tangle, nextMilestoneIndex);
        if (nextSolidMilestone != null) {
            if (applySolidMilestoneToLedger(nextSolidMilestone)) {
                logChange(currentSolidMilestoneIndex);

                if (nextMilestoneIndex == getLatestMilestoneIndex()) {
                    transactionRequester.clearRecentlyRequestedTransactions();
                }

                removeCurrentAndLowerSeenMilestone(nextMilestoneIndex);
            }
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addMilestoneCandidate(Hash milestoneHash, int milestoneIndex) {
        if (!unsolidMilestones.containsKey(milestoneHash) && !seenMilestones.containsKey(milestoneIndex) &&
                milestoneIndex > getLatestSolidMilestoneIndex()) {
            unsolidMilestones.put(milestoneHash, milestoneIndex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSeenMilestone(Hash milestoneHash, int milestoneIndex) {
        if (!seenMilestones.containsKey(milestoneIndex)) {
            seenMilestones.put(milestoneIndex, milestoneHash);
        }
        removeFromQueues(milestoneHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Map.Entry<Hash, Integer>> getOldestMilestonesInQueue() {
        return new ArrayList<>(solidificationQueue.entrySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeFromQueues(Hash milestoneHash) {
        unsolidMilestones.remove(milestoneHash);
        solidificationQueue.remove(milestoneHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLatestMilestoneIndex() {
        return latestMilestoneIndex.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getLatestMilestoneHash() {
        return latestMilestoneHash.get();
    }

    private int getLatestSolidMilestoneIndex() {
        return latestSolidMilestone.get();
    }

    private void setLatestSolidMilestone(int milestoneIndex) {
        if (milestoneIndex > latestSolidMilestone.get()) {
            this.latestSolidMilestone.set(milestoneIndex);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setLatestMilestone(Hash milestoneHash, int milestoneIndex) {
        if (milestoneIndex > latestMilestoneIndex.get()) {
            this.latestMilestoneHash.set(milestoneHash);
            this.latestMilestoneIndex.set(milestoneIndex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInitialScanComplete() {
        return initialized.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerNewMilestone(int oldMilestoneIndex, int newMilestoneIndex, Hash newMilestoneHash) {
        if (newMilestoneIndex > oldMilestoneIndex) {
            setLatestMilestone(newMilestoneHash, newMilestoneIndex);
            tangle.publish("lmi %d %d", oldMilestoneIndex, newMilestoneIndex);
            latestMilestoneLogger.info("Latest milestone has changed from #" + oldMilestoneIndex + " to #" + newMilestoneIndex);
        }
    }

    /**
     * Remove milestone candidate from the {@link #seenMilestones} queue, then iterate through the
     * {@link #unsolidMilestones} queue and remove any milestones with an index below the provided index.
     *
     * @param solidMilestoneIndex   The milestone index to remove
     */
    private void removeCurrentAndLowerSeenMilestone(int solidMilestoneIndex) {
        seenMilestones.remove(solidMilestoneIndex);
        for (Iterator<Map.Entry<Hash, Integer>> iterator = unsolidMilestones.entrySet().iterator();
             !Thread.currentThread().isInterrupted() && iterator.hasNext();) {
            Map.Entry<Hash, Integer> nextMilestone = iterator.next();
            if (nextMilestone.getValue() <= solidMilestoneIndex ||
                    nextMilestone.getValue() < snapshotProvider.getLatestSnapshot().getInitialIndex()) {
                iterator.remove();
            }
        }
        scanMilestonesInQueue();
    }


    private boolean applySolidMilestoneToLedger(MilestoneViewModel milestone) throws Exception {
        if (ledgerService.applyMilestoneToLedger(milestone)) {
            if (milestoneRepairer.isRepairRunning() && milestoneRepairer.isRepairSuccessful(milestone)) {
                milestoneRepairer.stopRepair();
            }
            syncProgressInfo.addMilestoneApplicationTime();
            return true;
        } else {
            milestoneRepairer.repairCorruptedMilestone(milestone);
            return false;
        }
    }




    private void logChange(int prevSolidMilestoneIndex) {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();
        int nextLatestSolidMilestone = latestSnapshot.getIndex();
        setLatestSolidMilestone(nextLatestSolidMilestone);


        if (prevSolidMilestoneIndex == nextLatestSolidMilestone) {
            return;
        }

        latestSolidMilestoneLogger.info("Latest SOLID milestone index changed from #" + prevSolidMilestoneIndex + " to #" + nextLatestSolidMilestone);

        tangle.publish("lmsi %d %d", prevSolidMilestoneIndex, nextLatestSolidMilestone);
        tangle.publish("lmhs %s", latestMilestoneHash);

        if (!config.isPrintSyncProgressEnabled()) {
            return;
        }

        // only print more sophisticated progress if we are coming from a more unsynced state
        if (getLatestMilestoneIndex() - nextLatestSolidMilestone < 1) {
            syncProgressInfo.setSyncMilestoneStartIndex(nextLatestSolidMilestone);
            syncProgressInfo.resetMilestoneApplicationTimes();
            return;
        }

        int estSecondsToBeSynced = syncProgressInfo.computeEstimatedTimeToSyncUpSeconds(getLatestMilestoneIndex(),
                nextLatestSolidMilestone);
        StringBuilder progressSB = new StringBuilder();

        // add progress bar
        progressSB.append(ASCIIProgressBar.getProgressBarString(syncProgressInfo.getSyncMilestoneStartIndex(),
                getLatestMilestoneIndex(), nextLatestSolidMilestone));
        // add lsm to lm
        progressSB.append(String.format(" [LSM %d / LM %d - remaining: %d]", nextLatestSolidMilestone,
                getLatestMilestoneIndex(), getLatestMilestoneIndex() - nextLatestSolidMilestone));
        // add estimated time to get fully synced
        if (estSecondsToBeSynced != -1) {
            progressSB.append(String.format(" - est. seconds to get synced: %d", estSecondsToBeSynced));
        }
        progressBarLogger.info(progressSB.toString());
    }


    /**
     * Holds variables containing information needed for sync progress calculation.
     */
    private static class SyncProgressInfo {
        /**
         * The actual start milestone index from which the node started from when syncing up.
         */
        private int syncMilestoneStartIndex;

        /**
         * Used to calculate the average time needed to apply a milestone.
         */
        private BlockingQueue<Long> lastMilestoneApplyTimes = new ArrayBlockingQueue<>(100);

        /**
         * Gets the milestone sync start index.
         *
         * @return the milestone sync start index
         */
        int getSyncMilestoneStartIndex() {
            return syncMilestoneStartIndex;
        }

        /**
         * Sets the milestone sync start index.
         *
         * @param syncMilestoneStartIndex the value to set
         */
        void setSyncMilestoneStartIndex(int syncMilestoneStartIndex) {
            this.syncMilestoneStartIndex = syncMilestoneStartIndex;
        }

        /**
         * Adds the current time as a time where a milestone got applied to the ledger state.
         */
        void addMilestoneApplicationTime() {
            if (lastMilestoneApplyTimes.remainingCapacity() == 0) {
                lastMilestoneApplyTimes.remove();
            }
            lastMilestoneApplyTimes.add(System.currentTimeMillis());
        }

        /**
         * Clears the records of times when milestones got applied.
         */
        void resetMilestoneApplicationTimes() {
            lastMilestoneApplyTimes.clear();
        }

        /**
         * Computes the estimated time (seconds) needed to get synced up by looking at the last N milestone applications
         * times and the current solid and last milestone indices.
         *
         * @param latestMilestoneIndex      the current latest known milestone index
         * @param latestSolidMilestoneIndex the current to the ledger applied milestone index
         * @return the number of seconds needed to get synced up or -1 if not enough data is available
         */
        int computeEstimatedTimeToSyncUpSeconds(int latestMilestoneIndex, int latestSolidMilestoneIndex) {
            // compute average time needed to apply a milestone
            Object[] times = lastMilestoneApplyTimes.toArray();
            long sumDelta = 0;
            double avgMilestoneApplyMillisec;
            if (times.length > 1) {
                // compute delta sum
                for (int i = times.length - 1; i > 1; i--) {
                    sumDelta += ((Long)times[i]) - ((Long)times[i - 1]);
                }
                avgMilestoneApplyMillisec = (double) sumDelta / (double) (times.length - 1);
            } else {
                return -1;
            }

            return (int) ((avgMilestoneApplyMillisec / 1000) * (latestMilestoneIndex - latestSolidMilestoneIndex));
        }
    }

}
