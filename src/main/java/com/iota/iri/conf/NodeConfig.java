package com.iota.iri.conf;

/**
 * A configuration that specifies how the node communicates with other nodes.
 *
 * @implNote It currently extends two other interfaces. This has been done due to lack of separation of concerns in
 * the current code base and will be changed in the future
 */
public interface NodeConfig extends ProtocolConfig, NetworkConfig {
}
