package com.iota.iri;

import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.IXIResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class IXITest {
    static TemporaryFolder ixiDir = new TemporaryFolder();
    static IXI ixi;

    @BeforeClass
    public static void setUp() throws Exception {
        ixiDir.create();
        ixi = new IXI();
        ixi.init(ixiDir.getRoot().getAbsolutePath().toString());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        ixi.shutdown();
        ixiDir.delete();
    }

    @Test
    public void init() throws Exception {
        AbstractResponse response;
        IXIResponse ixiResponse;

        /*
        final String testJs =
                "var Callable = Java.type(\"com.iota.iri.service.CallableRequest\");\n" +
                        "print(\"hello world\");\n" +
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
        final String testPackage = "{\"main\": \"index.js\"}";


        ixiDir.newFolder("test");
        try (OutputStream out = new BufferedOutputStream(
                Files.newOutputStream(Paths.get(ixiDir.getRoot().toString(),"test", "index.js"), CREATE))) {
            out.write(testJs.getBytes());
        }
        try (OutputStream out = new BufferedOutputStream(
                Files.newOutputStream(Paths.get(ixiDir.getRoot().toString(),"test", "package.json"), CREATE))) {
            out.write(testPackage.getBytes());
        }
        // Allow IXI to load the file
        Map<String, Object> request = new HashMap<>();
        Thread.sleep(1000);
        response = IXI.instance().processCommand("test.getParser", request);

        assertFalse(response instanceof ErrorResponse);
        assertTrue(response instanceof IXIResponse);

        ixiResponse = ((IXIResponse) response);
        assertNotNull(ixiResponse.getResponse());
        */
    }

    @Test
    public void processCommand() throws Exception {

    }

}
