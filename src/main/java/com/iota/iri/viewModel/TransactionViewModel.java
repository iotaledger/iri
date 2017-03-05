package com.iota.iri.viewModel;

import com.iota.iri.tangle.IPersistenceProvider;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.ITransaction;
import com.iota.iri.utils.Converter;

import java.nio.ByteBuffer;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class TransactionViewModel {
    static	final	int	SIGNATURE_MESSAGE_OFFSET	=	0,	SIGNATURE_MESSAGE_LENGTH	=	19683;
    static	final	int	CHECKSUM_OFFSET	=	SIGNATURE_MESSAGE_OFFSET	+	SIGNATURE_MESSAGE_LENGTH,	CHECKSUM_LENGTH	=	243;
    static	final	int	ADDRESS_OFFSET	=	CHECKSUM_OFFSET	+	CHECKSUM_LENGTH,	ADDRESS_LENGTH	=	243;
    static	final	int	VALUE_OFFSET	=	ADDRESS_OFFSET	+	ADDRESS_LENGTH,	VALUE_LENGTH	=	81;
    static	final	int	TAG_OFFSET	=	VALUE_OFFSET	+	VALUE_LENGTH,	TAG_LENGTH	=	81;
    static	final	int	TIMESTAMP_OFFSET	=	TAG_OFFSET	+	TAG_LENGTH,	TIMESTAMP_LENGTH	=	81;
    static	final	int	TRUNK_OFFSET	=	TIMESTAMP_OFFSET	+	TIMESTAMP_LENGTH,	TRUNK_LENGTH	=	243;
    static	final	int	BRANCH_OFFSET	=	TRUNK_OFFSET	+	TRUNK_LENGTH,	BRANCH_LENGTH	=	243;
    static	final	int	NONCE_OFFSET	=	BRANCH_OFFSET	+	BRANCH_LENGTH,	NONCE_SIZE	=	243;
    public static final int TRINARY_SIZE = 8019;//NONCE_TRINARY_OFFSET + NONCE_TRINARY_SIZE;

    public static IPersistenceProvider storageProvider;
    ITransaction transaction;
    int[] hash;
    int[] trits;

    public TransactionViewModel(int[] trits) {
        this.transaction = new ITransaction();
        this.transaction.bytes = Converter.bytes(trits);
        this.trits = trits;
    }

    public TransactionViewModel(byte[] bytes) {
        this.transaction = new ITransaction();
        transaction.bytes = bytes;
    }

    public void save() {
        this.transaction.hash = Converter.bytes(this.getHash());
        TransactionViewModel.storageProvider.save(transaction);
    }

    public int[] getTrits() {
        if(trits == null) {
            trits = new int[TRINARY_SIZE];
            Converter.getTrits(transaction.bytes, trits);
        }
        return trits;
    }

    public int[] getHash() {
        if(this.hash == null) {
            this.calculateHash(new Curl());
        }
        return hash;
    }
    private void calculateHash(Curl curl) {
        this.hash = new int[Curl.HASH_LENGTH];
        curl.absorb(this.getTrits(), 0, this.trits.length);
        curl.squeeze(this.hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(this.hash);
    }

    /*
    public static TransactionViewModel fromAddress(long pointer) {
        ByteBuffer bytePointer = ByteBuffer.allocate(Long.BYTES);
        bytePointer.putLong(pointer);
        return new TransactionViewModel(storageProvider.load(ITransaction.class, bytePointer.array()));
    }

    public static TransactionViewModel fromHash(int[] hash) {
        return new TransactionViewModel(storageProvider.load(ITransaction.class, Converter.bytes(hash)));
    }
    */
}
