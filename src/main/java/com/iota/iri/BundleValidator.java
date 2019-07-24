package com.iota.iri;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.*;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;

import java.util.*;

/**
 * Validates bundles.
 * <p>
 * Bundles are lists of transactions that represent an atomic transfer, meaning that either all transactions inside the
 * bundle will be accepted by the network, or none. All transactions in a bundle have the same bundle hash and are
 * chained together via their trunks.
 * </p>
 */
public class BundleValidator {

    /**
     * Instructs the validation code to validate the signatures of the bundle.
     */
    public static final int MODE_VALIDATE_SIGNATURES = 1;

    /**
     * Instructs the validation code to compare the computed bundle hash from the essence data against
     * the tail transaction's bundle hash.
     */
    public static final int MODE_VALIDATE_BUNDLE_HASH = 1 << 1;

    /**
     * Instructs the validation code to check the integrity of the bundle by checking current/last index positions,
     * making sure that every transaction of the the bundle is present, value transaction's address ends with a 0 trit,
     * the bundle's aggregation of value doesn't exceed the max supply of tokens and the sum of the bundle equals 0.
     */
    public static final int MODE_VALIDATE_SEMANTICS = 1 << 2;

    /**
     * Instructs the validation code to fully validate the semantics, bundle hash and signatures of the given bundle.
     */
    public static final int MODE_VALIDATE_ALL = MODE_VALIDATE_SIGNATURES | MODE_VALIDATE_BUNDLE_HASH | MODE_VALIDATE_SEMANTICS;

    /**
     * Instructs the validation code to skip checking the bundle's already computed validity and instead to proceed to
     * validate the bundle further.
     */
    public static final int MODE_SKIP_CACHED_VALIDITY = 1 << 3;

    /**
     * Instructs the validation code to skip checking whether the tail transaction is present or a tail transaction
     * was given as the start transaction.
     */
    public static final int MODE_SKIP_TAIL_TX_EXISTENCE = 1 << 4;

    /**
     * Fetches a bundle of transactions identified by the {@code tailHash} and validates the transactions. Bundle is a
     * group of transactions with the same bundle hash chained by their trunks.
     * <p>
     * The fetched transactions have the same bundle hash as the transaction identified by {@code tailHash} The
     * validation does the following semantic checks:
     * <ol>
     * <li>The absolute bundle value never exceeds the total, global supply of iotas</li>
     * <li>The last trit when we convert from binary</li>
     * <li>Total bundle value is 0 (inputs and outputs are balanced)</li>
     * <li>Recalculate the bundle hash by absorbing and squeezing the transactions' essence</li>
     * <li>Validate the signature on input transactions</li>
     * </ol>
     * <p>
     * As well as the following syntactic checks:
     * <ol>
     * <li>{@code tailHash} has an index of 0</li>
     * <li>The transactions' reference order is consistent with the indexes</li>
     * <li>The last index of each transaction in the bundle matches the last index of the tail transaction</li>
     * <li>Check that last trit in a valid address hash is 0. We generate addresses using binary Kerl and
     * we lose the last trit in the process</li>
     * </ol>
     *
     * @param tangle          used to fetch the bundle's transactions from the persistence layer
     * @param initialSnapshot the initial snapshot that defines the genesis for our ledger state
     * @param tailHash        the hash of the last transaction in a bundle.
     * @return A list of transactions of the bundle contained in another list. If the bundle is valid then the tail
     * transaction's {@link TransactionViewModel#getValidity()} will return 1, else {@link
     * TransactionViewModel#getValidity()} will return -1. If the bundle is invalid then an empty list will be
     * returned.
     * @throws Exception if a persistence error occurred
     * @implNote if {@code tailHash} was already invalidated/validated by a previous call to this method then we don't
     * validate it again.
     * </p>
     */
    public List<TransactionViewModel> validate(Tangle tangle, Snapshot initialSnapshot, Hash tailHash) throws Exception {
        List<TransactionViewModel> bundleTxs = new LinkedList<>();
        if(validate(tangle, tailHash, MODE_VALIDATE_ALL, bundleTxs)){
            if (bundleTxs.get(0).getValidity() != 1) {
                // the bundle and its signatures is valid, therefore mark it in the database
                System.out.println("poop");
                bundleTxs.get(0).setValidity(tangle, initialSnapshot, 1);
            }
            return bundleTxs;
        }
        if (!bundleTxs.isEmpty() &&  bundleTxs.get(0).getCurrentIndex() == 0 && bundleTxs.get(0).getValidity() != -1) {
            bundleTxs.get(0).setValidity(tangle, initialSnapshot, -1);
        }
        return Collections.EMPTY_LIST;
    }

