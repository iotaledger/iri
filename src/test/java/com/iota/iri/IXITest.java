package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.ErrorResponse;
import com.iota.iri.service.dto.IXIResponse;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardOpenOption.CREATE;
import static org.junit.Assert.*;

/**
 * Created by paul on 1/4/17.
 */
public class IXITest {
    @Test
    public void init() throws Exception {
        final String ixiPath = "ixiTest";
        Configuration.put(Configuration.DefaultConfSettings.IXI_DIR, ixiPath);
        IXI.instance().init();

        final String testJs =
        "var Callable = Java.type(\"com.iota.iri.service.CallableRequest\");\n" +
        "var IXIResponse = Java.type(\"com.iota.iri.service.dto.IXIResponse\");\n" +
        "API.put(\"getParser\", new Callable({\n" +
                "call: function(req) {\n" +
                    "var IntArray = Java.type(\"int[]\");\n" +
                    "var out = new IntArray(Math.floor(Math.random()*9)+1);\n" +
                    "out[0] = 2;\n" +
                    "var r = IXIResponse.create({\n" +
                            "myArray: out,\n" +
                            "name: \"Foo\"\n" +
                    "});\n" +
                    "return r;\n" +
                "}\n" +
        "}));";

        final File testFile = new File(ixiPath + "/test.js");
        testFile.createNewFile();
        try (OutputStream out = new BufferedOutputStream(
                Files.newOutputStream(testFile.toPath(), CREATE))) {
            out.write(testJs.getBytes());
        }
        // Allow IXI to load the file
        Map<String, Object> request = new HashMap<>();
        Thread.sleep(1000);
        AbstractResponse response = IXI.processCommand("test.getParser", request);

        assertFalse(response instanceof ErrorResponse);
        assertTrue(response instanceof IXIResponse);
        assertNotNull(((IXIResponse)response).getResponse());

        testFile.delete();

        IXI.shutdown();
    }

    @Test
    public void processCommand() throws Exception {

    }

}
