package com.iota.iri.service.validation;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.conf.ProtocolConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.Sponge;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

import static com.iota.iri.controllers.TransactionViewModel.*;

public class TransactionValidator {
    private static final int  TESTNET_MWM_CAP = 13;

    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final TipsViewModel tipsViewModel;
    private final TransactionRequester transactionRequester;
    private int minWeightMagnitude = 81;
    private static final long MAX_TIMESTAMP_FUTURE = 2L * 60L * 60L;
    private static final long MAX_TIMESTAMP_FUTURE_MS = MAX_TIMESTAMP_FUTURE * 1_000L;


    /**
     * Constructor for Tangle Validator
     *
     * @param tangle relays tangle data to and from the persistence layer
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param tipsViewModel container that gets updated with the latest tips (transactions with no children)
     * @param transactionRequester used to request missing transactions from neighbors
     * @param protocolConfig used for checking if we are in testnet and mwm. testnet <tt>true</tt> if we are in testnet
     *                       mode, this caps {@code mwm} to {@value #TESTNET_MWM_CAP} regardless of parameter input.
     *                       minimum weight magnitude: the minimal number of 9s that ought to appear at the end of the
     *                       transaction hash
     */
    public TransactionValidator(Tangle tangle, SnapshotProvider snapshotProvider, TipsViewModel tipsViewModel, TransactionRequester transactionRequester, ProtocolConfig protocolConfig) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.tipsViewModel = tipsViewModel;
        this.transactionRequester = transactionRequester;
        setMwm(protocolConfig.isTestnet(), protocolConfig.getMwm());
    }

    /**
     * Set the Minimum Weight Magnitude for validation checks.
     */
    @VisibleForTesting
    void setMwm(boolean testnet, int mwm) {
        minWeightMagnitude = mwm;

        //lowest allowed MWM encoded in 46 bytes.
        if (!testnet){
            minWeightMagnitude = Math.max(minWeightMagnitude, TESTNET_MWM_CAP);
        }
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
        if(transactionRequester.wasTransactionRecentlyRequested(transactionViewModel.getHash())) {
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
        }
    }

    /**
     * Tries to solidify the transactions quickly by performing {@link #checkApproovee} on both parents (trunk and
     * branch). If the parents are solid, mark the transactions as solid.
     * @param transactionViewModel transaction to solidify
     * @return <tt>true</tt> if we made the transaction solid, else <tt>false</tt>.
     * @throws Exception
     */
    public boolean quickSetSolid(final TransactionViewModel transactionViewModel) throws Exception {
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
        if(snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(approovee.getHash())) {
            return true;
        }
        if(approovee.getType() == PREFILLED_SLOT) {
            // don't solidify from the bottom until cuckoo filters can identify where we deleted -> otherwise we will
            // continue requesting old transactions forever
            //transactionRequester.requestTransaction(approovee.getHash(), false);
            return false;
        }
        return approovee.isSolid();
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