    private static boolean hasMode(int mode, int has) {
        return (mode & has) == has;
    }

    /**
     * <p>
     *  Loads the rest of the bundle of the given start transaction and then validates the bundle
     *  given the mode of validation.
     * </p>
     * <p>
 *      Note that this method does not update the validity flag of the given transaction in the database.
     * </p>
     *
     * @param tangle used to fetch the bundle's transactions from the persistence layer
     * @param startTxHash the hash of the entrypoint transaction, must be the tail transaction if {@link BundleValidator#MODE_SKIP_TAIL_TX_EXISTENCE} is not used
     * @param validationMode the validation mode defining the level of validation done with the loaded up bundle
     * @param bundleTxs a list which gets filled with the transactions of the bundle
     * @return whether the validation criteria were passed or not
     * @throws Exception if an error occurred in the persistence layer
     */
    public boolean validate(Tangle tangle, Hash startTxHash, int validationMode, List<TransactionViewModel> bundleTxs) throws Exception {
        TransactionViewModel startTx = TransactionViewModel.fromHash(tangle, startTxHash);
        if (startTx == null || (!hasMode(validationMode, MODE_SKIP_TAIL_TX_EXISTENCE) && (startTx.getCurrentIndex() != 0 || startTx.getValidity() == -1))) {
            return false;
        }

        // load up the bundle by going through the trunks (note that we might not load up the entire bundle in case we
        // were instructed to not check whether we actually got the tail transaction)
        final Map<Hash, TransactionViewModel> bundleTxsMapping = loadTransactionsFromTangle(tangle, startTx);

        // check the semantics of the bundle: total sum, semantics per tx (current/last index), missing txs, supply
        boolean bundleSemanticsValidity = checkBundleSemantics(startTx, bundleTxsMapping, bundleTxs, validationMode);
        if (hasMode(validationMode, MODE_VALIDATE_SEMANTICS) && !bundleSemanticsValidity) {
            return false;
        }

        // return if the bundle's validity was computed before
        if (!hasMode(validationMode, MODE_SKIP_CACHED_VALIDITY)) {
            switch(startTx.getValidity()){
                case 1:
                    return true;
                case -1:
                    return false;
                default:
                    // validate further
            }
        }

        // compute the normalized bundle hash used to verify the signatures
        final byte[] normalizedBundle = new byte[Curl.HASH_LENGTH / ISS.TRYTE_WIDTH];
        boolean bundleHashValidity = computeNormalizedBundleHash(bundleTxs, normalizedBundle, validationMode);
        if (hasMode(validationMode, MODE_VALIDATE_BUNDLE_HASH) && !bundleHashValidity) {
            return false;
        }

        // verify the signatures of input transactions
        if(hasMode(validationMode, MODE_VALIDATE_SIGNATURES)){
            return validateSignatures(bundleTxs, normalizedBundle);
        }

        return true;
    }

