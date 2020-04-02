package com.iota.iri.network.protocol;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.utils.TransactionTruncator;

import java.nio.ByteBuffer;

/**
 * Defines the information contained in a heartbeat
 */
public class Heartbeat {
	private int firstSolidMilestoneIndex;
	private int lastSolidMilestoneIndex;

	/**
	 * Parses the given message into a {@link Heartbeat} object.
	 *
	 * @param msg the buffer containing the handshake info
	 * @return the {@link Heartbeat} object
	 */
	public static Heartbeat fromByteBuffer(ByteBuffer msg) {
		Heartbeat heartbeat = new Heartbeat();
		heartbeat.setFirstSolidMilestoneIndex(msg.getInt());
		heartbeat.setLastSolidMilestoneIndex(msg.getInt());
		return heartbeat;
	}


	public int getFirstSolidMilestoneIndex() {
		return firstSolidMilestoneIndex;
	}

	public void setFirstSolidMilestoneIndex(int firstSolidMilestoneIndex) {
		this.firstSolidMilestoneIndex = firstSolidMilestoneIndex;
	}

	public int getLastSolidMilestoneIndex() {
		return lastSolidMilestoneIndex;
	}

	public void setLastSolidMilestoneIndex(int lastSolidMilestoneIndex) {
		this.lastSolidMilestoneIndex = lastSolidMilestoneIndex;
	}
}
