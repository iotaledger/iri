package com.iota.iri.model;

import com.iota.iri.storage.Persistable;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains the data of a local snapshot in its entirety.
 */
public class LocalSnapshot implements Persistable {

    // meta
    public int milestoneIndex;
    public long milestoneTimestamp;
    public int numSolidEntryPoints;
    public int numSeenMilestones;
    public Map<Hash, Integer> solidEntryPoints;
    public Map<Hash, Integer> seenMilestones;

    // state
    public Map<Hash, Long> ledgerState;

    @Override
    public byte[] bytes() {
        ByteBuffer buf = ByteBuffer.allocate(
                // index, timestamp, num solid entry points/seen milestones
                20 +
                // solid entry points
                        (solidEntryPoints.size() * (Hash.SIZE_IN_BYTES + 4)) +
                        // seen milestones
                        (seenMilestones.size() * (Hash.SIZE_IN_BYTES + 4)) +
                        // ledger state
                        (ledgerState.size() * (Hash.SIZE_IN_BYTES + 8)));

        // nums
        buf.putInt(milestoneIndex);
        buf.putLong(milestoneTimestamp);
        buf.putInt(numSolidEntryPoints);
        buf.putInt(numSeenMilestones);

        // maps
        for (Map.Entry<Hash, Integer> entry : solidEntryPoints.entrySet()) {
            buf.put(entry.getKey().bytes());
            buf.putInt(entry.getValue());
        }
        for (Map.Entry<Hash, Integer> entry : seenMilestones.entrySet()) {
            buf.put(entry.getKey().bytes());
            buf.putInt(entry.getValue());
        }
        for (Map.Entry<Hash, Long> entry : ledgerState.entrySet()) {
            buf.put(entry.getKey().bytes());
            buf.putLong(entry.getValue());
        }

        return buf.array();
    }

    @Override
    public void read(byte[] bytes) {
        byte[] hashBuf = new byte[Hash.SIZE_IN_BYTES];
        ByteBuffer buf = ByteBuffer.wrap(bytes);

        // nums
        milestoneIndex = buf.getInt();
        milestoneTimestamp = buf.getLong();
        numSolidEntryPoints = buf.getInt();
        numSeenMilestones = buf.getInt();

        // solid entry points
        solidEntryPoints = new HashMap<>();
        for (int i = 0; i < numSolidEntryPoints; i++) {
            buf.get(hashBuf);
            solidEntryPoints.put(HashFactory.ADDRESS.create(hashBuf, 0, Hash.SIZE_IN_BYTES), buf.getInt());
        }

        // seen milestones
        seenMilestones = new HashMap<>();
        for (int i = 0; i < numSeenMilestones; i++) {
            buf.get(hashBuf);
            seenMilestones.put(HashFactory.ADDRESS.create(hashBuf, 0, Hash.SIZE_IN_BYTES), buf.getInt());
        }

        // actual ledger state
        int ledgerStateEntriesCount = buf.remaining() / (Hash.SIZE_IN_BYTES + 8);
        ledgerState = new HashMap<>();
        for (int i = 0; i < ledgerStateEntriesCount; i++) {
            buf.get(hashBuf);
            ledgerState.put(HashFactory.ADDRESS.create(hashBuf, 0, Hash.SIZE_IN_BYTES), buf.getLong());
        }
    }

    @Override
    public byte[] metadata() {
        return new byte[0];
    }

    @Override
    public void readMetadata(byte[] bytes) {

    }

    @Override
    public boolean merge() {
        return false;
    }
}