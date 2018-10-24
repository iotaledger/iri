package com.iota.iri.service.snapshot.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotMetaData;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

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
     * This method retrieves the meta data of a snapshot from a file.
     *
     * It is used by local snapshots to determine the relevant information about the saved snapshot.
     *
     * @param snapshotMetaDataFile File object with the path to the snapshot metadata file
     * @return SnapshotMetaData instance holding all the relevant details about the snapshot
     * @throws SnapshotException if anything goes wrong while reading and parsing the file
     */
    public static SnapshotMetaDataImpl fromFile(File snapshotMetaDataFile) throws SnapshotException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(
                new FileInputStream(snapshotMetaDataFile))))) {

            Hash hash;
            int index;
            long timestamp;
            int solidEntryPointsSize;
            int seenMilestonesSize;

            // read the hash
            String line;
            if((line = reader.readLine()) != null) {
                hash = HashFactory.TRANSACTION.create(line);
            } else {
                throw new SnapshotException("invalid or malformed snapshot metadata file at " +
                        snapshotMetaDataFile.getAbsolutePath());
            }

            // read the index
            if((line = reader.readLine()) != null) {
                index = Integer.parseInt(line);
            } else {
                throw new SnapshotException("invalid or malformed snapshot metadata file at " +
                        snapshotMetaDataFile.getAbsolutePath());
            }

            // read the timestamp
            if((line = reader.readLine()) != null) {
                timestamp = Long.parseLong(line);
            } else {
                throw new SnapshotException("invalid or malformed snapshot metadata file at " +
                        snapshotMetaDataFile.getAbsolutePath());
            }

            // read the solid entry points size
            if((line = reader.readLine()) != null) {
                solidEntryPointsSize = Integer.parseInt(line);
            } else {
                throw new SnapshotException("invalid or malformed snapshot metadata file at " +
                        snapshotMetaDataFile.getAbsolutePath());
            }

            // read the solid entry points size
            if((line = reader.readLine()) != null) {
                seenMilestonesSize = Integer.parseInt(line);
            } else {
                throw new SnapshotException("invalid or malformed snapshot metadata file at " +
                        snapshotMetaDataFile.getAbsolutePath());
            }

            // read the solid entry points from our file
            HashMap<Hash, Integer> solidEntryPoints = new HashMap<>();
            for(int i = 0; i < solidEntryPointsSize; i++) {
                if((line = reader.readLine()) != null) {
                    String[] parts = line.split(";", 2);
                    if(parts.length >= 2) {
                        solidEntryPoints.put(HashFactory.TRANSACTION.create(parts[0]), Integer.parseInt(parts[1]));
                    }
                } else {
                    throw new SnapshotException("invalid or malformed snapshot metadata file at " +
                            snapshotMetaDataFile.getAbsolutePath());
                }
            }

            // read the seen milestones
            HashMap<Hash, Integer> seenMilestones = new HashMap<>();
            for(int i = 0; i < seenMilestonesSize; i++) {
                if((line = reader.readLine()) != null) {
                    String[] parts = line.split(";", 2);
                    if(parts.length >= 2) {
                        seenMilestones.put(HashFactory.TRANSACTION.create(parts[0]), Integer.parseInt(parts[1]));
                    }
                } else {
                    throw new SnapshotException("invalid or malformed snapshot metadata file at " +
                            snapshotMetaDataFile.getAbsolutePath());
                }
            }

            // close the reader
            reader.close();

            // create and return our SnapshotMetaData object
            return new SnapshotMetaDataImpl(hash, index, timestamp, solidEntryPoints, seenMilestones);
        } catch (IOException e) {
            throw new SnapshotException("failed to read the snapshot metadata file at " +
                    snapshotMetaDataFile.getAbsolutePath(), e);
        }
    }

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

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeToDisk(String filePath) throws SnapshotException {
        try {
            Files.write(
                    Paths.get(filePath),
                    () -> Stream.concat(
                            Stream.of(
                                    hash.toString(),
                                    String.valueOf(index),
                                    String.valueOf(timestamp),
                                    String.valueOf(solidEntryPoints.size()),
                                    String.valueOf(seenMilestones.size())
                            ),
                            Stream.concat(
                                solidEntryPoints.entrySet()
                                        .stream()
                                        .sorted(Map.Entry.comparingByValue())
                                        .<CharSequence>map(entry -> entry.getKey().toString() + ";" + entry.getValue()),
                                seenMilestones.entrySet()
                                        .stream()
                                        .sorted(Map.Entry.comparingByValue())
                                        .<CharSequence>map(entry -> entry.getKey().toString() + ";" + entry.getValue())
                            )
                    ).iterator()
            );
        } catch (IOException e) {
            throw new SnapshotException("failed to write snapshot meta data file at " + filePath, e);
        }
    }
}
