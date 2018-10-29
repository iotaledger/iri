package com.iota.iri.service.snapshot.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotMetaData;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements the basic contract of the {@link SnapshotMetaData} interface.
 */
public class SnapshotMetaDataImpl implements SnapshotMetaData {
    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getInitialHash()}.
     */
    private Hash initialHash;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getInitialIndex()}.
     */
    private int initialIndex;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getInitialTimestamp()}.
     */
    private long initialTimestamp;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getHash()}.
     */
    private Hash hash;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getIndex()}.
     */
    private int index;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getTimestamp()}.
     */
    private long timestamp;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getSolidEntryPoints()}.
     */
    private Map<Hash, Integer> solidEntryPoints;

    /**
     * Internal property for the value returned by {@link SnapshotMetaData#getSeenMilestones()}.
     */
    private Map<Hash, Integer> seenMilestones;

    /**
     * Creates a meta data object with the given information.
     *
     * It simply stores the passed in parameters in the internal properties.
     *
     * @param hash hash of the transaction that the snapshot belongs to
     * @param index milestone index that the snapshot belongs to
     * @param timestamp timestamp of the transaction that the snapshot belongs to
     * @param solidEntryPoints map with the transaction hashes of the solid entry points associated to their milestone
     *                         index
     * @param seenMilestones map of milestone transaction hashes associated to their milestone index
     */
    public SnapshotMetaDataImpl(Hash hash, int index, Long timestamp, Map<Hash, Integer> solidEntryPoints,
                                Map<Hash, Integer> seenMilestones) {

        this.initialHash = hash;
        this.initialIndex = index;
        this.initialTimestamp = timestamp;

        setHash(hash);
        setIndex(index);
        setTimestamp(timestamp);
        setSolidEntryPoints(new HashMap<>(solidEntryPoints));
        setSeenMilestones(new HashMap<>(seenMilestones));
    }

    /**
     * Creates a deep clone of the passed in {@link SnapshotMetaData}.
     *
     * @param snapshotMetaData object that shall be cloned
     */
    public SnapshotMetaDataImpl(SnapshotMetaData snapshotMetaData) {
        this(snapshotMetaData.getInitialHash(), snapshotMetaData.getInitialIndex(),
                snapshotMetaData.getInitialTimestamp(), snapshotMetaData.getSolidEntryPoints(),
                snapshotMetaData.getSeenMilestones());

        this.setIndex(snapshotMetaData.getIndex());
        this.setHash(snapshotMetaData.getHash());
        this.setTimestamp(snapshotMetaData.getTimestamp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getInitialHash() {
        return initialHash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialHash(Hash initialHash) {
        this.initialHash = initialHash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInitialIndex() {
        return initialIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialIndex(int initialIndex) {
        this.initialIndex = initialIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getInitialTimestamp() {
        return initialTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialTimestamp(long initialTimestamp) {
        this.initialTimestamp = initialTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getHash() {
        return hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHash(Hash hash) {
        this.hash = hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIndex() {
        return this.index;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimestamp() {
        return this.timestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Hash, Integer> getSolidEntryPoints() {
        return solidEntryPoints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSolidEntryPoints(Map<Hash, Integer> solidEntryPoints) {
        this.solidEntryPoints = solidEntryPoints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasSolidEntryPoint(Hash solidEntrypoint) {
        return solidEntryPoints.containsKey(solidEntrypoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSolidEntryPointIndex(Hash solidEntrypoint) {
        return solidEntryPoints.get(solidEntrypoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Hash, Integer> getSeenMilestones() {
        return seenMilestones;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSeenMilestones(Map<Hash, Integer> seenMilestones) {
        this.seenMilestones = seenMilestones;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(SnapshotMetaData newMetaData) {
        initialIndex = newMetaData.getInitialIndex();
        initialHash = newMetaData.getInitialHash();
        initialTimestamp = newMetaData.getInitialTimestamp();

        setIndex(newMetaData.getIndex());
        setHash(newMetaData.getHash());
        setTimestamp(newMetaData.getTimestamp());
        setSolidEntryPoints(new HashMap<>(newMetaData.getSolidEntryPoints()));
        setSeenMilestones(new HashMap<>(newMetaData.getSeenMilestones()));
    }
}
