package com.iota.iri.viewModel;

import com.iota.iri.conf.Configuration;
import com.iota.iri.dataAccess.IStorageProvider;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.ITransaction;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class TransactionViewModel {
    public static IStorageProvider storageProvider;
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
            trits = new int[ITransaction.TRINARY_SIZE];
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

    public static TransactionViewModel fromPointer(long pointer) {
        ByteBuffer bytePointer = ByteBuffer.allocate(Long.BYTES);
        bytePointer.putLong(pointer);
        return new TransactionViewModel(storageProvider.load(ITransaction.class, 0, bytePointer.array()));
    }

    public static TransactionViewModel fromHash(int[] hash) {
        return new TransactionViewModel(storageProvider.load(ITransaction.class, 0, Converter.bytes(hash)));
    }
}
