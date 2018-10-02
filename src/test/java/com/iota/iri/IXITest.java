package com.iota.iri;

import com.iota.iri.service.dto.AbstractResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link IXI}
 */
public class IXITest {
    static TemporaryFolder ixiDir = new TemporaryFolder();
    static IXI ixi;

    /**
     * Create IXI temporary directory and start IXI.
     * @throws Exception if temporary folder can not be created.
     */
    @BeforeClass
    public static void setUp() throws Exception {
        ixiDir.create();
        ixi = new IXI();
        ixi.init(ixiDir.getRoot().getAbsolutePath());
    }

    /**
     * Shutdown IXI and delete temporary folder.
     * @throws InterruptedException if directory watch thread was interrupted.
     */
    @AfterClass
    public static void tearDown() throws InterruptedException {
        ixi.shutdown();
        ixiDir.delete();
    }

    /**
     * If an command matches the command pattern, but is not valid, expect an unknown command error message.
     */
    @Test
    public void processCommandError() {
        AbstractResponse response = ixi.processCommand("testCommand.testSuffix", null);
        assertNotNull("Response should not be null", response);
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [testCommand.testSuffix] is unknown"));
    }

    /**
     * If null is given as a command, expect a parameter check error message.
     */
    @Test
    public void processCommandNull() {
        AbstractResponse response = ixi.processCommand(null, null);
        assertNotNull("Response should not be null", response);
        assertTrue("Wrong error message returned in response", response.toString().contains("Command can not be null or empty"));
    }

    /**
     * If an empty string is given as a command, expect a parameter check error message.
     */
    @Test
    public void processCommandEmpty() {
        AbstractResponse response = ixi.processCommand("", null);
        assertNotNull("Response should not be null", response);
        assertTrue("Wrong error message returned in response", response.toString().contains("Command can not be null or empty"));
    }

    /**
     * If an does not match the command pattern, expect an unknown command error message.
     */
    @Test
    public void processCommandUnknown() {
        AbstractResponse response = ixi.processCommand("unknown", null);
        assertNotNull("Response should not be null", response);
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [unknown] is unknown"));
    }

}