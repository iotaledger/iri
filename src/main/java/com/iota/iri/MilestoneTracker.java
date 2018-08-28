package com.iota.iri;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.ISS;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.StateDiff;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.ProgressLogger;
import com.iota.iri.zmq.MessageQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.iota.iri.MilestoneTracker.Validity.*;

public class MilestoneTracker {
    /**
     * Validity states of transactions regarding their milestone status.
     */
    enum Validity {
        VALID,
        INVALID,
        INCOMPLETE
    }

    private static int RESCAN_INTERVAL = 5000;

    /**
     * How often (in milliseconds) to dump log messages about status updates.
     */
    private static int STATUS_LOG_INTERVAL = 5000;

    /**
     * How many milestones after the latest solid one will try to be actively solidified.
     */
    private static int MILESTONE_SOLIDIFY_AHEAD_RANGE = 50;

    private AtomicBoolean ledgerValidatorInitialized = new AtomicBoolean(false);

    private final Logger log = LoggerFactory.getLogger(MilestoneTracker.class);
    private final Tangle tangle;
    private final Hash coordinator;
    private final TransactionValidator transactionValidator;
    private final boolean testnet;
    private final MessageQ messageQ;
    private final int numOfKeysInMilestone;
    private final boolean acceptAnyTestnetCoo;
    private final boolean isRescanning;
    public Snapshot initialSnapshot;
    public Snapshot latestSnapshot;

    private LedgerValidator ledgerValidator;
    public Hash latestMilestone = Hash.NULL_HASH;
    public Hash latestSolidSubtangleMilestone = latestMilestone;

    public int latestMilestoneIndex;
    public int latestSolidSubtangleMilestoneIndex;

    private final Set<Hash> analyzedMilestoneCandidates = new HashSet<>();
    private final ConcurrentHashMap<Hash, Integer> unsolidMilestones = new ConcurrentHashMap<>();

    private boolean shuttingDown;

    /**
     * This variable is used to keep track of the asynchronous tasks, that the "Solid Milestone Tracker" should wait for.
     */
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    public MilestoneTracker(Tangle tangle,
                            TransactionValidator transactionValidator,
                            MessageQ messageQ,
                            Snapshot initialSnapshot, IotaConfig config
    ) {
        this.tangle = tangle;
        this.transactionValidator = transactionValidator;
        this.messageQ = messageQ;
        this.initialSnapshot = initialSnapshot;
        this.latestSnapshot = initialSnapshot.clone();

        //configure
        this.testnet = config.isTestnet();
        this.coordinator = new Hash(config.getCoordinator());
        this.numOfKeysInMilestone = config.getNumberOfKeysInMilestone();
        this.latestMilestoneIndex = latestSnapshot.index();
        this.latestSolidSubtangleMilestoneIndex = latestSnapshot.index();
        this.acceptAnyTestnetCoo = config.isDontValidateTestnetMilestoneSig();
        this.isRescanning = config.isRescanDb() || config.isRevalidate();
    }

    public void init (LedgerValidator ledgerValidator) {
        this.ledgerValidator = ledgerValidator;

        // start the threads
        spawnLatestMilestoneTracker();
        spawnSolidMilestoneTracker();
        spawnMilestoneSolidifier();
    }

