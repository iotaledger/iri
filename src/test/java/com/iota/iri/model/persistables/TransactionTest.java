package com.iota.iri.model.persistables;

import static org.junit.Assert.*;

import org.junit.Test;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;

public class TransactionTest {

    @Test
    public void testBytes() {
        Transaction t = TransactionTestUtils.getRandomTransaction();
        
        Transaction newtx = new Transaction();
        newtx.read(t.bytes());
        newtx.readMetadata(t.metadata());
        
        assertArrayEquals("metadata should be the same in the copy", t.metadata(), newtx.metadata());
        assertArrayEquals("bytes should be the same in the copy", t.bytes(), newtx.bytes());
    }
    
    @Test
    public void fromTrits() {
        byte[] trits = TransactionTestUtils.getRandomTransactionTrits();
        byte[] bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, bytes);
        
        TransactionViewModel TVM = new TransactionViewModel(trits, Hash.NULL_HASH);
        TVM.getAddressHash();
        TVM.getTrunkTransactionHash();
        TVM.getBranchTransactionHash();
        TVM.getBundleHash();
        TVM.getTagValue();
        TVM.getObsoleteTagValue();
        TVM.setAttachmentData();
        TVM.setMetadata();
        
        assertArrayEquals("bytes in the TVM should be unmodified", TVM.getBytes(), bytes);
        
        Transaction tvmTransaction = TVM.getTransaction();
        
        assertEquals("branch in transaction should be the same as in the TVM", tvmTransaction.branch, TVM.getTransaction().branch);
    }
}
