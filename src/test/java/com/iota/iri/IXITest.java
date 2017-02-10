package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.service.dto.AbstractResponse;
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
        final String ixiPath = "ixi";

        IXI.instance().init();

        final String testJs =
                "var Callable = Java.type(\"java.util.concurrent.Callable\");\n" +
                        "var Response = Java.type(\"com.iota.iri.service.dto.AbstractResponse\");\n" +
                        "var MyRes = Java.extend(Response, {\n" +
                        "text: \"FOO\"\n" +
                        "});\n" +
                        "API.put(\"getParser\", new Callable({\n" +
                        "call: function() {\n" +
                        "return new MyRes();\n" +
                        "}\n" +
                        "}));\n";

        final File testFile = new File(ixiPath + "/test.js");
        testFile.createNewFile();
        try (OutputStream out = new BufferedOutputStream(
                Files.newOutputStream(testFile.toPath(), CREATE))) {
            out.write(testJs.getBytes());
        }
        Map<String, Object> request = new HashMap<>();
        AbstractResponse response = IXI.processCommand("test.getParser", request);

        testFile.delete();

        IXI.shutdown();
    }

    @Test
    public void processCommand() throws Exception {

    }

}
