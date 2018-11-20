package com.iota.iri;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.Sponge;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.iota.iri.controllers.TransactionViewModel.*;

public class TransactionValidator {
    private static final Logger log = LoggerFactory.getLogger(TransactionValidator.class);
    private static final int  TESTNET_MWM_CAP = 13;
    public static final int SOLID_SLEEP_TIME = 500;

    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final TipsViewModel tipsViewModel;
    private final TransactionRequester transactionRequester;
    private int minWeightMagnitude = 81;
    private static final long MAX_TIMESTAMP_FUTURE = 2L * 60L * 60L;
    private static final long MAX_TIMESTAMP_FUTURE_MS = MAX_TIMESTAMP_FUTURE * 1_000L;


    /////////////////////////////////fields for solidification thread//////////////////////////////////////

    private Thread newSolidThread;

    /**
     * If true use {@link #newSolidTransactionsOne} while solidifying. Else use {@link #newSolidTransactionsTwo}.
     */
    private final AtomicBoolean useFirst = new AtomicBoolean(true);
    /**
     * Is {@link #newSolidThread} shutting down
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    /**
     * mutex for solidification
     */
    private final Object cascadeSync = new Object();
    private final Set<Hash> newSolidTransactionsOne = new LinkedHashSet<>();
    private final Set<Hash> newSolidTransactionsTwo = new LinkedHashSet<>();

