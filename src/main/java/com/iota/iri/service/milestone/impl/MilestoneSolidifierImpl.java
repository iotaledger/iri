package com.iota.iri.service.milestone.impl;

import com.iota.iri.conf.SolidificationConfig;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.milestone.MilestoneException;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.milestone.MilestoneValidity;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.ASCIIProgressBar;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

public class MilestoneSolidifierImpl implements MilestoneSolidifier {
    private static final IntervalLogger log = new IntervalLogger(MilestoneSolidifierImpl.class);
    // Max size fo the solidification queue
    private static final int MAX_SIZE = 10;

    private Map<Hash, Integer> unsolidMilestones = new ConcurrentHashMap<>();
    private Map<Hash, Integer> solidificationQueue = new ConcurrentHashMap<>();
    private Map<Integer, Hash> seenMilestones = new ConcurrentHashMap<>();

    private Map.Entry<Hash, Integer> oldestMilestoneInQueue = null;

    private int latestMilestoneIndex;
    private Hash latestMilestoneHash;
    private int latestSolidMilestone;

    private TransactionSolidifier transactionSolidifier;
    private LedgerService ledgerService;
    private SnapshotProvider snapshotProvider;
    private Tangle tangle;
    private TransactionRequester transactionRequester;
    private MilestoneService milestoneService;
    private SolidificationConfig config;


    private SyncProgressInfo syncProgressInfo = new SyncProgressInfo();

    /**
     * Holds the milestone index of the milestone that caused the repair logic to get started.
     */
    private int errorCausingMilestoneIndex = Integer.MAX_VALUE;

    /**
     * Counter for the backoff repair strategy (see {@link #repairCorruptedMilestone(MilestoneViewModel)}.
     */
    private int repairBackoffCounter = 0;

    /**
     * An indicator for whether or not the solidifier has processed from the first batch of seen milestones
     */
    private boolean initialized = false;

    /**
     * The execution service for the milestone solidifier thread
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Milestone Solidifier");

    private boolean firstRun = true;


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
                                   SolidificationConfig config){
        this.transactionSolidifier = transactionSolidifier;
        this.snapshotProvider = snapshotProvider;
        this.ledgerService = ledgerService;
        this.tangle = tangle;
        this.transactionRequester = txRequester;
        this.milestoneService = milestoneService;
        this.config = config;
    }

    @Override
    public void start(){
        executorService.silentExecute(this::milestoneSolidificationThread);
    }

    @Override
    public void shutdown(){
        executorService.shutdownNow();
    }


    private void milestoneSolidificationThread() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                if (firstRun) {
                    firstRun = false;
                    bootStrapSolidMilestones();
                    ledgerService.restoreLedgerState();

                    Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();
                    setLatestMilestone(latestSnapshot.getHash(), latestSnapshot.getIndex());
                    logChange(snapshotProvider.getInitialSnapshot().getIndex());

                    syncProgressInfo.setSyncMilestoneStartIndex(snapshotProvider.getInitialSnapshot().getIndex());
                }

                scanMilestonesInQueue();
                solidifySeenMilestones();
                processSolidifyQueue();
                checkLatestSolidMilestone();

                if(latestMilestoneIndex > latestSolidMilestone) {
                    solidifyLog();
                }
            } catch(Exception e){
                log.error("Error running milestone solidification thread", e);
            }
        }
    }

    /**
     * Scan through milestones in the {@link #unsolidMilestones} queue and remove any that are present in the
     * {@link #seenMilestones} queue. If it isn't in the {@link #seenMilestones} queue, then the youngest and oldest
     * milestone objects are updated with that milestone object. Used to reset the oldest milestones for solidification.
     * If upon scanning the {@link #unsolidMilestones} queue is empty and {@link #initialized} is false, set
     * {@link #initialized} to true.
     */
    private void scanMilestonesInQueue() {
        if(unsolidMilestones.size() > 0) {
            Map<Hash, Integer> newSolidificationQueue = new ConcurrentHashMap<>();
            unsolidMilestones.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(milestone -> {
                        if (newSolidificationQueue.size() < MAX_SIZE) {
                            newSolidificationQueue.put(milestone.getKey(), milestone.getValue());
                        }
                    });
            solidificationQueue = newSolidificationQueue;
        }

        oldestMilestoneInQueue = null;
        for (Map.Entry<Hash, Integer> currentEntry : solidificationQueue.entrySet()) {
            if (seenMilestones.containsKey(currentEntry.getValue())) {
                unsolidMilestones.remove(currentEntry.getKey());
                solidificationQueue.remove(currentEntry.getKey());
            } else {
                updateOldestMilestone(currentEntry.getKey(), currentEntry.getValue());
            }
        }
        if(!initialized && unsolidMilestones.size() == 0){
            initialized = true;
        }
    }

