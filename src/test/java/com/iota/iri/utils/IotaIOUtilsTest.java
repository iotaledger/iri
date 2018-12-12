package com.iota.iri.utils;

import java.io.FileReader;
import java.io.IOException;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;


public class IotaIOUtilsTest {

    @Test
    public void testGetFileStreamFromCwd() throws IOException {
        //We could have created a temporary file but then there may be permission problems
        String path = "/checkstyle.xml";
        assertThat(path + " is being treated as resource and not a local file",
                IotaIOUtils.getFileStreamFromCwdOrResource(path), instanceOf(FileReader.class));
    }

    @Test
    public void testGetFileStreamFromResource() throws IOException {
        String path = "/snapshotMainnet.sig";
        assertThat(path + " is being treated as a local file and not a resource",
                IotaIOUtils.getFileStreamFromCwdOrResource(path), not(instanceOf(FileReader.class)));
    }
}