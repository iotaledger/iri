package com.iota.iri.network;

/**
 * A background worker that sends {@link com.iota.iri.network.protocol.Heartbeat}s to neighbors.
 */
public interface HeartbeatPulse {
	/**
	 * Starts the background worker that calls {@link #sendHeartbeat()} rhythmically.
	 */
	void start();

	/**
	 * Stops the background worker that sends out heartbeats.
	 */
	void shutdown();

	/**
	 * Sends {@link com.iota.iri.network.protocol.Heartbeat} to all neighbors.
	 */
	void sendHeartbeat();
}