    private void updateOldestMilestone(Hash milestoneHash, int milestoneIndex){
        if (oldestMilestoneInQueue == null || oldestMilestoneInQueue.getValue() > milestoneIndex) {
            oldestMilestoneInQueue = new AbstractMap.SimpleEntry<>(milestoneHash, milestoneIndex);
        }
    }

    /**
     * Iterates through seen milestones and ensures that they are valid. If the milestone is invalid, the index is
     * checked to see if a milestone object exists already. If an existing milestone is present, then the current
     * milestone index/hash pairing is replaced with the existing milestone hash.
     */
    private void solidifySeenMilestones() throws Exception {
        Iterator<Map.Entry<Integer, Hash>> iterator = seenMilestones.entrySet().iterator();
        Map.Entry<Integer, Hash> milestone;
        while(!Thread.currentThread().isInterrupted() && iterator.hasNext()){
            milestone = iterator.next();
            int milestoneIndex = milestone.getKey();
            TransactionViewModel oldMilestoneTransaction = TransactionViewModel.fromHash(tangle, milestone.getValue());
            MilestoneValidity valid = milestoneService.validateMilestone(oldMilestoneTransaction, milestoneIndex);
            if(valid.equals(MilestoneValidity.INVALID)){
                MilestoneViewModel nextMilestone = MilestoneViewModel.get(tangle, milestoneIndex);
                if(nextMilestone != null) {
                    if(TransactionViewModel.fromHash(tangle, nextMilestone.getHash()).isSolid()) {
                        seenMilestones.remove(milestoneIndex);
                        removeFromQueue(oldMilestoneTransaction.getHash());
                        add(nextMilestone.getHash(), milestoneIndex);
                    }
                } else {
                    seenMilestones.remove(milestoneIndex);
                    removeFromQueue(oldMilestoneTransaction.getHash());
                }
            }
        }
    }

    /**
     * Iterates through valid milestones and submits them to the {@link TransactionSolidifier}
     */
    private void processSolidifyQueue() throws Exception {
        Iterator<Map.Entry<Hash, Integer>> iterator = solidificationQueue.entrySet().iterator();
        Map.Entry<Hash, Integer> milestone;
        while(!Thread.currentThread().isInterrupted() && iterator.hasNext()){
            milestone = iterator.next();
            TransactionViewModel milestoneCandidate = TransactionViewModel.fromHash(tangle, milestone.getKey());

            MilestoneValidity validity = milestoneService.validateMilestone(milestoneCandidate, milestone.getValue());
            switch(validity){
                case VALID:
                    milestoneCandidate.isMilestone(tangle, snapshotProvider.getInitialSnapshot(), true);
                    if(milestoneCandidate.isSolid()) {
                        addSeenMilestone(milestone.getKey(), milestone.getValue());
                    } else {
                        transactionSolidifier.addToSolidificationQueue(milestone.getKey());
                    }
                    break;
                case INCOMPLETE:
                    transactionSolidifier.addToSolidificationQueue(milestone.getKey());
                    break;
                case INVALID:
                    removeFromQueue(milestone.getKey());
            }
        }
    }

