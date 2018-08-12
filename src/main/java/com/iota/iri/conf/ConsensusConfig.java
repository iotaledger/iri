package com.iota.iri.conf;

/**
 * A configuration for all configuration concerned with achieving consensus on the ledger state across different nodes
 *
 * @implNote It currently extends two other interfaces. This has been done due to lack of separation of concerns in
 * the current code base and will be changed in the future
 */
public interface ConsensusConfig extends SnapshotConfig, MilestoneConfig {
}
