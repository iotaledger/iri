package com.iota.iri.utils;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.model.persistables.Transaction;
import org.junit.Test;

import static org.junit.Assert.*;

public class TransactionTruncatorTest {

    @Test
    public void truncateByteEncodedTransaction() {
        byte[] originTxData = TransactionTestUtils.constructTransactionBytes();
        byte[] truncatedTx = TransactionTruncator.truncateTransaction(originTxData);
        assertEquals("should have the correct size after truncation",
                Transaction.SIZE - TransactionTestUtils.TRUNCATION_BYTES_COUNT,
                truncatedTx.length);

        for (int i = 0; i < TransactionTestUtils.NON_EMPTY_SIG_PART_BYTES_COUNT; i++) {
            assertEquals("non empty sig frag part should be intact", TransactionTestUtils.SIG_FILL, truncatedTx[i]);
        }
        for (int i = truncatedTx.length - 1; i > truncatedTx.length - TransactionTruncator.NON_SIG_TX_PART_BYTES_LENGTH; i--) {
            assertEquals("non sig frag part should be intact", TransactionTestUtils.REST_FILL, truncatedTx[i]);
        }
        for (int i = 0; i < truncatedTx.length; i++) {
            assertNotEquals(
                    "truncated tx should not have zero bytes (given the non sig frag part not containing zero bytes)",
                    TransactionTestUtils.EMPTY_FILL, truncatedTx[i]);
        }
    }

    @Test
    public void expandTruncatedByteEncodedTransaction() {
        int truncatedSize = 1000;
        byte[] truncatedTxData = new byte[truncatedSize];
        for (int i = 0; i < truncatedSize; i++) {
            truncatedTxData[i] = 3;
        }
        byte[] expandedTxData = TransactionTruncator.expandTransaction(truncatedTxData);

        // stuff after signature message fragment should be intact
        for (int i = expandedTxData.length - 1; i >= TransactionTruncator.SIG_DATA_MAX_BYTES_LENGTH; i--) {
            assertEquals("non sig data should be intact", 3, expandedTxData[i]);
        }

        // bytes between truncated signature message fragment and rest should be 0s
        int expandedBytesCount = truncatedSize - TransactionTruncator.NON_SIG_TX_PART_BYTES_LENGTH;
        for (int i = TransactionTruncator.SIG_DATA_MAX_BYTES_LENGTH - 1; i > expandedBytesCount; i--) {
            assertEquals("expanded sig frag should be filled with zero at the right positions", 0, expandedTxData[i]);
        }

        // origin signature message fragment should be intact
        for (int i = 0; i < TransactionTruncator.SIG_DATA_MAX_BYTES_LENGTH - expandedBytesCount; i++) {
            assertEquals("origin sig frag should be intact", 3, expandedTxData[i]);
        }
    }
}
