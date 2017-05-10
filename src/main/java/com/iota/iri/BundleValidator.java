package com.iota.iri;

import com.iota.iri.controllers.HashesViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.utils.Converter;

import java.util.*;

public class BundleValidator {
    private final List<List<TransactionViewModel>> transactions = new LinkedList<>();
    private final HashesViewModel bundle;

    BundleValidator(HashesViewModel bundleHashes) throws Exception {
        this.bundle = bundleHashes;
    }

    public static BundleValidator load(HashesViewModel bundleHashes) throws Exception {
        BundleValidator bundleValidator = new BundleValidator(bundleHashes);
        bundleValidator.init();
        return bundleValidator;
    }

    private void init() throws Exception {

        final Map<Hash, TransactionViewModel> bundleTransactions = loadTransactionsFromTangle();

        for (TransactionViewModel transactionViewModel : bundleTransactions.values()) {

            if (transactionViewModel.getCurrentIndex() == 0 && transactionViewModel.getValidity() >= 0) {

                final List<TransactionViewModel> instanceTransactionViewModels = new LinkedList<>();

                final long lastIndex = transactionViewModel.getLastIndex();
                long bundleValue = 0;
                int i = 0;
                MAIN_LOOP:
                while (true) {

                    instanceTransactionViewModels.add(transactionViewModel);

                    if (transactionViewModel.getCurrentIndex() != i || transactionViewModel.getLastIndex() != lastIndex
                            || ((bundleValue += transactionViewModel.value()) < -TransactionViewModel.SUPPLY || bundleValue > TransactionViewModel.SUPPLY)) {
                        instanceTransactionViewModels.get(0).setValidity(-1);
                        break;
                    }

                    if (i++ == lastIndex) { // It's supposed to become -3812798742493 after 3812798742493 and to go "down" to -1 but we hope that noone will create such long bundles

                        if (bundleValue == 0) {

                            if (instanceTransactionViewModels.get(0).getValidity() == 0) {

                                final Curl bundleHash = new Curl();
                                for (final TransactionViewModel transactionViewModel2 : instanceTransactionViewModels) {
                                    bundleHash.absorb(transactionViewModel2.trits(), TransactionViewModel.ESSENCE_TRINARY_OFFSET, TransactionViewModel.ESSENCE_TRINARY_SIZE);
                                }
                                final int[] bundleHashTrits = new int[TransactionViewModel.BUNDLE_TRINARY_SIZE];
                                bundleHash.squeeze(bundleHashTrits, 0, bundleHashTrits.length);
                                if (instanceTransactionViewModels.get(0).getBundleHash().equals(new Hash(Converter.bytes(bundleHashTrits, 0, TransactionViewModel.BUNDLE_TRINARY_SIZE)))) {

                                    final int[] normalizedBundle = ISS.normalizedBundle(bundleHashTrits);

                                    for (int j = 0; j < instanceTransactionViewModels.size(); ) {

                                        transactionViewModel = instanceTransactionViewModels.get(j);
                                        if (transactionViewModel.value() < 0) { // let's recreate the address of the transactionViewModel.

                                            final Curl address = new Curl();
                                            int offset = 0;
                                            do {

                                                address.absorb(
                                                        ISS.digest(Arrays.copyOfRange(normalizedBundle, offset, offset = (offset + ISS.NUMBER_OF_FRAGMENT_CHUNKS) % (Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE)),
                                                                Arrays.copyOfRange(instanceTransactionViewModels.get(j).trits(), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE)),
                                                        0, Curl.HASH_LENGTH);

                                            } while (++j < instanceTransactionViewModels.size()
                                                    && instanceTransactionViewModels.get(j).getAddress().getHash().equals(transactionViewModel.getAddress().getHash())
                                                    && instanceTransactionViewModels.get(j).value() == 0);

                                            final int[] addressTrits = new int[TransactionViewModel.ADDRESS_TRINARY_SIZE];
                                            address.squeeze(addressTrits, 0, addressTrits.length);
                                            //if (!Arrays.equals(Converter.bytes(addressTrits, 0, TransactionViewModel.ADDRESS_TRINARY_SIZE), transactionViewModel.getAddress().getHash().bytes())) {
                                            if (! transactionViewModel.getAddress().getHash().equals(new Hash(Converter.bytes(addressTrits, 0, TransactionViewModel.ADDRESS_TRINARY_SIZE)))) {
                                                instanceTransactionViewModels.get(0).setValidity(-1);
                                                break MAIN_LOOP;
                                            }
                                        } else {
                                            j++;
                                        }
                                    }

                                    instanceTransactionViewModels.get(0).setValidity(1);
                                    transactions.add(instanceTransactionViewModels);
                                } else {
                                    instanceTransactionViewModels.get(0).setValidity(-1);
                                }
                            } else {
                                transactions.add(instanceTransactionViewModels);
                            }
                        } else {
                            instanceTransactionViewModels.get(0).setValidity(-1);
                        }
                        break;

                    } else {
                        transactionViewModel = bundleTransactions.get(transactionViewModel.getTrunkTransactionHash());
                        if (transactionViewModel == null) {
                            break;
                        }
                    }
                }
            }
        }
    }

    public boolean isInconsistent() {
        long value = 0;
        for (final List<TransactionViewModel> bundleTransactionViewModels : getTransactions()) {
            for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {
                if (bundleTransactionViewModel.value() != 0) {
                    value += bundleTransactionViewModel.value();
                }
            }
        }
        return (value != 0 || getTransactions().size() == 0);
    }

    private Map<Hash, TransactionViewModel> loadTransactionsFromTangle() {
        final Map<Hash, TransactionViewModel> bundleTransactions = new HashMap<>();
        try {
            for (final Hash transactionViewModel : this.bundle.getHashes()) {
                bundleTransactions.put(transactionViewModel, TransactionViewModel.fromHash(transactionViewModel));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bundleTransactions;
    }

    public List<List<TransactionViewModel>> getTransactions() {
        return transactions;
    }

    Set<TransactionViewModel> getTransactionViewModels() throws Exception {
        Set<TransactionViewModel> transactionViewModelSet = new HashSet<>();
        for(Hash hash: bundle.getHashes()) {
            transactionViewModelSet.add(TransactionViewModel.fromHash(hash));
        }
        return transactionViewModelSet;
    }
}
