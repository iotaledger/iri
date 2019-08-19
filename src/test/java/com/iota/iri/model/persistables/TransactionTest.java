package com.iota.iri.model.persistables;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;

public class TransactionTest {

    @Test
    public void testBytes() {
        Transaction t = TransactionTestUtils.getTransaction();
        
        Transaction newtx = new Transaction();
        newtx.read(t.bytes());
        newtx.readMetadata(t.metadata());
        
        assertArrayEquals("metadata should be the same in the copy", t.metadata(), newtx.metadata());
        assertArrayEquals("bytes should be the same in the copy", t.bytes(), newtx.bytes());
    }
    
    @Test
    public void fromTrits() {
        byte[] trits = TransactionTestUtils.getTransactionTrits();
        byte[] bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, bytes);
        
        TransactionViewModel tvm = new TransactionViewModel(trits, Hash.NULL_HASH);
        tvm.getAddressHash();
        tvm.getTrunkTransactionHash();
        tvm.getBranchTransactionHash();
        tvm.getBundleHash();
        tvm.getTagValue();
        tvm.getObsoleteTagValue();
        tvm.setAttachmentData();
        tvm.setMetadata();
        
        assertArrayEquals("bytes in the TVM should be unmodified", tvm.getBytes(), bytes);
        
        Transaction tvmTransaction = tvm.getTransaction();
        
        assertEquals("branch in transaction should be the same as in the tvm", tvmTransaction.branch, tvm.getTransaction().branch);
    }
}
