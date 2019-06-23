package com.iota.iri.conf;

/**
 * A configuration that specifies how the node communicates with other nodes.
 *
 * @implNote It currently extends two other interfaces. This has been done due to lack of separation of concerns in
 * the current code base and will be changed in the future
 */
public interface NodeConfig extends ProtocolConfig, NetworkConfig {

    boolean getWASMSupport();

    boolean getStreamingGraphSupport();

    long getNumBlocksPerPeriod();

    interface Descriptions {
        String ENABLE_WASMVM = "If enabling the WASM virtual machine or not.";
        String STREAMING_GRAPH = "If enabling streaming graph computation or not.";
        String PERIOD_SIZE = "Define the number of blocks in a period.";
    }
}
