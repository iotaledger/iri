package com.iota.iri.service.validation;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.conf.ProtocolConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.Sponge;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;

/**
 * Tool for determining validity of a transaction via a {@link TransactionViewModel}, tryte array or byte array.
 */
public class TransactionValidator {
    private static final int  TESTNET_MWM_CAP = 13;

    private final SnapshotProvider snapshotProvider;
    private final TransactionRequester transactionRequester;
    private int minWeightMagnitude = 81;
    private static final long MAX_TIMESTAMP_FUTURE = 2L * 60L * 60L;
    private static final long MAX_TIMESTAMP_FUTURE_MS = MAX_TIMESTAMP_FUTURE * 1_000L;


    /**
     * Constructor for Tangle Validator
     *
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param transactionRequester used to request missing transactions from neighbors
     * @param protocolConfig used for checking if we are in testnet and mwm. testnet <tt>true</tt> if we are in testnet
     *                       mode, this caps {@code mwm} to {@value #TESTNET_MWM_CAP} regardless of parameter input.
     *                       minimum weight magnitude: the minimal number of 9s that ought to appear at the end of the
     *                       transaction hash
     */
    public TransactionValidator(SnapshotProvider snapshotProvider, TransactionRequester transactionRequester, ProtocolConfig protocolConfig) {
        this.snapshotProvider = snapshotProvider;
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
        if (hasInvalidTimestamp(transactionViewModel)) {
            throw new StaleTimestampException("Invalid transaction timestamp.");
        }

        confirmValidTransactionValues(transactionViewModel);
        int weightMagnitude = transactionViewModel.weightMagnitude;
        if (weightMagnitude < minWeightMagnitude) {
            throw new IllegalStateException("Invalid transaction hash");
        }

        if (transactionViewModel.value() != 0 && transactionViewModel.getAddressHash().trits()[Curl.HASH_LENGTH - 1] != 0) {
            throw new IllegalStateException("Invalid transaction address");
        }
    }

    private void confirmValidTransactionValues(TransactionViewModel transactionViewModel) throws IllegalStateException {
        for (int i = TransactionViewModel.VALUE_TRINARY_OFFSET + TransactionViewModel.VALUE_USABLE_TRINARY_SIZE;
             i < TransactionViewModel.VALUE_TRINARY_OFFSET + TransactionViewModel.VALUE_TRINARY_SIZE; i++) {
            if (transactionViewModel.trits()[i] != 0) {
                throw new IllegalStateException("Invalid transaction value");
            }
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
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(bytes,
                TransactionViewModel.TRINARY_SIZE, curl));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
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
