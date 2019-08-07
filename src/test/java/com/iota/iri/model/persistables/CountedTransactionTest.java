package com.iota.iri.model.persistables;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CountedTransactionTest {



    @Test
    public void testBytes() {
        Transaction t = TransactionTestUtils.getTransaction();

        Transaction newtx = new Transaction();
        newtx.read(t.bytes());
        newtx.readMetadata(t.metadata());
        CountedTransaction ctx1 = CountedTransaction.fromTransaction(newtx, 5);

        CountedTransaction ctx2 = new CountedTransaction();
        ctx2.read(ctx1.bytes());
        ctx2.readMetadata(ctx1.metadata());


        assertArrayEquals("metadata should be the same in the copy", ctx1.metadata(), ctx2.metadata());
        assertArrayEquals("bytes should be the same in the copy", newtx.bytes(), ctx2.bytes());
    }

//    @Test
//    public void fromTrits() {
//        byte[] trits = TransactionTestUtils.getTransactionTrits();
//        byte[] bytes = Converter.allocateBytesForTrits(trits.length);
//        Converter.bytes(trits, bytes);
//
//        TransactionViewModel tvm = new TransactionViewModel(trits, Hash.NULL_HASH);
//        tvm.getAddressHash();
//        tvm.getTrunkTransactionHash();
//        tvm.getBranchTransactionHash();
//        tvm.getBundleHash();
//        tvm.getTagValue();
//        tvm.getObsoleteTagValue();
//        tvm.setAttachmentData();
//        tvm.setMetadata();
//
//        assertArrayEquals("bytes in the TVM should be unmodified", tvm.getBytes(), bytes);
//
//        Transaction tvmTransaction = tvm.getTransaction();
//
//        assertEquals("branch in transaction should be the same as in the tvm", tvmTransaction.branch, tvm.getTransaction().branch);
//    }

}