    /**
     * <p>
     * Checks the bundle's semantic validity by checking current/last index positions,
     * making sure that every transaction of the the bundle is present, value transaction addresses end with a 0 trit,
     * the bundle's aggregation of value doesn't exceed the max supply of tokens and the sum of the bundle equals 0.
     * </p>
     *
     * <p>
     * Note that if the validation mode does not include {@link BundleValidator#MODE_VALIDATE_SEMANTICS}, this method
     * will basically just compute an ordered list of the transactions and will not do any actual validation of any kind.
     * </p>
     *
     * @param startTx the start transaction from which to built the bundle up from
     * @param bundleTxsMapping a mapping of the transaction hashes to the actual transactions
     * @param bundleTxs a list which gets filled with the transactions in order of trunk ordering
     * @param validationMode the used validation mode
     * @return whether the bundle is semantically valid
     */
    private static boolean checkBundleSemantics(TransactionViewModel startTx, Map<Hash, TransactionViewModel> bundleTxsMapping, List<TransactionViewModel> bundleTxs, int validationMode) {
        TransactionViewModel tvm = startTx;
        final long lastIndex = tvm.lastIndex();
        long bundleValue = 0;

        if(!hasMode(validationMode, MODE_VALIDATE_SEMANTICS)){
            for (int i = 0; i <= lastIndex; tvm = bundleTxsMapping.get(tvm.getTrunkTransactionHash()), i++) {
                // this will always be hit and terminate the for loop by returning
                if (tvm == null) {
                    return true;
                }
                bundleTxs.add(tvm);
            }
        }

        // iterate over all transactions of the bundle and do some basic semantic checks
        for (int i = 0; i <= lastIndex; tvm = bundleTxsMapping.get(tvm.getTrunkTransactionHash()), i++) {

            if (tvm == null) {
                // we miss a transaction, abort
                return false;
            }

            bundleTxs.add(tvm);

            // semantic checks
            if (
                    tvm.getCurrentIndex() != i
                            || tvm.lastIndex() != lastIndex
                            || ((bundleValue = Math.addExact(bundleValue, tvm.value())) < -TransactionViewModel.SUPPLY
                            || bundleValue > TransactionViewModel.SUPPLY)
            ) {
                    return false;
            }

            // we lose the last trit by converting from bytes
            if (tvm.value() != 0 && tvm.getAddressHash().trits()[Curl.HASH_LENGTH - 1] != 0) {
                return false;
            }
        }

        // total bundle value sum must be 0
        if (bundleValue != 0) {
            return false;
        }

        return true;
    }

    /**
     * <p>
     * Computes the normalized bundle hash of the given bundle transactions using the essence data and writes it into
     * the given normalizedBundleHash byte array.
     * </p>
     * <p>
     * Note that if the validation mode does not include {@link BundleValidator#MODE_VALIDATE_BUNDLE_HASH}, this method
     * will compute the bundle hash and write it into the normalizedBundleHash parameter, even if it is not valid.
     * </p>
     *
     * @param bundleTxs a list of ordered (by index) bundle transactions
     * @param normalizedBundleHash an array in which the normalized bundle hash is written into
     * @param validationMode the used validation mode
     * @return whether the bundle hash of the first transaction in the list corresponds to the computed bundle hash
     */
    private static boolean computeNormalizedBundleHash(List<TransactionViewModel> bundleTxs, byte[] normalizedBundleHash, int validationMode) {
        final Sponge curlInstance = SpongeFactory.create(SpongeFactory.Mode.KERL);
        final byte[] bundleHashTrits = new byte[TransactionViewModel.BUNDLE_TRINARY_SIZE];

        // compute actual bundle hash
        for (final TransactionViewModel tvm2 : bundleTxs) {
            curlInstance.absorb(tvm2.trits(), TransactionViewModel.ESSENCE_TRINARY_OFFSET, TransactionViewModel.ESSENCE_TRINARY_SIZE);
        }
        curlInstance.squeeze(bundleHashTrits, 0, bundleHashTrits.length);

        // compare the computed bundle hash against the tail transaction's
        if (hasMode(validationMode, MODE_VALIDATE_BUNDLE_HASH) && !Arrays.equals(bundleTxs.get(0).getBundleHash().trits(), bundleHashTrits)) {
            return false;
        }

        // normalizing the bundle in preparation for signature verification
        ISSInPlace.normalizedBundle(bundleHashTrits, normalizedBundleHash);
        return true;
    }

