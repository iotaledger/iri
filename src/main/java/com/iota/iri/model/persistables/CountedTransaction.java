package com.iota.iri.model.persistables;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.utils.Serializer;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class CountedTransaction extends Transaction {

    public static int counterSize = Long.BYTES;
    public long storageReferenceCounter = 0l;

    public static CountedTransaction fromTransaction(Transaction tx, long storageReferenceCounter){
        CountedTransaction toReturn = new CountedTransaction();
        toReturn.bytes = tx.bytes();
        byte[] metadataBuffer = tx.metadata();
        ByteBuffer bb = ByteBuffer.allocate(metadataBuffer.length + counterSize);
        bb.putLong(storageReferenceCounter);
        bb.put(metadataBuffer);
        toReturn.readMetadata(bb.array());

        return toReturn;
    }


    @Override
    public byte[] metadata() {
        byte[] metadataBuffer = super.metadata();
        ByteBuffer bb = ByteBuffer.allocate(metadataBuffer.length + counterSize);
        bb.putLong(storageReferenceCounter);
        bb.put(metadataBuffer);
        return bb.array();
    }


    @Override
    public void readMetadata(byte[] bytes) {
        if(bytes != null) {
            byte[] counterBytes = new byte[counterSize];
            System.arraycopy(bytes, 0, counterBytes, 0, counterSize);
            storageReferenceCounter = Serializer.getLong(counterBytes, 0);
            byte[] metadataBytes = new byte[bytes.length - counterSize];
            System.arraycopy(bytes, counterSize, metadataBytes, 0, metadataBytes.length);


            super.readMetadata(metadataBytes);
        }


    }
}
