package com.iota.iri;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.utils.Converter;

import java.util.Arrays;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;

public class TransactionTestUtils {

    private static Random seed = new Random();
    
    public static void setCurrentIndex(TransactionViewModel tx, long currentIndex) {
        Converter.copyTrits(currentIndex, tx.trits(), TransactionViewModel.CURRENT_INDEX_TRINARY_OFFSET,
                TransactionViewModel.CURRENT_INDEX_TRINARY_SIZE);
    }

    public static void setLastIndex(TransactionViewModel tx, long lastIndex) {
        Converter.copyTrits(lastIndex, tx.trits(), TransactionViewModel.LAST_INDEX_TRINARY_OFFSET,
                TransactionViewModel.LAST_INDEX_TRINARY_SIZE);
    }

    public static TransactionViewModel createBundleHead(int index) {
        
        TransactionViewModel tx = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        setLastIndex(tx, index);
        setCurrentIndex(tx, index);
        return tx;
    }

    public static TransactionViewModel createTransactionWithTrunkBundleHash(TransactionViewModel trunkTx, Hash branchHash) {
        TransactionViewModel tx = new TransactionViewModel(
                getRandomTransactionWithTrunkAndBranch(trunkTx.getHash(), branchHash),
                getRandomTransactionHash());
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
    
    public static Transaction getRandomTransactionFilledParsed(Random seed) {
        Transaction transaction = getRandomTransaction(seed);
        
        transaction.type = TransactionViewModel.FILLED_SLOT;
        transaction.parsed = true;
        return transaction;
    }
    
    public static Transaction getRandomTransaction(Random seed) {
        Transaction transaction = new Transaction();

        byte[] trits = new byte[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE];
        for(int i = 0; i < trits.length; i++) {
            trits[i] = (byte) (seed.nextInt(3) - 1);
        }

        transaction.bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, 0, transaction.bytes, 0, trits.length);
        return transaction;
    }
    
    public static byte[] getRandomTransactionWithTrunkAndBranch(Hash trunk, Hash branch) {
        byte[] trits = getRandomTransactionTrits();
        System.arraycopy(trunk.trits(), 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branch.trits(), 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        return trits;
    }
    
    public static byte[] getRandomTransactionTrits() {
        byte[] out = new byte[TransactionViewModel.TRINARY_SIZE];

        for(int i = 0; i < out.length; i++) {
            out[i] = (byte) (seed.nextInt(3) - 1);
        }

        return out;
    }

    public static Hash getRandomTransactionHash() {
        byte[] out = new byte[Hash.SIZE_IN_TRITS];

        for(int i = 0; i < out.length; i++) {
            out[i] = (byte) (seed.nextInt(3) - 1);
        }

        return HashFactory.TRANSACTION.create(out);
    }
    
    public static Transaction get9Transaction() {
        Transaction transaction = new Transaction();

        byte[] trits = new byte[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE];
        Arrays.fill(trits, (byte) 1);

        transaction.bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, 0, transaction.bytes, 0, trits.length);
        return transaction;
    }

    private static String expandTrytes(String trytes) {
        return trytes + StringUtils.repeat('9', TransactionViewModel.TRYTES_SIZE - trytes.length());
    }
    
    
}
