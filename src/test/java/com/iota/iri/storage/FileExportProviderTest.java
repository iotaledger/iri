package com.iota.iri.storage;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class FileExportProviderTest {

    FileExportProvider fileExportProvider;

    @Before
    public void setUp() {
        this.fileExportProvider = new FileExportProvider();
    }

    @Test
    public void updateSenderIsNull() {
        assertFalse(this.fileExportProvider.update(null, null, null));
    }

    @Test
    public void updateWithoutSender() {
        assertFalse(this.fileExportProvider.update(null, null, "failString"));
    }

    @Test
    public void updateWithoutTransaction() {
        assertFalse(this.fileExportProvider.update(null, null, "sender"));
    }

    @Test
    public void update() {
        Transaction transaction = new Transaction();
        transaction.sender = "testSender";
        Indexable index = new Hash("D9XCNSCCAJGLWSQOQAQNFWANPYKYMCQ9VCOMROLDVLONPPLDFVPIZNAPVZLQMPFYJPAHUKIAEKNCQIYJZ");
        assertTrue(this.fileExportProvider.update(transaction, index, "sender"));
    }

    @Test
    public void updateException() {
        Transaction transaction = new Transaction();
        transaction.sender = "testSender";
        assertFalse(this.fileExportProvider.update(transaction, null, "sender"));
    }

}
