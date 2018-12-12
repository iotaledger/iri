package com.iota.iri.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;


public class IotaIOUtilsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testGetFileStreamFromCwd() throws IOException {
        File test = folder.newFile("test");
        String path = test.getPath();
        assertThat(path + " is being treated as resource and not a local file",
                IotaIOUtils.getFileStreamFromFileOrResource(path), instanceOf(FileReader.class));
    }

    @Test
    public void testGetFileStreamFromResource() throws IOException {
        String path = "/snapshotMainnet.sig";
        assertThat(path + " is being treated as a local file and not a resource",
                IotaIOUtils.getFileStreamFromFileOrResource(path), not(instanceOf(FileReader.class)));
    }
}