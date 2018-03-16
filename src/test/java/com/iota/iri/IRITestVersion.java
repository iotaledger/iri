package com.iota.iri;

/**
 * This is a stub class so that test classes get access to an IRI that provides this information.
 */
public class IRITestVersion implements IRI {

    @Override
    public String getVersion() {
        return "test-version-stub";
    }

    @Override
    public String getJarName() {
        return "test-jar-stub";
    }

    @Override
    public String getNetName() {
        return "test-net-stub";
    }
}
