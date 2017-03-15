package com.iota.iri;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import com.iota.iri.utils.Converter;

/**
 * A bundle is a group of transactions that follow each other from
 * currentIndex=0 to currentIndex=lastIndex
 * 
 * several bundles can form a single branch if they are chained.
 */
public class Bundle {

    private final List<List<TransactionViewModel>> transactions = new LinkedList<>();

    public Bundle(final byte[] bundle) throws Exception {

        final BundleViewModel bundleViewModel = BundleViewModel.fromHash(bundle);
        /*
        final TransactionViewModel bundleTransactionViewModel = TransactionViewModel.fromHash(bundle);
        if(bundleTransactionViewModel == null) {
            return;
        }
        final Map<byte[], TransactionViewModel> bundleTransactions = loadTransactionsFromTangle(bundleTransactionViewModel);
        */
        final Map<byte[], TransactionViewModel> bundleTransactions = loadTransactionsFromTangle(bundleViewModel);

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
                        instanceTransactionViewModels.get(0).setValidity(-1, true);
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
                                if (Arrays.equals(Converter.bytes(bundleHashTrits, 0, TransactionViewModel.BUNDLE_TRINARY_SIZE), instanceTransactionViewModels.get(0).getBundleHash())) {

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
                                                    && Arrays.equals(instanceTransactionViewModels.get(j).getAddress().getHash().bytes(), transactionViewModel.getAddress().getHash().bytes())
                                                    && instanceTransactionViewModels.get(j).value() == 0);
                                            
                                            final int[] addressTrits = new int[TransactionViewModel.ADDRESS_TRINARY_SIZE];
                                            address.squeeze(addressTrits, 0, addressTrits.length);
                                            if (!Arrays.equals(Converter.bytes(addressTrits, 0, TransactionViewModel.ADDRESS_TRINARY_SIZE), transactionViewModel.getAddress().getHash().bytes())) {
                                                instanceTransactionViewModels.get(0).setValidity(-1, true);
                                                break MAIN_LOOP;
                                            }
                                        } else {
                                            j++;
                                        }
                                    }

                                    instanceTransactionViewModels.get(0).setValidity(1, true);
                                    transactions.add(instanceTransactionViewModels);
                                } else {
                                    instanceTransactionViewModels.get(0).setValidity(-1, true);
                                }
                            } else {
                                transactions.add(instanceTransactionViewModels);
                            }
                        } else {
                            instanceTransactionViewModels.get(0).setValidity(-1, true);
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


    private Map<byte[], TransactionViewModel> loadTransactionsFromTangle(final BundleViewModel bundleViewModel) {
        final Map<byte[], TransactionViewModel> bundleTransactions = new HashMap<>();
        try {
            for (final TransactionViewModel transactionViewModel : bundleViewModel.getTransactions()) {
                bundleTransactions.put(transactionViewModel.getHash(), transactionViewModel);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bundleTransactions;
    }
    
    public List<List<TransactionViewModel>> getTransactions() {
        return transactions;
    }
}
