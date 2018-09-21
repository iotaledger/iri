package com.iota.iri.storage;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class FileExportProviderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new  TemporaryFolder();
    private FileExportProvider fileExportProvider;

    @Before
    public void setUp() {
        this.fileExportProvider = new FileExportProvider();
        this.fileExportProvider.init();
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

    @Test
    public void createDirectory() throws IOException {
        String tempPath = new File(temporaryFolder.getRoot().getPath(), "testFolder").getPath();
        Path newFolder = this.fileExportProvider.createDirectory(Paths.get(tempPath));
        assertTrue(Files.exists(newFolder));
    }

    @Test
    public void createDirectoryAlreadyExists() {
        String tempPath = new File(temporaryFolder.getRoot().getPath(), "testFolderExists").getPath();
        Path newFolder = this.fileExportProvider.createDirectory(Paths.get(tempPath));
        Path existingFolder = this.fileExportProvider.createDirectory(newFolder);
        assertSame(existingFolder, newFolder);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createDirectoryWithNull() {
        this.fileExportProvider.createDirectory(null);
    }

}