    private void spawnLatestMilestoneTracker() {
        (new Thread(() -> {
            log.info("Waiting for Ledger Validator initialization ...");
            while(!ledgerValidatorInitialized.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) { /* do nothing */ }
            }

            // to be able to process the milestones in the correct order after a rescan of the database, we block write
            // access until the scan finished at least once
            if(isRescanning) {
                lock.writeLock().lock();
            }

            ProgressLogger scanningMilestonesProgress = new ProgressLogger("Scanning Latest Milestones", log);

            // bootstrap our latestMilestone with the last milestone in the database (faster startup)
            try {
                analyzeMilestoneCandidate(MilestoneViewModel.latest(tangle).getHash());
            } catch(Exception e) { /* do nothing */ }

            log.info("Tracker started.");
            boolean firstRun = true;
            while (!shuttingDown) {
                long scanTime = System.currentTimeMillis();

                try {
                    // analyze all found milestone candidates
                    Set<Hash> hashes = AddressViewModel.load(tangle, coordinator).getHashes();
                    scanningMilestonesProgress.setEnabled(firstRun).start(hashes.size());
                    for(Hash hash: hashes) {
                        if(!shuttingDown && analyzedMilestoneCandidates.add(hash) && analyzeMilestoneCandidate(hash) == INCOMPLETE) {
                            analyzedMilestoneCandidates.remove(hash);
                        }

                        scanningMilestonesProgress.progress();
                    }
                    scanningMilestonesProgress.finish();

                    // allow the "Solid Milestone Tracker" to continue if we finished the first run in rescanning mode
                    if(firstRun && isRescanning) {
                        lock.writeLock().unlock();
                    }

                    Thread.sleep(Math.max(1, RESCAN_INTERVAL - (System.currentTimeMillis() - scanTime)));
                } catch (final Exception e) {
                    log.error("Error during Latest Milestone updating", e);
                }

                firstRun = false;
            }
        }, "Latest Milestone Tracker")).start();
    }

    private void spawnSolidMilestoneTracker() {
        (new Thread(() -> {
            log.info("Initializing Ledger Validator...");
            try {
                ledgerValidator.init();
                ledgerValidatorInitialized.set(true);
            } catch (Exception e) {
                log.error("Error initializing snapshots. Skipping.", e);
            }
            log.info("Tracker started.");
            while (!shuttingDown) {
                long scanTime = System.currentTimeMillis();

                try {
                    if(latestSolidSubtangleMilestoneIndex < latestMilestoneIndex) {
                        updateLatestSolidSubtangleMilestone();
                    }

                    Thread.sleep(Math.max(1, RESCAN_INTERVAL - (System.currentTimeMillis() - scanTime)));
                } catch (final Exception e) {
                    log.error("Error during Solid Milestone updating", e);
                }
            }
        }, "Solid Milestone Tracker")).start();
    }

    private void spawnMilestoneSolidifier() {
        new Thread(() -> {
            while(!shuttingDown) {
                unsolidMilestones.forEach((milestoneHash, milestoneIndex) -> {
                    try {
                        // remove old milestones that are not relevant anymore
                        if(milestoneIndex <= latestSolidSubtangleMilestoneIndex) {
                            unsolidMilestones.remove(milestoneHash);
                        }

                        // remove milestones that have become solid and are in our check range
                        else if(milestoneIndex < latestSolidSubtangleMilestoneIndex + MILESTONE_SOLIDIFY_AHEAD_RANGE && transactionValidator.checkSolidity(milestoneHash, true)) {
                            unsolidMilestones.remove(milestoneHash);
                        }
                    } catch(Exception e) {
                        log.error("Error while trying to solidify milestones", e);
                    }
                });

                try { Thread.sleep(500); } catch (InterruptedException e) { /* do nothing */ }
            }
        }, "Milestone Solidifier").start();
    }

    /**
     * This method analyzes a transaction to determine if it is a milestone.
     *
     * In contrast to {@link #validateMilestone} it also updates the internal variables keeping track of the latest
     * milestone and dumps a log message whenever a change is detected. So this method can be seen as the core logic for
     * scanning and keeping track of the latest milestones. Since it internally calls {@link #validateMilestone} a
     * milestone will have its corresponding {@link MilestoneViewModel} created after being processed by this method.
     *
     * The method first checks if the transaction originates from the coordinator address, so the check is relatively
     * cheap and can easily performed on any incoming transaction.
     *
     * @param potentialMilestoneTransactionHash hash of the transaction that shall get checked for being a milestone
     * @return VALID if the transaction is a milestone, INCOMPLETE if the bundle is not complete and INVALID otherwise
     * @throws Exception if something goes wrong while retrieving information from the database
     */
    public Validity analyzeMilestoneCandidate(Hash potentialMilestoneTransactionHash) throws Exception {
        return analyzeMilestoneCandidate(TransactionViewModel.fromHash(tangle, potentialMilestoneTransactionHash));
    }

    /**
     * This method analyzes a transaction to determine if it is a milestone.
     *
     * In contrast to {@link #validateMilestone} it also updates the internal variables keeping track of the latest
     * milestone and dumps a log message whenever a change is detected. So this method can be seen as the core logic for
     * scanning and keeping track of the latest milestones. Since it internally calls {@link #validateMilestone} a
     * milestone will have its corresponding {@link MilestoneViewModel} created after being processed by this method.
     *
     * The method first checks if the transaction originates from the coordinator address, so the check is relatively
     * cheap and can easily performed on any incoming transaction.
     *
     * @param potentialMilestoneTransaction transaction that shall get checked for being a milestone
     * @return VALID if the transaction is a milestone, INCOMPLETE if the bundle is not complete and INVALID otherwise
     * @throws Exception if something goes wrong while retrieving information from the database
     */
    public Validity analyzeMilestoneCandidate(TransactionViewModel potentialMilestoneTransaction) throws Exception {
        if (coordinator.equals(potentialMilestoneTransaction.getAddressHash()) && potentialMilestoneTransaction.getCurrentIndex() == 0) {
            int milestoneIndex = getIndex(potentialMilestoneTransaction);

            switch (validateMilestone(SpongeFactory.Mode.CURLP27, potentialMilestoneTransaction, milestoneIndex)) {
                case VALID:
                    if (milestoneIndex > latestMilestoneIndex) {
                        messageQ.publish("lmi %d %d", latestMilestoneIndex, milestoneIndex);
                        log.info("Latest milestone has changed from #" + latestMilestoneIndex + " to #" + milestoneIndex);

                        latestMilestone = potentialMilestoneTransaction.getHash();
                        latestMilestoneIndex = milestoneIndex;
                    }

                    if(!potentialMilestoneTransaction.isSolid() && milestoneIndex >= latestSolidSubtangleMilestoneIndex) {
                        unsolidMilestones.put(potentialMilestoneTransaction.getHash(), milestoneIndex);
                    }

                    return VALID;

                case INCOMPLETE:
                    // issue a solidity check to solidify incomplete milestones
                    if(milestoneIndex >= latestSolidSubtangleMilestoneIndex) {
                        unsolidMilestones.put(potentialMilestoneTransaction.getHash(), milestoneIndex);
                    }

                    return INCOMPLETE;
            }
        }

        return INVALID;
    }

    /**
     * This method allows us to soft reset the ledger state, in case we face an inconsistent SnapshotState.
     *
     * It simply resets the latest snapshot to the initial one and rebuilds the ledger state. This will also make the
     * updateLatestSolidSubtangleMilestone trigger again and give it a chance to detect corruptions.
     */
    public void softReset() {
        lock.writeLock().lock();

        try {
            latestSnapshot = initialSnapshot.clone();
            latestSolidSubtangleMilestone = Hash.NULL_HASH;
            latestSolidSubtangleMilestoneIndex = initialSnapshot.index();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * This method allows us to hard reset the ledger state, in case we detect that milestones were processed in the
     * wrong order.
     *
     * It resets the snapshotIndex of all milestones following the one provided in the parameters, removes all
     * potentially corrupt StateDiffs and restores the initial ledger state, so we can start rebuilding it. This allows
     * us to recover from the invalid ledger state without repairing or pruning the database.
     *
     * @param targetMilestone the last correct milestone
     */
    public void hardReset(MilestoneViewModel targetMilestone, String reason) {
        // ignore errors due to old milestones
        if(targetMilestone == null || targetMilestone.index() < initialSnapshot.index()) {
            return;
        }

        // block write access to the ledger from other threads
        lock.writeLock().lock();

        // create a progress logger and start the logging
        ProgressLogger hardResetLogger = new ProgressLogger(
            "Resetting ledger to milestone " + targetMilestone.index() + " due to \"" + reason + "\"", log
        ).start(latestMilestoneIndex - targetMilestone.index() + 2); // +1 for softReset and +1 for starting milestone

        // prune all potentially invalid database fields
        try {
            MilestoneViewModel currentMilestone = targetMilestone;
            while(currentMilestone != null) {
                // reset the snapshotIndex of the milestone and its referenced approvees + it's StateDiff
                resetSnapshotIndexOfMilestone(currentMilestone);
                tangle.delete(StateDiff.class, currentMilestone.getHash());

                int currentStep = latestMilestoneIndex - currentMilestone.index() + 2 - hardResetLogger.getStepCount();
                hardResetLogger.progress(currentStep);

                // iterate to the next milestone
                currentMilestone = MilestoneViewModel.findClosestNextMilestone(tangle, currentMilestone.index());
            }
        } catch(Exception e) {
            hardResetLogger.abort(e);
        }

        // after we have cleaned up the database we do a soft reset to rescan the existing data
        softReset();

        // dump message when we are done
        hardResetLogger.finish();

        // unblock write access to the ledger from other threads
        lock.writeLock().unlock();
    }

    /**
     * This method resets the snapshotIndex of all transactions that "belong" to a milestone.
     *
     * While traversing the graph we use the snapshotIndex value to check if it still belongs to the given milestone and
     * ignore all transactions that where referenced by a previous one. Since we check if the snapshotIndex is bigger or
     * equal to the one of the targetMilestone, we can ignore the case that a previous milestone was not processed, yet
     * since it's milestoneIndex would still be 0.
     *
     * @param currentMilestone the milestone that shall have its confirmed transactions reset
     * @throws Exception if something goes wrong while accessing the database
     */
    public void resetSnapshotIndexOfMilestone(MilestoneViewModel currentMilestone) throws Exception {
        // initialize variables used for traversing the graph
        TransactionViewModel milestoneTransaction = TransactionViewModel.fromHash(tangle, currentMilestone.getHash());
        Set<Hash> seenMilestoneTransactions = new HashSet<>();
        Queue<TransactionViewModel> transactionsToExamine = new LinkedList<>(Collections.singleton(milestoneTransaction));

        // iterate through our queue and process all elements (while we iterate we add more)
        TransactionViewModel currentTransaction;
        while((currentTransaction = transactionsToExamine.poll()) != null) {
            if(seenMilestoneTransactions.add(currentTransaction.getHash())) {
                // reset the snapshotIndex to allow a repair
                currentTransaction.setSnapshot(tangle, 0);

                // only examine transactions that still belong to our milestone
                if(!Hash.NULL_HASH.equals(currentTransaction.getBranchTransactionHash())) {
                    TransactionViewModel branchTransaction = currentTransaction.getBranchTransaction(tangle);
                    if(branchTransaction.getType() != TransactionViewModel.PREFILLED_SLOT && branchTransaction.snapshotIndex() >= currentMilestone.index()) {
                        transactionsToExamine.add(branchTransaction);
                    }
                }

                // only examine transactions that still belong to our milestone
                if(!Hash.NULL_HASH.equals(currentTransaction.getTrunkTransactionHash())) {
                    TransactionViewModel trunkTransaction = currentTransaction.getTrunkTransaction(tangle);
                    if(trunkTransaction.getType() != TransactionViewModel.PREFILLED_SLOT && trunkTransaction.snapshotIndex() >= currentMilestone.index()) {
                        transactionsToExamine.add(trunkTransaction);
                    }
                }
            }
        }
    }

    public Validity validateMilestone(SpongeFactory.Mode mode, TransactionViewModel transactionViewModel, int index) throws Exception {
        if (index < 0 || index >= 0x200000) {
            return INVALID;
        }

        if (MilestoneViewModel.get(tangle, index) != null) {
            // Already validated.
            return VALID;
        }
        final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle, transactionViewModel.getHash());
        if (bundleTransactions.size() == 0) {
            return INCOMPLETE;
        }
        else {
            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {

                //if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(),transactionViewModel.getHash())) {
                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                    //final TransactionViewModel transactionViewModel2 = StorageTransactions.instance().loadTransaction(transactionViewModel.trunkTransactionPointer);
                    final TransactionViewModel transactionViewModel2 = transactionViewModel.getTrunkTransaction(tangle);
                    if (transactionViewModel2.getType() == TransactionViewModel.FILLED_SLOT
                            && transactionViewModel.getBranchTransactionHash().equals(transactionViewModel2.getTrunkTransactionHash())
                            && transactionViewModel.getBundleHash().equals(transactionViewModel2.getBundleHash())) {

                        final byte[] trunkTransactionTrits = transactionViewModel.getTrunkTransactionHash().trits();
                        final byte[] signatureFragmentTrits = Arrays.copyOfRange(transactionViewModel.trits(), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);

                        final byte[] merkleRoot = ISS.getMerkleRoot(mode, ISS.address(mode, ISS.digest(mode,
                                Arrays.copyOf(ISS.normalizedBundle(trunkTransactionTrits),
                                        ISS.NUMBER_OF_FRAGMENT_CHUNKS),
                                signatureFragmentTrits)),
                                transactionViewModel2.trits(), 0, index, numOfKeysInMilestone);
                        if ((testnet && acceptAnyTestnetCoo) || (new Hash(merkleRoot)).equals(coordinator)) {
                            MilestoneViewModel newMilestoneViewModel = new MilestoneViewModel(index, transactionViewModel.getHash());
                            newMilestoneViewModel.store(tangle);

                            // if we find a NEW milestone that should have been processed before our latest solid
                            // milestone -> reset the ledger state and check the milestones again
                            //
                            // NOTE: this can happen if a new subtangle becomes solid before a previous one while syncing
                            if(index < latestSolidSubtangleMilestoneIndex) {
                                hardReset(newMilestoneViewModel, "previously unknown milestone (#" + index + ") appeared");
                            }
                            return VALID;
                        } else {
                            return INVALID;
                        }
                    }
                }
            }
        }
        return INVALID;
    }

    public void updateLatestSolidSubtangleMilestone() throws Exception {
        lock.writeLock().lock();

        try {
            // introduce some variables that help us to emit log messages while processing the milestones
            int previousSolidSubtangleLatestMilestoneIndex = latestSolidSubtangleMilestoneIndex;
            long scanStart = System.currentTimeMillis();

            // get the next milestone
            MilestoneViewModel nextMilestone = MilestoneViewModel.findClosestNextMilestone(
                tangle, previousSolidSubtangleLatestMilestoneIndex
            );

            // while we have a milestone which is solid
            while(
                !shuttingDown &&
                nextMilestone != null &&
                transactionValidator.checkSolidity(nextMilestone.getHash(), true)
            ) {
                // if we can update the ledger state with our current milestone
                if(ledgerValidator.updateSnapshot(nextMilestone)) {
                    // update our internal variables
                    latestSolidSubtangleMilestone = nextMilestone.getHash();
                    latestSolidSubtangleMilestoneIndex = nextMilestone.index();

                    // iterate to the next milestone
                    nextMilestone = MilestoneViewModel.findClosestNextMilestone(tangle, latestSolidSubtangleMilestoneIndex);
                }

                // otherwise -> try to repair and abort our loop
                else {
                    // do a soft reset if we didn't do a hard reset yet
                    if(latestSnapshot.index() != initialSnapshot.index()) {
                        softReset();
                    }

                    nextMilestone = null;
                }

                // dump a log message periodically and if we are done
                if(previousSolidSubtangleLatestMilestoneIndex != latestSolidSubtangleMilestoneIndex && (
                    System.currentTimeMillis() - scanStart >= STATUS_LOG_INTERVAL || nextMilestone == null
                )) {
                    messageQ.publish("lmsi %d %d", previousSolidSubtangleLatestMilestoneIndex, latestSolidSubtangleMilestoneIndex);
                    messageQ.publish("lmhs %s", latestSolidSubtangleMilestone);
                    log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                            + previousSolidSubtangleLatestMilestoneIndex + " to #"
                            + latestSolidSubtangleMilestoneIndex);

                    scanStart = System.currentTimeMillis();
                    previousSolidSubtangleLatestMilestoneIndex = latestSolidSubtangleMilestoneIndex;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static int getIndex(TransactionViewModel transactionViewModel) {
        return (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET, 15);
    }

    public void shutDown() {
        shuttingDown = true;
    }

    public void reportToSlack(final int milestoneIndex, final int depth, final int nextDepth) {
        try {
            final String request = "token=" + URLEncoder.encode("<botToken>", "UTF-8") + "&channel=" + URLEncoder.encode("#botbox", "UTF-8") + "&text=" + URLEncoder.encode("TESTNET: ", "UTF-8") + "&as_user=true";

            final HttpURLConnection connection = (HttpsURLConnection) (new URL("https://slack.com/api/chat.postMessage")).openConnection();
            ((HttpsURLConnection)connection).setHostnameVerifier((hostname, session) -> true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            OutputStream out = connection.getOutputStream();
            out.write(request.getBytes("UTF-8"));
            out.close();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            InputStream inputStream = connection.getInputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {

                result.write(buffer, 0, length);
            }
            log.info(result.toString("UTF-8"));

        } catch (final Exception e) {

            e.printStackTrace();
        }
    }
}