    /**
     * Constructor for Tangle Validator
     *
     * @param tangle relays tangle data to and from the persistence layer
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param tipsViewModel container that gets updated with the latest tips (transactions with no children)
     * @param transactionRequester used to request missing transactions from neighbors
     */
    TransactionValidator(Tangle tangle, SnapshotProvider snapshotProvider, TipsViewModel tipsViewModel, TransactionRequester transactionRequester) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.tipsViewModel = tipsViewModel;
        this.transactionRequester = transactionRequester;
    }

    /**
     * Does two things:
     * <ol>
     *     <li>Sets the minimum weight magnitude (MWM). POW on a transaction is validated by counting a certain
     *     number of consecutive 9s in the end of the transaction hash. The number of 9s is the MWM.</li>
     *     <li>Starts the transaction solidification thread.</li>
     * </ol>
     *
     *
     * @see #spawnSolidTransactionsPropagation()
     * @param testnet <tt>true</tt> if we are in testnet mode, this caps {@code mwm} to {@value #TESTNET_MWM_CAP}
     *                regardless of parameter input.
     * @param mwm minimum weight magnitude: the minimal number of 9s that ought to appear at the end of the transaction
     *            hash
     */
    public void init(boolean testnet, int mwm) {
        setMwm(testnet, mwm);

        newSolidThread = new Thread(spawnSolidTransactionsPropagation(), "Solid TX cascader");
        newSolidThread.start();
    }

    //Package Private For Testing
    void setMwm(boolean testnet, int mwm) {
        minWeightMagnitude = mwm;

        //lowest allowed MWM encoded in 46 bytes.
        if (!testnet){
            minWeightMagnitude = Math.max(minWeightMagnitude, TESTNET_MWM_CAP);
        }
    }

    /**
     * Shutdown roots to tip solidification thread
     * @throws InterruptedException
     * @see #spawnSolidTransactionsPropagation()
     */
    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        newSolidThread.join();
    }

    /**
     * @return the minimal number of trailing 9s that have to be present at the end of the transaction hash
     * in order to validate that sufficient proof of work has been done
     */
    public int getMinWeightMagnitude() {
        return minWeightMagnitude;
    }

    /**
     * Checks that the timestamp of the transaction is below the last global snapshot time
     * or more than {@value #MAX_TIMESTAMP_FUTURE} seconds in the future, and thus invalid.
     *
     * <p>
     *     First the attachment timestamp (set after performing POW) is checked, and if not available
     *     the regular timestamp is checked. Genesis transaction will always be valid.
     * </p>
     * @param transactionViewModel transaction under test
     * @return <tt>true</tt> if timestamp is not in valid bounds and {@code transactionViewModel} is not genesis.
     * Else returns <tt>false</tt>.
     */
    private boolean hasInvalidTimestamp(TransactionViewModel transactionViewModel) {
        // ignore invalid timestamps for transactions that were requested by our node while solidifying a milestone
        if(transactionRequester.isTransactionRequested(transactionViewModel.getHash(), true)) {
            return false;
        }

        if (transactionViewModel.getAttachmentTimestamp() == 0) {
            return transactionViewModel.getTimestamp() < snapshotProvider.getInitialSnapshot().getTimestamp() && !snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(transactionViewModel.getHash())
                    || transactionViewModel.getTimestamp() > (System.currentTimeMillis() / 1000) + MAX_TIMESTAMP_FUTURE;
        }
        return transactionViewModel.getAttachmentTimestamp() < (snapshotProvider.getInitialSnapshot().getTimestamp() * 1000L)
                || transactionViewModel.getAttachmentTimestamp() > System.currentTimeMillis() + MAX_TIMESTAMP_FUTURE_MS;
    }

    /**
     * Runs the following validation checks on a transaction:
     * <ol>
     *     <li>{@link #hasInvalidTimestamp} check.</li>
     *     <li>Check that no value trits are set beyond the usable index, otherwise we will have values larger
     *     than max supply.</li>
     *     <li>Check that sufficient POW was performed.</li>
     *     <li>In value transactions, we check that the address has 0 set as the last trit. This must be because of the
     *     conversion between bytes to trits.</li>
     * </ol>
     *Exception is thrown upon failure.
     *
     * @param transactionViewModel transaction that should be validated
     * @param minWeightMagnitude the minimal number of trailing 9s at the end of the transaction hash
     * @throws StaleTimestampException if timestamp check fails
     * @throws IllegalStateException if any of the other checks fail
     */
    public void runValidation(TransactionViewModel transactionViewModel, final int minWeightMagnitude) {
        transactionViewModel.setMetadata();
        transactionViewModel.setAttachmentData();
        if(hasInvalidTimestamp(transactionViewModel)) {
            throw new StaleTimestampException("Invalid transaction timestamp.");
        }
        for (int i = VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE; i < VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE; i++) {
            if (transactionViewModel.trits()[i] != 0) {
                throw new IllegalStateException("Invalid transaction value");
            }
        }

        int weightMagnitude = transactionViewModel.weightMagnitude;
        if(weightMagnitude < minWeightMagnitude) {
            throw new IllegalStateException("Invalid transaction hash");
        }

        if (transactionViewModel.value() != 0 && transactionViewModel.getAddressHash().trits()[Curl.HASH_LENGTH - 1] != 0) {
            throw new IllegalStateException("Invalid transaction address");
        }
    }

    /**
     * Creates a new transaction from  {@code trits} and validates it with {@link #runValidation}.
     *
     * @param trits raw transaction trits
     * @param minWeightMagnitude minimal number of trailing 9s in transaction for POW validation
     * @return the transaction resulting from the raw trits if valid.
     * @throws RuntimeException if validation fails
     */
    public TransactionViewModel validateTrits(final byte[] trits, int minWeightMagnitude) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(trits, 0, trits.length, SpongeFactory.create(SpongeFactory.Mode.CURLP81)));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
    }

    /**
     * Creates a new transaction from {@code bytes} and validates it with {@link #runValidation}.
     *
     * @param bytes raw transaction bytes
     * @param minWeightMagnitude minimal number of trailing 9s in transaction for POW validation
     * @return the transaction resulting from the raw bytes if valid
     * @throws RuntimeException if validation fails
     */
    public TransactionViewModel validateBytes(final byte[] bytes, int minWeightMagnitude, Sponge curl) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(bytes, TRINARY_SIZE, curl));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
    }

    /**
     * This method does the same as {@link #checkSolidity(Hash, boolean, int)} but defaults to an unlimited amount
     * of transactions that are allowed to be traversed.
     *
     * @param hash hash of the transactions that shall get checked
     * @param milestone true if the solidity check was issued while trying to solidify a milestone and false otherwise
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */
    public boolean checkSolidity(Hash hash, boolean milestone) throws Exception {
        return checkSolidity(hash, milestone, Integer.MAX_VALUE);
    }

    /**
     * This method checks transactions for solidity and marks them accordingly if they are found to be solid.
     *
     * It iterates through all approved transactions until it finds one that is missing in the database or until it
     * reached solid transactions on all traversed subtangles. In case of a missing transactions it issues a transaction
     * request and returns false. If no missing transaction is found, it marks the processed transactions as solid in
     * the database and returns true.
     *
     * Since this operation can potentially take a long time to terminate if it would have to traverse big parts of the
     * tangle, it is possible to limit the amount of transactions that are allowed to be processed, while looking for
     * unsolid / missing approvees. This can be useful when trying to "interrupt" the solidification of one transaction
     * (if it takes too many steps) to give another one the chance to be solidified instead (i.e. prevent blocks in the
     * solidification threads).
     *
     * @param hash hash of the transactions that shall get checked
     * @param milestone true if the solidity check was issued while trying to solidify a milestone and false otherwise
     * @param maxProcessedTransactions the maximum amount of transactions that are allowed to be traversed
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */
    public boolean checkSolidity(Hash hash, boolean milestone, int maxProcessedTransactions) throws Exception {
        if(fromHash(tangle, hash).isSolid()) {
            return true;
        }
        Set<Hash> analyzedHashes = new HashSet<>(snapshotProvider.getInitialSnapshot().getSolidEntryPoints().keySet());
        if(maxProcessedTransactions != Integer.MAX_VALUE) {
            maxProcessedTransactions += analyzedHashes.size();
        }
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                if(analyzedHashes.size() >= maxProcessedTransactions) {
                    return false;
                }

                final TransactionViewModel transaction = fromHash(tangle, hashPointer);
                if(!transaction.isSolid()) {
                    if (transaction.getType() == PREFILLED_SLOT && !snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hashPointer)) {
                        solid = false;

                        if (!transactionRequester.isTransactionRequested(hashPointer, milestone)) {
                            transactionRequester.requestTransaction(hashPointer, milestone);
                            break;
                        }
                    } else {
                        nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                    }
                }
            }
        }
        if (solid) {
            updateSolidTransactions(tangle, snapshotProvider.getInitialSnapshot(), analyzedHashes);
        }
        analyzedHashes.clear();
        return solid;
    }

    public void addSolidTransaction(Hash hash) {
        synchronized (cascadeSync) {
            if (useFirst.get()) {
                newSolidTransactionsOne.add(hash);
            } else {
                newSolidTransactionsTwo.add(hash);
            }
        }
    }

    /**
     * Creates a runnable that runs {@link #propagateSolidTransactions()} in a loop every {@value #SOLID_SLEEP_TIME} ms
     * @return runnable that is not started
     */
    private Runnable spawnSolidTransactionsPropagation() {
        return () -> {
            while(!shuttingDown.get()) {
                propagateSolidTransactions();
                try {
                    Thread.sleep(SOLID_SLEEP_TIME);
                } catch (InterruptedException e) {
                    // Ignoring InterruptedException. Do not use Thread.currentThread().interrupt() here.
                    log.error("Thread was interrupted: ", e);
                }
            }
        };
    }

    /**
     * Iterates over all currently known solid transactions. For each solid transaction, we find
     * its children (approvers) and try to quickly solidify them with {@link #quietQuickSetSolid}.
     * If we manage to solidify the transactions, we add them to the solidification queue for a traversal by a later run.
     */
    //Package private for testing
    void propagateSolidTransactions() {
        Set<Hash> newSolidHashes = new HashSet<>();
        useFirst.set(!useFirst.get());
        //synchronized to make sure no one is changing the newSolidTransactions collections during addAll
        synchronized (cascadeSync) {
            //We are using a collection that doesn't get updated by other threads
            if (useFirst.get()) {
                newSolidHashes.addAll(newSolidTransactionsTwo);
                newSolidTransactionsTwo.clear();
            } else {
                newSolidHashes.addAll(newSolidTransactionsOne);
                newSolidTransactionsOne.clear();
            }
        }
        Iterator<Hash> cascadeIterator = newSolidHashes.iterator();
        while(cascadeIterator.hasNext() && !shuttingDown.get()) {
            try {
                Hash hash = cascadeIterator.next();
                TransactionViewModel transaction = fromHash(tangle, hash);
                Set<Hash> approvers = transaction.getApprovers(tangle).getHashes();
                for(Hash h: approvers) {
                    TransactionViewModel tx = fromHash(tangle, h);
                    if(quietQuickSetSolid(tx)) {
                        tx.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
                        tipsViewModel.setSolid(h);
                        addSolidTransaction(h);
                    }
                }
            } catch (Exception e) {
                log.error("Error while propagating solidity upwards", e);
            }
        }
    }


    /**
     * Updates a transaction after it was stored in the tangle. Tells the node to not request the transaction anymore,
     * to update the live tips accordingly, and attempts to quickly solidify the transaction.
     *
     * <p/>
     * Performs the following operations:
     *
     * <ol>
     *     <li>Removes {@code transactionViewModel}'s hash from the the request queue since we already found it.</li>
     *     <li>If {@code transactionViewModel} has no children (approvers), we add it to the node's active tip list.</li>
     *     <li>Removes {@code transactionViewModel}'s parents (branch & trunk) from the node's tip list
     *     (if they're present there).</li>
     *     <li>Attempts to quickly solidify {@code transactionViewModel} by checking whether its direct parents
     *     are solid. If solid we add it to the queue transaction solidification thread to help it propagate the
     *     solidification to the approving child transactions.</li>
     *     <li>Requests missing direct parent (trunk & branch) transactions that are needed to solidify
     *     {@code transactionViewModel}.</li>
     * </ol>
     * @param transactionViewModel received transaction that is being updated
     * @throws Exception if an error occurred while trying to solidify
     * @see TipsViewModel
     */
    //Not part of the validation process. This should be moved to a component in charge of
    //what transaction we gossip.
    public void updateStatus(TransactionViewModel transactionViewModel) throws Exception {
        transactionRequester.clearTransactionRequest(transactionViewModel.getHash());
        if(transactionViewModel.getApprovers(tangle).size() == 0) {
            tipsViewModel.addTipHash(transactionViewModel.getHash());
        }
        tipsViewModel.removeTipHash(transactionViewModel.getTrunkTransactionHash());
        tipsViewModel.removeTipHash(transactionViewModel.getBranchTransactionHash());

        if(quickSetSolid(transactionViewModel)) {
            transactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
            tipsViewModel.setSolid(transactionViewModel.getHash());
            addSolidTransaction(transactionViewModel.getHash());
        }
    }

    /**
     * Perform a {@link #quickSetSolid} while capturing and logging errors
     * @param transactionViewModel transaction we try to solidify.
     * @return <tt>true</tt> if we managed to solidify, else <tt>false</tt>.
     */
    private boolean quietQuickSetSolid(TransactionViewModel transactionViewModel) {
        try {
            return quickSetSolid(transactionViewModel);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    /**
     * Tries to solidify the transactions quickly by performing {@link #checkApproovee} on both parents (trunk and
     * branch). If the parents are solid, mark the transactions as solid.
     * @param transactionViewModel transaction to solidify
     * @return <tt>true</tt> if we made the transaction solid, else <tt>false</tt>.
     * @throws Exception
     */
    private boolean quickSetSolid(final TransactionViewModel transactionViewModel) throws Exception {
        if(!transactionViewModel.isSolid()) {
            boolean solid = true;
            if (!checkApproovee(transactionViewModel.getTrunkTransaction(tangle))) {
                solid = false;
            }
            if (!checkApproovee(transactionViewModel.getBranchTransaction(tangle))) {
                solid = false;
            }
            if(solid) {
                transactionViewModel.updateSolid(true);
                transactionViewModel.updateHeights(tangle, snapshotProvider.getInitialSnapshot());
                return true;
            }
        }
        return false;
    }

    /**
     * If the the {@code approvee} is missing, request it from a neighbor.
     * @param approovee transaction we check.
     * @return true if {@code approvee} is solid.
     * @throws Exception if we encounter an error while requesting a transaction
     */
    private boolean checkApproovee(TransactionViewModel approovee) throws Exception {
        if(approovee.getType() == PREFILLED_SLOT) {
            transactionRequester.requestTransaction(approovee.getHash(), false);
            return false;
        }
        if(snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(approovee.getHash())) {
            return true;
        }
        return approovee.isSolid();
    }

    //Package Private For Testing
    boolean isNewSolidTxSetsEmpty () {
        return newSolidTransactionsOne.isEmpty() && newSolidTransactionsTwo.isEmpty();
    }

    /**
     * Thrown if transaction fails {@link #hasInvalidTimestamp} check.
     */
    public static class StaleTimestampException extends RuntimeException {
        StaleTimestampException (String message) {
            super(message);
        }
    }
}
