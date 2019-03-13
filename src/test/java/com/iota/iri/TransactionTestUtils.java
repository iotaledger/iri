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

    private static Random seed = new Random(1);
    
    /**
     * Updates the transaction index in trits.
     * 
     * @param tx The transaction to update
     * @param currentIndex The new index to set the transaction to
     */
    public static void setCurrentIndex(TransactionViewModel tx, long currentIndex) {
        setCurrentIndex(tx.trits(), currentIndex);
    }

    public static void setCurrentIndex(byte[] trits, long currentIndex) {
        Converter.copyTrits(currentIndex, trits, TransactionViewModel.CURRENT_INDEX_TRINARY_OFFSET,
                TransactionViewModel.CURRENT_INDEX_TRINARY_SIZE);
    }

    /**
     * Updates the last transaction index in trits.
     * 
     * @param tx The transaction to update
     * @param lastIndex The new last index to set the transaction to
     */
    public static void setLastIndex(TransactionViewModel tx, long lastIndex) {
        setLastIndex(tx.trits(), lastIndex);
    }

    public static void setLastIndex(byte[] trits, long lastIndex) {
        Converter.copyTrits(lastIndex, trits, TransactionViewModel.LAST_INDEX_TRINARY_OFFSET,
                TransactionViewModel.LAST_INDEX_TRINARY_SIZE);
    }

    /**
     * Generates a random transaction with a random hash.
     * Transaction last and current index are set to the index provided.
     * 
     * @param index The index to set the transaction to
     * @return A random transaction which is located on the end of its (nonexistent) bundle
     */
    public static TransactionViewModel createBundleHead(int index) {
        
        TransactionViewModel tx = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        setLastIndex(tx, index);
        setCurrentIndex(tx, index);
        return tx;
    }

    /**
     * Generates a transaction with the specified trunk and branch, and bundle hash from trunk.
     * This transaction indices are updated to match the trunk index.
     * 
     * @param trunkTx The trunk transaction
     * @param branchHash The branch transaction hash
     * @return A transaction in the same bundle as trunk, with its index 1 below trunk index
     */
    public static TransactionViewModel createTransactionWithTrunkBundleHash(TransactionViewModel trunkTx, Hash branchHash) {
        byte[] txTrits = getTransactionWithTrunkAndBranch(trunkTx.getHash(), branchHash);
        setCurrentIndex(txTrits, trunkTx.getCurrentIndex() - 1);
        setLastIndex(txTrits, trunkTx.lastIndex());
        System.arraycopy(trunkTx.trits(), TransactionViewModel.BUNDLE_TRINARY_OFFSET, txTrits,
                TransactionViewModel.BUNDLE_TRINARY_OFFSET, TransactionViewModel.BUNDLE_TRINARY_SIZE);
        TransactionViewModel tx = new TransactionViewModel(
                txTrits,
                getRandomTransactionHash());

        return tx;
    }

    /**
     * Generates a transaction with the provided trytes.
     * If the trytes are not enough to make a full transaction, 9s are appended.
     * Transaction hash is calculated and added.
     * 
     * @param trytes The transaction trytes to use
     * @return The transaction
     */
    public static TransactionViewModel createTransactionWithTrytes(String trytes) {
        String expandedTrytes  = expandTrytes(trytes);
        byte[] trits = Converter.allocatingTritsFromTrytes(expandedTrytes);
        return createTransactionFromTrits(trits);
    }

    public static TransactionViewModel createTransactionFromTrits(byte[] trits) {
        return new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
    }

    /**
     * Generates a transaction with the provided trytes, trunk and hash.
     * If the trytes are not enough to make a full transaction, 9s are appended.
     * Transaction hash is calculated and added.
     * 
     * @param trytes The transaction trytes to use
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return The transaction
     */
    public static TransactionViewModel createTransactionWithTrunkAndBranch(String trytes, Hash trunk, Hash branch) {
        byte[] trits = createTransactionWithTrunkAndBranchTrits(trytes, trunk, branch);
        return new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
    }
    
    /**
     * Generates transaction trits with the provided trytes, trunk and hash.
     * If the trytes are not enough to make a full transaction, 9s are appended.
     * 
     * @param trytes The transaction trytes to use
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return The transaction trits
     */
    public static byte[] createTransactionWithTrunkAndBranchTrits(String trytes, Hash trunk, Hash branch) {
        String expandedTrytes = expandTrytes(trytes);
        byte[] trits =  Converter.allocatingTritsFromTrytes(expandedTrytes);
        return getTransactionTritsWithTrunkAndBranch(trits, trunk, branch);
    }
    
    /**
     * Generates random transaction trits with the provided trytes, trunk and hash.
     * 
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return The transaction trits
     */
    public static byte[] getTransactionWithTrunkAndBranch(Hash trunk, Hash branch) {
        byte[] trits = new byte[TransactionViewModel.TRINARY_SIZE];
        return getTransactionTritsWithTrunkAndBranch(trits, trunk, branch);
    }
    
    /**
     * Generates transaction trits with the provided trits, trunk and hash.
     * 
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return trits The transaction trits
     */
    public static byte[] getTransactionTritsWithTrunkAndBranch(byte[] trits, Hash trunk, Hash branch) {
        System.arraycopy(trunk.trits(), 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branch.trits(), 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        return trits;
    }

    /**
     * Increases a char with the next char in the alphabet, until the char is Z.
     * When the char is Z, adds a new char starting at A.
     * 9 turns To A.
     * 
     * @param trytes The Trytes to change.
     * @return The changed trytes
     */
    public static String nextWord(String trytes) {
        if ("".equals(trytes)) {
            return "A";
        }
        char[] chars = trytes.toUpperCase().toCharArray();
        for (int i = chars.length -1; i>=0; --i) {
            if (chars[i] == '9') {
                chars[i] = 'A';
            }
            else if (chars[i] != 'Z') {
                ++chars[i];
                return new String(chars);
            }
        }
        return trytes + 'A';
    }
    
    /**
     * Generates a random transaction.
     * 
     * @return The transaction
     */
    public static Transaction getRandomTransaction() {
        byte[] trits = getRandomTransactionTrits();
        return buildTransaction(trits);
    }
    
    /**
     * Generates random trits for a transaction.
     * 
     * @return The transaction trits
     */
    public static byte[] getRandomTransactionTrits() {
        return getRandomTrits(TransactionViewModel.TRINARY_SIZE);
    }
    
    /**
     * Generates a transaction with only 9s.
     * 
     * @return The transaction
     */
    public static Transaction get9Transaction() {
        byte[] trits = new byte[TransactionViewModel.TRINARY_SIZE];
        Arrays.fill(trits, (byte) 0);

        return buildTransaction(trits);
    }

    /**
     * Generates random trits for a transaction.
     * 
     * @return The transaction hash
     */
    public static Hash getRandomTransactionHash() {
        byte[] out = getRandomTrits(Hash.SIZE_IN_TRITS);
        return HashFactory.TRANSACTION.create(out);
    }
    
    /**
     * Builds a transaction by transforming trits to bytes.
     * 
     * @param trits The trits to build the transaction
     * @return The created transaction
     */
    public static Transaction buildTransaction(byte[] trits) {
        Transaction transaction = new Transaction();
        
        transaction.bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, 0, transaction.bytes, 0, trits.length);
        transaction.readMetadata( transaction.bytes);
        return transaction;
    }

    /**
     * Appends 9s to the supplied trytes until the trytes are of size {@link TransactionViewModel.TRYTES_SIZE}.
     * 
     * @param trytes the trytes to append to.
     * @return The expanded trytes string
     */
    private static String expandTrytes(String trytes) {
        return trytes + StringUtils.repeat('9', TransactionViewModel.TRYTES_SIZE - trytes.length());
    }
    
    /**
     * Generates random trits of specified size.
     * 
     * @param size the amount of trits to generate
     * @return The trits
     */
    private static byte[] getRandomTrits(int size) {
        byte[] out = new byte[size];

        for(int i = 0; i < out.length; i++) {
            out[i] = (byte) (seed.nextInt(3) - 1);
        }
        return out;
    }
}
