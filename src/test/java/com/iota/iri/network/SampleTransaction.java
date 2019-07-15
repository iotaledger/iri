package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.network.protocol.ProtocolMessage;
import com.iota.iri.utils.Converter;
import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;

/**
 * A sample transaction for network code related tests.
 */
public class SampleTransaction {

    public final static int SIG_FILL = 4;
    public final static int SIG_FILLED_COUNT = 1000;
    public final static LongHashFunction xxHash = LongHashFunction.xx();
    public final static long BYTES_DIGEST_OF_SAMPLE_TX = xxHash.hashBytes(createSampleTxBuffer().array(), 0,
            ProtocolMessage.TRANSACTION_GOSSIP.getMaxLength() - Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH);
    public static byte[] TRITS_OF_SAMPLE_TX;
    public static byte[] BYTES_OF_SAMPLE_TX;
    public static byte[] TRUNCATED_SAMPLE_TX_BYTES;
    public static Hash CURL_HASH_OF_SAMPLE_TX;
    public static Transaction SAMPLE_TRANSACTION;

    static {
        TRITS_OF_SAMPLE_TX = new byte[TransactionViewModel.TRINARY_SIZE];
        BYTES_OF_SAMPLE_TX = new byte[Transaction.SIZE];
        System.arraycopy(createSampleTxBuffer().array(), 0, BYTES_OF_SAMPLE_TX, 0, Transaction.SIZE);
        Converter.getTrits(BYTES_OF_SAMPLE_TX, TRITS_OF_SAMPLE_TX);

        CURL_HASH_OF_SAMPLE_TX = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, TRITS_OF_SAMPLE_TX);

        byte[] raw = new byte[Transaction.SIZE];
        for (int i = 0; i < SIG_FILLED_COUNT; i++) {
            raw[i] = SIG_FILL;
        }
        TRUNCATED_SAMPLE_TX_BYTES = Protocol.truncateTx(raw);

        SAMPLE_TRANSACTION = new Transaction();
        SAMPLE_TRANSACTION.bytes = BYTES_OF_SAMPLE_TX;
        SAMPLE_TRANSACTION.type = TransactionViewModel.FILLED_SLOT;
        SAMPLE_TRANSACTION.parsed = true;
    }

    /**
     * Creates a byte buffer containing a sample transaction.
     * 
     * @return a byte buffer containing a sample transaction
     */
    public static ByteBuffer createSampleTxBuffer() {
        int size = ProtocolMessage.TRANSACTION_GOSSIP.getMaxLength();
        ByteBuffer buf = ByteBuffer.allocate(size);
        byte[] txBytes = new byte[size];
        for (int i = 0; i < SIG_FILLED_COUNT; i++) {
            txBytes[i] = SIG_FILL;
        }
        buf.put(txBytes);
        buf.flip();
        return buf;
    }

}
