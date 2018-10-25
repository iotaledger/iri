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

            Hash hash = readMilestoneHash(reader);
            int index = readMilestoneIndex(reader);
            long timestamp = readMilestoneTimestamp(reader);
            int amountOfSolidEntryPoints = readAmountOfSolidEntryPoints(reader);
            int amountOfSeenMilestones = readAmountOfSeenMilestones(reader);
            Map<Hash, Integer> solidEntryPoints = readSolidEntryPoints(reader, amountOfSolidEntryPoints);
            Map<Hash, Integer> seenMilestones = readSeenMilestones(reader, amountOfSeenMilestones);

            return new SnapshotMetaDataImpl(hash, index, timestamp, solidEntryPoints, seenMilestones);
        } catch (IOException e) {
            throw new SnapshotException("failed to read from the snapshot metadata file at " +
                    snapshotMetaDataFile.getAbsolutePath(), e);
        }
    }

    /**
     * This method reads the transaction hash of the milestone that references the
     * {@link com.iota.iri.service.snapshot.Snapshot} from the meta data file.
     *
     * @param reader reader that is used to read the file
     * @return Hash of the milestone transaction that references the {@link com.iota.iri.service.snapshot.Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the hash from the file
     * @throws IOException if we could not read from the file
     */
    private static Hash readMilestoneHash(BufferedReader reader) throws SnapshotException, IOException {
        String line;
        if((line = reader.readLine()) == null) {
            throw new SnapshotException("could not read the transaction hash from the meta data file");
        }

        return HashFactory.TRANSACTION.create(line);
    }

    /**
     * This method reads the milestone index of the milestone that references the
     * {@link com.iota.iri.service.snapshot.Snapshot} from the meta data file.
     *
     * @param reader reader that is used to read the file
     * @return milestone index of the milestone that references the {@link com.iota.iri.service.snapshot.Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the milestone index from the file
     * @throws IOException if we could not read from the file
     */
    private static int readMilestoneIndex(BufferedReader reader) throws SnapshotException, IOException {
        try {
            String line;
            if ((line = reader.readLine()) == null) {
                throw new SnapshotException("could not read the milestone index from the meta data file");
            }

            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new SnapshotException("could not parse the milestone index from the meta data file", e);
        }
    }

    /**
     * This method reads the timestamp of the milestone that references the
     * {@link com.iota.iri.service.snapshot.Snapshot} from the meta data file.
     *
     * @param reader reader that is used to read the file
     * @return timestamp of the milestone that references the {@link com.iota.iri.service.snapshot.Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the milestone timestamp from the file
     * @throws IOException if we could not read from the file
     */
    private static long readMilestoneTimestamp(BufferedReader reader) throws SnapshotException, IOException {
        try {
            String line;
            if ((line = reader.readLine()) == null) {
                throw new SnapshotException("could not read the milestone timestamp from the meta data file");
            }

            return Long.parseLong(line);
        } catch (NumberFormatException e) {
            throw new SnapshotException("could not parse the milestone timestamp from the meta data file", e);
        }
    }

    /**
     * This method reads the amount of solid entry points of the {@link com.iota.iri.service.snapshot.Snapshot} from the
     * meta data file.
     *
     * @param reader reader that is used to read the file
     * @return amount of solid entry points of the {@link com.iota.iri.service.snapshot.Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the amount of solid entry points from the file
     * @throws IOException if we could not read from the file
     */
    private static int readAmountOfSolidEntryPoints(BufferedReader reader) throws SnapshotException, IOException {
        try {
            String line;
            if ((line = reader.readLine()) == null) {
                throw new SnapshotException("could not read the amount of solid entry points from the meta data file");
            }

            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new SnapshotException("could not parse the amount of solid entry points from the meta data file", e);
        }
    }

    /**
     * This method reads the amount of seen milestones of the {@link com.iota.iri.service.snapshot.Snapshot} from the
     * meta data file.
     *
     * @param reader reader that is used to read the file
     * @return amount of seen milestones of the {@link com.iota.iri.service.snapshot.Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the amount of seen milestones from the file
     * @throws IOException if we could not read from the file
     */
    private static int readAmountOfSeenMilestones(BufferedReader reader) throws SnapshotException, IOException {
        try {
            String line;
            if ((line = reader.readLine()) == null) {
                throw new SnapshotException("could not read the amount of seen milestones from the meta data file");
            }

            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new SnapshotException("could not parse the amount of seen milestones from the meta data file", e);
        }
    }

    /**
     * This method reads the solid entry points of the {@link com.iota.iri.service.snapshot.Snapshot} from the
     * meta data file.
     *
     * @param reader reader that is used to read the file
     * @param amountOfSolidEntryPoints the amount of solid entry points we expect
     * @return the solid entry points of the {@link com.iota.iri.service.snapshot.Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the solid entry points from the file
     * @throws IOException if we could not read from the file
     */
    private static Map<Hash, Integer> readSolidEntryPoints(BufferedReader reader, int amountOfSolidEntryPoints)
            throws SnapshotException, IOException {

        Map<Hash, Integer> solidEntryPoints = new HashMap<>();

        for(int i = 0; i < amountOfSolidEntryPoints; i++) {
            String line;
            if ((line = reader.readLine()) == null) {
                throw new SnapshotException("could not read a solid entry point from the meta data file");
            }

            String[] parts = line.split(";", 2);
            if(parts.length == 2) {
                try {
                    solidEntryPoints.put(HashFactory.TRANSACTION.create(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    throw new SnapshotException("could not parse a solid entry point from the meta data file", e);
                }
            } else {
                throw new SnapshotException("could not parse a solid entry point from the meta data file");
            }
        }

        return solidEntryPoints;
    }

    /**
     * This method reads the seen milestones of the {@link com.iota.iri.service.snapshot.Snapshot} from the
     * meta data file.
     *
     * @param reader reader that is used to read the file
     * @param amountOfSeenMilestones the amount of seen milestones we expect
     * @return the seen milestones of the {@link com.iota.iri.service.snapshot.Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the seen milestones from the file
     * @throws IOException if we could not read from the file
     */
    private static Map<Hash, Integer> readSeenMilestones(BufferedReader reader, int amountOfSeenMilestones)
            throws SnapshotException, IOException {

        Map<Hash, Integer> seenMilestones = new HashMap<>();

        for(int i = 0; i < amountOfSeenMilestones; i++) {
            String line;
            if ((line = reader.readLine()) == null) {
                throw new SnapshotException("could not read a seen milestone from the meta data file");
            }

            String[] parts = line.split(";", 2);
            if(parts.length == 2) {
                try {
                    seenMilestones.put(HashFactory.TRANSACTION.create(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    throw new SnapshotException("could not parse a seen milestone from the meta data file", e);
                }
            } else {
                throw new SnapshotException("could not parse a seen milestone from the meta data file");
            }
        }

        return seenMilestones;
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
