package com.iota.iri.network.pipeline;

import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;

import java.util.Arrays;

/**
 * Defines a payload whic gets submitted to the {@link ValidationStage}.
 */
public class ValidationPayload extends Payload {

    private Neighbor neighbor;
    private byte[] txTrits;
    private byte[] hashTrits;
    private Long txBytesDigest;
    private Hash hashOfRequestedTx;

    /**
     * Creates a new {@link ValidationStage}.
     *
     * @param neighbor          the {@link Neighbor} from which the transaction originated from (can be null)
     * @param txTrits           the trits representation of the transaction
     * @param hashTrits         the hash of the transaction in trits representation
     * @param txBytesDigest     the digest of the tansaction payload
     * @param hashOfRequestedTx the hash of the requested transaction
     */
    public ValidationPayload(Neighbor neighbor, byte[] txTrits, byte[] hashTrits, Long txBytesDigest,
            Hash hashOfRequestedTx) {
        this.neighbor = neighbor;
        this.txBytesDigest = txBytesDigest;
        this.txTrits = txTrits;
        this.hashTrits = hashTrits;
        this.hashOfRequestedTx = hashOfRequestedTx;
    }

    /**
     * {@inheritDoc}
     */
    public Neighbor getOriginNeighbor() {
        return neighbor;
    }

    /**
     * Gets the transaction trits.
     * 
     * @return the transaction trits
     */
    public byte[] getTxTrits() {
        return txTrits;
    }

    /**
     * Gets the transaction payload digest.
     * 
     * @return the transaction payload digest
     */
    public Long getTxBytesDigest() {
        return txBytesDigest;
    }

    /**
     * Gets the hash of the requested transaction.
     * 
     * @return the hash of the requested transaction.
     */
    public Hash getHashOfRequestedTx() {
        return hashOfRequestedTx;
    }

    /**
     * Gets the hash of the transaction.
     * 
     * @return the hash of the transaction.
     */
    public byte[] getHashTrits() {
        return hashTrits;
    }

    /**
     * Sets the transaction hash trits.
     * 
     * @param hashTrits the hash trits to set
     */
    public void setHashTrits(byte[] hashTrits) {
        this.hashTrits = hashTrits;
    }

    @Override
    public String toString() {
        return "ValidationPayload{" + "neighbor=" + neighbor.getHostAddressAndPort() + ", hashTrits="
                + Arrays.toString(hashTrits) + ", txBytesDigest=" + txBytesDigest + ", hashOfRequestedTx="
                + hashOfRequestedTx.toString() + '}';
    }
}
