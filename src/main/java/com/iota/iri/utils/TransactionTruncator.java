package com.iota.iri.utils;

import com.iota.iri.model.persistables.Transaction;

public class TransactionTruncator {

    /**
     * The max amount of bytes a signature message fragment is made up from.
     */
    public final static int SIG_DATA_MAX_BYTES_LENGTH = 1312;
    /**
     * The amount of bytes making up the non signature message fragment part of a transaction.
     */
    public final static int NON_SIG_TX_PART_BYTES_LENGTH = 292;

    /**
     * Truncates the given byte encoded transaction by removing unneeded bytes from the signature message fragment.
     *
     * @param txBytes the transaction bytes to truncate
     * @return an array containing the truncated transaction data
     */
    public static byte[] truncateTransaction(byte[] txBytes) {
        // check how many bytes from the signature can be truncated
        int bytesToTruncate = 0;
        for (int i = SIG_DATA_MAX_BYTES_LENGTH - 1; i >= 0; i--) {
            if (txBytes[i] != 0) {
                break;
            }
            bytesToTruncate++;
        }
        // allocate space for truncated tx
        byte[] truncatedTx = new byte[SIG_DATA_MAX_BYTES_LENGTH - bytesToTruncate + NON_SIG_TX_PART_BYTES_LENGTH];
        System.arraycopy(txBytes, 0, truncatedTx, 0, SIG_DATA_MAX_BYTES_LENGTH - bytesToTruncate);
        System.arraycopy(txBytes, SIG_DATA_MAX_BYTES_LENGTH, truncatedTx, SIG_DATA_MAX_BYTES_LENGTH - bytesToTruncate,
                NON_SIG_TX_PART_BYTES_LENGTH);
        return truncatedTx;
    }

    /**
     * Expands an array containing a truncated transaction using a given reference size
     * to determine the amount of bytes to pad.
     *
     * @param data          the truncated transaction data to be expanded
     * @param referenceSize the max size to use as a reference to compute the bytes to be added
     * @return an array containing the expanded transaction data
     */
    public static byte[] expandTransaction(byte[] data, int referenceSize) {
        byte[] txDataBytes = new byte[Transaction.SIZE];
        int numOfBytesOfSigMsgFragToExpand = referenceSize - data.length;
        byte[] sigMsgFragPadding = new byte[numOfBytesOfSigMsgFragToExpand];
        int sigMsgFragBytesToCopy = data.length - TransactionTruncator.NON_SIG_TX_PART_BYTES_LENGTH;

        // build up transaction payload. empty signature message fragment equals padding with 1312x 0 bytes
        System.arraycopy(data, 0, txDataBytes, 0, sigMsgFragBytesToCopy);
        System.arraycopy(sigMsgFragPadding, 0, txDataBytes, sigMsgFragBytesToCopy, sigMsgFragPadding.length);
        System.arraycopy(data, sigMsgFragBytesToCopy, txDataBytes, TransactionTruncator.SIG_DATA_MAX_BYTES_LENGTH,
                TransactionTruncator.NON_SIG_TX_PART_BYTES_LENGTH);
        return txDataBytes;
    }

    /**
     * Expands an array containing a truncated transaction.
     *
     * @param data the truncated transaction data to be expanded
     * @return an array containing the expanded transaction data
     */
    public static byte[] expandTransaction(byte[] data) {
        return expandTransaction(data, Transaction.SIZE);
    }

}
