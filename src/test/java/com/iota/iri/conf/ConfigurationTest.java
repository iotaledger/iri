package com.iota.iri.conf;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * Created by paul on 4/15/17.
 */
public class ConfigurationTest {
    static TemporaryFolder confFolder = new TemporaryFolder();
    static File iniFile;

    @BeforeClass
    public static void setupClas() throws IOException {
        confFolder.create();
        iniFile = confFolder.newFile();
    }

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void getIniValue() throws Exception {

    }

    @Test
    public void allSettings() throws Exception {

    }

    @Test
    public void put() throws Exception {

    }

    @Test
    public void put1() throws Exception {

    }

    @Test
    public void floating() throws Exception {

    }

    @Test
    public void doubling() throws Exception {

    }

    @Test
    public void string() throws Exception {

    }

    @Test
    public void integer() throws Exception {

    }

    @Test
    public void booling() throws Exception {

    }

}