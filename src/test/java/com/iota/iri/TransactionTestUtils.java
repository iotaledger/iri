package com.iota.iri;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.StringUtils;

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

    public static TransactionViewModel createTransactionWithTrytes(String trytes) {
        String expandedTrytes  = expandTrytes(trytes);
        byte[] trits = Converter.allocatingTritsFromTrytes(expandedTrytes);
        return new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
    }

    public static TransactionViewModel createTransactionWithTrunkAndBranch(String trytes, Hash trunk, Hash branch) {
        String expandedTrytes = expandTrytes(trytes);
        byte[] trits =  Converter.allocatingTritsFromTrytes(expandedTrytes);
        System.arraycopy(trunk.trits(), 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branch.trits(), 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        return new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
    }

    public static String nextWord(String trytes) {
        if ("".equals(trytes)) {
            return "A";
        }
        char[] chars = trytes.toUpperCase().toCharArray();
        for (int i = chars.length -1; i>=0; --i) {
            if (chars[i] != 'Z') {
                ++chars[i];
                return new String(chars);
            }
        }
        return trytes + 'A';
    }

    private static String expandTrytes(String trytes) {
        return trytes + StringUtils.repeat('9', TransactionViewModel.TRYTES_SIZE - trytes.length());
    }
}
