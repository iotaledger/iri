package com.iota.iri;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;

public class TransactionTestUtils {

    public static void setCurrentIndex(TransactionViewModel tx, long currentIndex) {
        Converter.copyTrits(currentIndex, tx.trits(), TransactionViewModel.CURRENT_INDEX_TRINARY_OFFSET,
                TransactionViewModel.CURRENT_INDEX_TRINARY_SIZE);
    }

    public static void setLastIndex(TransactionViewModel tx, long lastIndex) {
        Converter.copyTrits(lastIndex, tx.trits(), TransactionViewModel.LAST_INDEX_TRINARY_OFFSET,
                TransactionViewModel.LAST_INDEX_TRINARY_SIZE);
    }

    public static TransactionViewModel createBundleHead(int index) {
        TransactionViewModel tx = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionTrits(), TransactionViewModelTest.getRandomTransactionHash());
        setLastIndex(tx, index);
        setCurrentIndex(tx, index);
        return tx;
    }

    public static TransactionViewModel createTransactionWithTrunkBundleHash(TransactionViewModel trunkTx, Hash branchHash) {
        TransactionViewModel tx = new TransactionViewModel(
                TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(trunkTx.getHash(), branchHash),
                TransactionViewModelTest.getRandomTransactionHash());
        setCurrentIndex(tx, trunkTx.getCurrentIndex() - 1);
        setLastIndex(tx, trunkTx.lastIndex());
        System.arraycopy(trunkTx.trits(), TransactionViewModel.BUNDLE_TRINARY_OFFSET, tx.trits(),
                TransactionViewModel.BUNDLE_TRINARY_OFFSET, TransactionViewModel.BUNDLE_TRINARY_SIZE);
        return tx;
    }
}
