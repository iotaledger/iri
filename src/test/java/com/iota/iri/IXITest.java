package com.iota.iri;

import com.iota.iri.service.CallableRequest;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.ErrorResponse;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link IXI}
 */
public class IXITest {
    private static TemporaryFolder ixiDir = new TemporaryFolder();
    private static IXI ixi;

    /**
     * Create IXI temporary directory and start IXI.
     * @throws Exception if temporary folder can not be created.
     */
    @BeforeClass
    public static void setUp() throws Exception {
        ixiDir.create();
        ixi = new IXI();
        ixi.init(ixiDir.getRoot().getAbsolutePath());

        Field ixiApiField = ixi.getClass().getDeclaredField("ixiAPI");
        ixiApiField.setAccessible(true);
        Map<String, Map<String, CallableRequest<AbstractResponse>>> ixiAPI =
                (Map<String, Map<String, CallableRequest<AbstractResponse>>>) ixiApiField.get(ixi);
        ixiAPI.put("IXI", new HashMap<>());
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
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [testCommand.testSuffix] is unknown"));
    }

    /**
     * If null is given as a command, expect a parameter check error message.
     */
    @Test
    public void processCommandNull() {
        AbstractResponse response = ixi.processCommand(null, null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command can not be null or empty"));
    }

    /**
     * If an empty string is given as a command, expect a parameter check error message.
     */
    @Test
    public void processCommandEmpty() {
        AbstractResponse response = ixi.processCommand("", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command can not be null or empty"));
    }

    /**
     * If the given command does not exist, expect an unknown command error message.
     */
    @Test
    public void processCommandUnknown() {
        AbstractResponse response = ixi.processCommand("unknown", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [unknown] is unknown"));
    }

    /**
     * If an IXI module does not have the given command, expect an unknown command error message.
     */
    @Test
    public void processIXICommandUnknown() {
        AbstractResponse response = ixi.processCommand("IXI.unknown", null);
        assertThat("Wrong type of response", response, CoreMatchers.instanceOf(ErrorResponse.class));
        assertTrue("Wrong error message returned in response", response.toString().contains("Command [IXI.unknown] is unknown"));
    }
}