    /**
     * Tries to solidifiy the next milestone index. If successful, the milestone will be removed from the
     * {@link #seenMilestones} queue, and any milestone objects below that index in the {@link #unsolidMilestones} queue
     * will be removed as well.
     */
    private void checkLatestSolidMilestone(){
        try {
            if (latestMilestoneIndex > latestSolidMilestone) {
                int nextMilestone = latestSolidMilestone + 1;
                if (seenMilestones.containsKey(nextMilestone)){
                    TransactionViewModel milestone = TransactionViewModel.fromHash(tangle,
                            seenMilestones.get(nextMilestone));
                    if(milestone.isSolid()){
                        updateSolidMilestone(latestSolidMilestone);
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

    private void solidifyLog(){
        if (!unsolidMilestones.isEmpty() && oldestMilestoneInQueue != null){
            int milestoneIndex = oldestMilestoneInQueue.getValue();
            log.info("Solidifying milestone # " + milestoneIndex + " - [ LSM: " + latestSolidMilestone +
                    " LM: " + latestMilestoneIndex + " ] - [ Remaining: " +
                    (latestMilestoneIndex - latestSolidMilestone) + " Queued: " +
                    (seenMilestones.size() + unsolidMilestones.size()) + " ]");
        }
    }

    /**
     * Iterates through milestone indexes starting from the initial snapshot index, and returns the newest existing
     * milestone index
     *
     * @return  Latest Solid Milestone index
     * @throws Exception
     */
    private void bootStrapSolidMilestones() throws Exception {
        boolean solid = true;
        int milestoneIndex = snapshotProvider.getInitialSnapshot().getIndex();
        MilestoneViewModel milestone;
        while(solid){
            milestoneIndex += 1;
            if((milestone = MilestoneViewModel.get(tangle, milestoneIndex)) == null){
                solid = false;
                milestoneIndex -= 1;
            }
        }
        latestSolidMilestone = milestoneIndex;
        latestMilestoneIndex = milestoneIndex;

        AddressViewModel.load(tangle, config.getCoordinator()).getHashes().forEach(hash -> {
            try {
                int index;
                if ((index = milestoneService.getMilestoneIndex(TransactionViewModel.fromHash(tangle, hash))) >
                        latestSolidMilestone){
                    add(hash, index);
                }
            } catch(Exception e) {
                log.error("Error processing existing milestone index", e);
            }
        });
    }



    private void updateSolidMilestone(int currentSolidMilestoneIndex) throws Exception{
        MilestoneViewModel nextSolidMilestone = MilestoneViewModel.get(tangle, currentSolidMilestoneIndex + 1);
        if(nextSolidMilestone != null) {
            applySolidMilestoneToLedger(nextSolidMilestone);
            logChange(currentSolidMilestoneIndex);

            if (currentSolidMilestoneIndex + 1 == latestMilestoneIndex) {
                transactionRequester.clearRecentlyRequestedTransactions();
            }

            removeSeenMilestone(currentSolidMilestoneIndex + 1);
            latestSolidMilestone = currentSolidMilestoneIndex + 1;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(Hash milestoneHash, int milestoneIndex){
        if(!unsolidMilestones.containsKey(milestoneHash) && !seenMilestones.containsKey(milestoneIndex) &&
                milestoneIndex > latestSolidMilestone){
            unsolidMilestones.put(milestoneHash, milestoneIndex);
            updateQueues(milestoneHash, milestoneIndex);
        }
    }

    private void updateQueues(Hash milestoneHash, int milestoneIndex){
        if(solidificationQueue.containsKey(milestoneHash)) {
            if(solidificationQueue.size() >= MAX_SIZE){
                Iterator<Map.Entry<Hash, Integer>> iterator = solidificationQueue.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }

        solidificationQueue.put(milestoneHash, milestoneIndex);
        scanMilestonesInQueue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSeenMilestone(Hash milestoneHash, int milestoneIndex){
        if(milestoneIndex > latestMilestoneIndex) {
            logNewMilestone(latestMilestoneIndex, milestoneIndex, milestoneHash);
        }

        if(!seenMilestones.containsKey(milestoneIndex)){
            seenMilestones.put(milestoneIndex, milestoneHash);
        }
        removeFromQueue(milestoneHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Map.Entry<Hash, Integer> >getOldestMilestonesInQueue(){
        return new ArrayList<>(solidificationQueue.entrySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeFromQueue(Hash milestoneHash) {
        unsolidMilestones.remove(milestoneHash);
        solidificationQueue.remove(milestoneHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLatestMilestoneIndex(){
        return latestMilestoneIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getLatestMilestoneHash(){
        return latestMilestoneHash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLatestMilestone(Hash milestoneHash, int milestoneIndex) {
        if (milestoneIndex > latestMilestoneIndex) {
            this.latestMilestoneHash = milestoneHash;
            this.latestMilestoneIndex = milestoneIndex;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInitialScanComplete(){
        return initialized;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logNewMilestone(int oldMilestoneIndex, int newMilestoneIndex, Hash newMilestoneHash){
        setLatestMilestone(newMilestoneHash, newMilestoneIndex);
        tangle.publish("lmi %d %d", oldMilestoneIndex, newMilestoneIndex);
        log.info("Latest milestone has changed from #" + oldMilestoneIndex + " to #" + newMilestoneIndex);
    }

    /**
     * Remove milestone candidate from the {@link #seenMilestones} queue, then iterate through the
     * {@link #unsolidMilestones} queue and remove any milestones with an index below the provided index.
     *
     * @param solidMilestoneIndex   The milestone index to remove
     */
    private void removeSeenMilestone(int solidMilestoneIndex) {
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


    private void applySolidMilestoneToLedger(MilestoneViewModel milestone) throws Exception {
        if (ledgerService.applyMilestoneToLedger(milestone)) {
            if (isRepairRunning() && isRepairSuccessful(milestone)) {
                stopRepair();
            }
            syncProgressInfo.addMilestoneApplicationTime();
        } else {
            repairCorruptedMilestone(milestone);
        }
    }

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
     * Every time we call this method the internal {@link #repairBackoffCounter} is incremented which causes the next
     * call of this method to repair an additional milestone. This means that whenever we face an error we first try to
     * reset only the last milestone, then the two last milestones, then the three last milestones (and so on ...) until
     * the problem was fixed.
     * </p>
     * <p>
     * To be able to tell when the problem is fixed and the {@link #repairBackoffCounter} can be reset, we store the
     * milestone index that caused the problem the first time we call this method.
     * </p>
     *
     * @param errorCausingMilestone the milestone that failed to be applied
     * @throws MilestoneException if we failed to reset the corrupted milestone
     */
    private void repairCorruptedMilestone(MilestoneViewModel errorCausingMilestone) throws MilestoneException {
        if (repairBackoffCounter++ == 0) {
            errorCausingMilestoneIndex = errorCausingMilestone.index();
        }
        for (int i = errorCausingMilestone.index(); i > errorCausingMilestone.index() - repairBackoffCounter; i--) {
            milestoneService.resetCorruptedMilestone(i);
        }
    }

    /**
     * <p>
     * Checks if we are currently trying to repair a milestone.
     * </p>
     * <p>
     * We simply use the {@link #repairBackoffCounter} as an indicator if a repair routine is running.
     * </p>
     *
     * @return {@code true} if we are trying to repair a milestone and {@code false} otherwise
     */
    private boolean isRepairRunning() {
        return repairBackoffCounter != 0;
    }

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
    private boolean isRepairSuccessful(MilestoneViewModel processedMilestone) {
        return processedMilestone.index() > errorCausingMilestoneIndex;
    }

    /**
     * <p>
     * Resets the internal variables that are used to keep track of the repair process.
     * </p>
     * <p>
     * It gets called whenever we advance to a milestone that has a higher milestone index than the milestone that
     * initially caused the repair routine to kick in (see {@link #repairCorruptedMilestone(MilestoneViewModel)}.
     * </p>
     */
    private void stopRepair() {
        repairBackoffCounter = 0;
        errorCausingMilestoneIndex = Integer.MAX_VALUE;
    }


    private void logChange(int prevSolidMilestoneIndex) {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();
        latestSolidMilestone = latestSnapshot.getIndex();

        if (prevSolidMilestoneIndex == latestSolidMilestone) {
            return;
        }

        Hash latestMilestoneHash = latestSnapshot.getHash();
        log.delegate().info("Latest SOLID milestone index changed from #" + prevSolidMilestoneIndex + " to #" + latestSolidMilestone);

        tangle.publish("lmsi %d %d", prevSolidMilestoneIndex, latestSolidMilestone);
        tangle.publish("lmhs %s", latestMilestoneHash);

        if (!config.isPrintSyncProgressEnabled()) {
            return;
        }

        int latestMilestoneIndex = getLatestMilestoneIndex();

        // only print more sophisticated progress if we are coming from a more unsynced state
        if(latestMilestoneIndex - latestSolidMilestone < 1) {
            syncProgressInfo.setSyncMilestoneStartIndex(latestSolidMilestone);
            syncProgressInfo.resetMilestoneApplicationTimes();
            return;
        }

        int estSecondsToBeSynced = syncProgressInfo.computeEstimatedTimeToSyncUpSeconds(latestMilestoneIndex,
                latestSolidMilestone);
        StringBuilder progressSB = new StringBuilder();

        // add progress bar
        progressSB.append(ASCIIProgressBar.getProgressBarString(syncProgressInfo.getSyncMilestoneStartIndex(),
                latestMilestoneIndex, latestSolidMilestone));
        // add lsm to lm
        progressSB.append(String.format(" [LSM %d / LM %d - remaining: %d]", latestSolidMilestone,
                latestMilestoneIndex, latestMilestoneIndex - latestSolidMilestone));
        // add estimated time to get fully synced
        if (estSecondsToBeSynced != -1) {
            progressSB.append(String.format(" - est. seconds to get synced: %d", estSecondsToBeSynced));
        }
        log.info(progressSB.toString());
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
        void addMilestoneApplicationTime(){
            if(lastMilestoneApplyTimes.remainingCapacity() == 0){
                lastMilestoneApplyTimes.remove();
            }
            lastMilestoneApplyTimes.add(System.currentTimeMillis());
        }

        /**
         * Clears the records of times when milestones got applied.
         */
        void resetMilestoneApplicationTimes(){
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