    /**
     * Validates the signatures of the given bundle transactions. The transactions must be ordered by index.
     *
     * @param bundleTxs a list of ordered (by index) bundle transactions
     * @param normalizedBundle the normalized bundle hash
     * @return whether all signatures were valid given the bundle hash and addresses
     */
    private static boolean validateSignatures(List<TransactionViewModel> bundleTxs, byte[] normalizedBundle) {
        final Sponge addressInstance = SpongeFactory.create(SpongeFactory.Mode.KERL);
        final byte[] addressTrits = new byte[TransactionViewModel.ADDRESS_TRINARY_SIZE];
        final byte[] digestTrits = new byte[Curl.HASH_LENGTH];
        TransactionViewModel tvm;
        for (int j = 0; j < bundleTxs.size(); ) {

            // iterate until next input transaction
            tvm = bundleTxs.get(j);
            if (tvm.value() >= 0) {
                j++;
                continue;
            }

            // verify the signature of the input address by computing the address
            addressInstance.reset();
            int offset = 0, offsetNext = 0;
            do {
                offsetNext = (offset + ISS.NUMBER_OF_FRAGMENT_CHUNKS - 1) % (Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE) + 1;
                ISSInPlace.digest(SpongeFactory.Mode.KERL,
                        normalizedBundle,
                        offset % (Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE),
                        bundleTxs.get(j).trits(),
                        TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET,
                        digestTrits);
                addressInstance.absorb(digestTrits, 0, Curl.HASH_LENGTH);
                offset = offsetNext;
            } //loop to traverse signature fragments divided between transactions
            while (++j < bundleTxs.size()
                    && bundleTxs.get(j).getAddressHash().equals(tvm.getAddressHash())
                    && bundleTxs.get(j).value() == 0);

            addressInstance.squeeze(addressTrits, 0, addressTrits.length);

            // verify the signature: compare the address against the computed address
            // derived from the signature/bundle hash
            if (!Arrays.equals(tvm.getAddressHash().trits(), addressTrits)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks that the bundle's inputs and outputs are balanced.
     *
     * @param transactionViewModels collection of transactions that are in a bundle
     * @return {@code true} if balanced, {@code false} if unbalanced or {@code transactionViewModels} is empty
     */
    public static boolean isInconsistent(Collection<TransactionViewModel> transactionViewModels) {
        long value = 0;
        for (final TransactionViewModel bundleTransactionViewModel : transactionViewModels) {
            if (bundleTransactionViewModel.value() != 0) {
                value += bundleTransactionViewModel.value();
                /*
                if(!milestone && bundleTransactionViewModel.getAddressHash().equals(Hash.NULL_HASH) && bundleTransactionViewModel.snapshotIndex() == 0) {
                    return true;
                }
                */
            }
        }
        return (value != 0 || transactionViewModels.size() == 0);
    }

    /**
     * Traverses down the given {@code tail} trunk until all transactions that belong to the same bundle (identified by
     * the bundle hash) are found and loaded.
     *
     * @param tangle connection to the persistence layer
     * @param tail   should be the last transaction of the bundle
     * @return map of all transactions in the bundle, mapped by their transaction hash
     */
    private static Map<Hash, TransactionViewModel> loadTransactionsFromTangle(Tangle tangle, TransactionViewModel tail) {
        final Map<Hash, TransactionViewModel> bundleTransactions = new HashMap<>();
        final Hash bundleHash = tail.getBundleHash();
        try {
            TransactionViewModel tx = tail;
            long i = tx.getCurrentIndex(), end = tx.lastIndex();
            do {
                bundleTransactions.put(tx.getHash(), tx);
                tx = tx.getTrunkTransaction(tangle);
            } while (i++ < end && tx.getCurrentIndex() != 0 && tx.getBundleHash().equals(bundleHash));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bundleTransactions;
    }
}
