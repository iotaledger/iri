package com.iota.iri.viewModel;

import com.iota.iri.tangle.IPersistenceProvider;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class TransactionVM {
    public static final int SIZE = 1604;

    public static final int TYPE_OFFSET = 0, TYPE_SIZE = Byte.BYTES;
    public static final int HASH_OFFSET = TYPE_OFFSET + TYPE_SIZE + ((Long.BYTES - (TYPE_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), HASH_SIZE = 46;

    private static final int BYTES_OFFSET = HASH_OFFSET + HASH_SIZE + ((Long.BYTES - (HASH_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), BYTES_SIZE = SIZE;

    public static final int ADDRESS_OFFSET = BYTES_OFFSET + BYTES_SIZE + ((Long.BYTES - (BYTES_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), ADDRESS_SIZE = 49;
    public static final int VALUE_OFFSET = ADDRESS_OFFSET + ADDRESS_SIZE + ((Long.BYTES - (ADDRESS_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), VALUE_SIZE = Long.BYTES;
    public static final int TAG_OFFSET = VALUE_OFFSET + VALUE_SIZE + ((Long.BYTES - (VALUE_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), TAG_SIZE = 17;
    private static final int CURRENT_INDEX_OFFSET = TAG_OFFSET + TAG_SIZE + ((Long.BYTES - (TAG_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), CURRENT_INDEX_SIZE = Long.BYTES;
    private static final int LAST_INDEX_OFFSET = CURRENT_INDEX_OFFSET + CURRENT_INDEX_SIZE + ((Long.BYTES - (CURRENT_INDEX_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), LAST_INDEX_SIZE = Long.BYTES;
    public static final int BUNDLE_OFFSET = LAST_INDEX_OFFSET + LAST_INDEX_SIZE + ((Long.BYTES - (LAST_INDEX_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), BUNDLE_SIZE = 49;
    private static final int TRUNK_TRANSACTION_OFFSET = BUNDLE_OFFSET + BUNDLE_SIZE + ((Long.BYTES - (BUNDLE_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), TRUNK_TRANSACTION_SIZE = HASH_SIZE;
    private static final int BRANCH_TRANSACTION_OFFSET = TRUNK_TRANSACTION_OFFSET + TRUNK_TRANSACTION_SIZE + ((Long.BYTES - (TRUNK_TRANSACTION_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), BRANCH_TRANSACTION_SIZE = HASH_SIZE;
    public static final int VALIDITY_OFFSET = BRANCH_TRANSACTION_OFFSET + BRANCH_TRANSACTION_SIZE + ((Long.BYTES - (BRANCH_TRANSACTION_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), VALIDITY_SIZE = 1;
    public static final int ARRIVAL_TIME_OFFSET = VALIDITY_OFFSET + VALIDITY_SIZE + ((Long.BYTES - (VALIDITY_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), ARIVAL_TIME_SIZE = Long.BYTES;


    /*
    public static	final	int	SIGNATURE_MESSAGE_OFFSET	=	0,	SIGNATURE_MESSAGE_LENGTH	=	19683;
    public static	final	int	CHECKSUM_OFFSET	=	SIGNATURE_MESSAGE_OFFSET	+	SIGNATURE_MESSAGE_LENGTH,	CHECKSUM_LENGTH	=	243;
    public static	final	int	ADDRESS_OFFSET	=	CHECKSUM_OFFSET	+	CHECKSUM_LENGTH,	ADDRESS_LENGTH	=	243;
    public static	final	int	VALUE_OFFSET	=	ADDRESS_OFFSET	+	ADDRESS_LENGTH,	VALUE_LENGTH	=	81;
    public static	final	int	TAG_OFFSET	=	VALUE_OFFSET	+	VALUE_LENGTH,	TAG_LENGTH	=	81;
    public static	final	int	TIMESTAMP_OFFSET	=	TAG_OFFSET	+	TAG_LENGTH,	TIMESTAMP_LENGTH	=	81;
    public static	final	int	TRUNK_OFFSET	=	TIMESTAMP_OFFSET	+	TIMESTAMP_LENGTH,	TRUNK_LENGTH	=	243;
    public static	final	int	BRANCH_OFFSET	=	TRUNK_OFFSET	+	TRUNK_LENGTH,	BRANCH_LENGTH	=	243;
    public static	final	int	NONCE_OFFSET	=	BRANCH_OFFSET	+	BRANCH_LENGTH,	NONCE_SIZE	=	243;
    */
    public static final int TRINARY_SIZE = 8019;//NONCE_TRINARY_OFFSET + NONCE_TRINARY_SIZE;

    public static IPersistenceProvider storageProvider;
    Transaction transaction;
    int[] hash;
    int[] trits;

    public TransactionVM(int[] trits) {
        this.transaction = new Transaction();
        this.transaction.bytes = Converter.bytes(trits);
        this.trits = trits;
    }

    public TransactionVM(byte[] bytes) {
        this.transaction = new Transaction();
        transaction.bytes = bytes;
    }

    public void save() throws Exception {
        this.transaction.hash = Converter.bytes(this.getHash());
        TransactionVM.storageProvider.save(transaction);
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
    public static TransactionVM fromAddress(long pointer) {
        ByteBuffer bytePointer = ByteBuffer.allocate(Long.BYTES);
        bytePointer.putLong(pointer);
        return new TransactionVM(storageProvider.load(Transaction.class, bytePointer.array()));
    }

    public static TransactionVM fromHash(int[] hash) {
        return new TransactionVM(storageProvider.load(Transaction.class, Converter.bytes(hash)));
    }
    */
}
