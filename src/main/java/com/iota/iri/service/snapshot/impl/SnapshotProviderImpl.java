package com.iota.iri.service.snapshot.impl;

import com.iota.iri.SignedFiles;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotMetaData;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotState;
import com.iota.iri.utils.IotaIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements the basic contract of the {@link SnapshotProvider} interface.
 */
public class SnapshotProviderImpl implements SnapshotProvider {
    /**
     * Public key that is used to verify the builtin snapshot signature.
     */
    private static final String SNAPSHOT_PUBKEY =
            "TTXJUGKTNPOOEXSTQVVACENJOQUROXYKDRCVK9LHUXILCLABLGJTIPNF9REWHOIMEUKWQLUOKD9CZUYAC";

    /**
     * Public key depth that is used to verify the builtin snapshot signature.
     */
    private static final int SNAPSHOT_PUBKEY_DEPTH = 6;

    /**
     * Snapshot index that is used to verify the builtin snapshot signature.
     */
    private static final int SNAPSHOT_INDEX = 9;

    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(SnapshotProviderImpl.class);

    /**
     * Holds a cached version of the builtin snapshot.
     *
     * Note: The builtin snapshot is embedded in the iri.jar and will not change. To speed up tests that need the
     *       snapshot multiple times while creating their own version of the LocalSnapshotManager, we cache the instance
     *       here so they don't have to rebuild it from the scratch every time (massively speeds up the unit tests).
     */
    private static SnapshotImpl builtinSnapshot = null;

    /**
     * Holds Snapshot related configuration parameters.
     */
    private final SnapshotConfig config;

    /**
     * Internal property for the value returned by {@link SnapshotProvider#getInitialSnapshot()}.
     */
    private Snapshot initialSnapshot;

    /**
     * Internal property for the value returned by {@link SnapshotProvider#getLatestSnapshot()}.
     */
    private Snapshot latestSnapshot;

    /**
     * Creates a data provider for the two {@link Snapshot} instances that are relevant for the node.
     *
     * It provides access to the two relevant {@link Snapshot} instances:
     *
     *     - the initial {@link Snapshot} (the starting point of the ledger based on the last global or local Snapshot)
     *     - the latest {@link Snapshot} (the state of the ledger after applying all changes up till the latest
     *       confirmed milestone)
     *
     * @param config Snapshot related configuration parameters
     * @throws SnapshotException if anything goes wrong while trying to read the snapshots
     */
    public SnapshotProviderImpl(SnapshotConfig config) throws SnapshotException {
        this.config = config;

        loadSnapshots();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Snapshot getInitialSnapshot() {
        return initialSnapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Snapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        initialSnapshot = null;
        latestSnapshot = null;
    }

    /**
     * Loads the snapshots that are provided by this data provider.
     *
     * We first check if a valid local {@link Snapshot} exists by trying to load it. If we fail to load the local
     * {@link Snapshot}, we fall back to the builtin one.
     *
     * After the {@link #initialSnapshot} was successfully loaded we create a copy of it that will act as the "working
     * copy" that will keep track of the latest changes that get applied while the node operates and processes the new
     * confirmed transactions.
     *
     * @throws SnapshotException if anything goes wrong while loading the snapshots
     */
    private void loadSnapshots() throws SnapshotException {
        initialSnapshot = loadLocalSnapshot();
        if (initialSnapshot == null) {
            initialSnapshot = loadBuiltInSnapshot();
        }

        latestSnapshot = new SnapshotImpl(initialSnapshot);
    }

    /**
     * Loads the last local snapshot from the disk.
     *
     * This method checks if local snapshot files are available on the hard disk of the node and tries to load them. If
     * no local snapshot files exist or local snapshots are not enabled we simply return null.
     *
     * @return local snapshot of the node
     * @throws SnapshotException if local snapshot files exist but are malformed
     */
    private SnapshotImpl loadLocalSnapshot() throws SnapshotException {
        if (config.getLocalSnapshotsEnabled()) {
            File localSnapshotFile = new File(config.getLocalSnapshotsBasePath() + ".snapshot.state");
            File localSnapshotMetadDataFile = new File(config.getLocalSnapshotsBasePath() + ".snapshot.meta");

            if (localSnapshotFile.exists() && localSnapshotFile.isFile() && localSnapshotMetadDataFile.exists() &&
                    localSnapshotMetadDataFile.isFile()) {

                SnapshotState snapshotState = readSnapshotStatefromFile(localSnapshotFile.getAbsolutePath());
                if (!snapshotState.hasCorrectSupply()) {
                    throw new SnapshotException("the snapshot state file has an invalid supply");
                }
                if (!snapshotState.isConsistent()) {
                    throw new SnapshotException("the snapshot state file is not consistent");
                }

                SnapshotMetaData snapshotMetaData = readSnapshotMetaDatafromFile(localSnapshotMetadDataFile);

                log.info("resumed from local snapshot #" + snapshotMetaData.getIndex() + " ...");

                return new SnapshotImpl(snapshotState, snapshotMetaData);
            }
        }

        return null;
    }

    /**
     * Loads the builtin snapshot (last global snapshot) that is embedded in the jar.
     *
     * We first verify the integrity of the snapshot files by checking the signature of the files and then construct
     * a {@link Snapshot} from the retrieved information.
     *
     * We add the NULL_HASH as the only solid entry point and an empty list of seen milestones.
     *
     * @return the builtin snapshot (last global snapshot) that is embedded in the jar
     * @throws SnapshotException if anything goes wrong while loading the builtin {@link Snapshot}
     */
    private SnapshotImpl loadBuiltInSnapshot() throws SnapshotException {
        if (builtinSnapshot == null) {
            try {
                if (!config.isTestnet() && !SignedFiles.isFileSignatureValid(
                        config.getSnapshotFile(),
                        config.getSnapshotSignatureFile(),
                        SNAPSHOT_PUBKEY,
                        SNAPSHOT_PUBKEY_DEPTH,
                        SNAPSHOT_INDEX
                )) {
                    throw new SnapshotException("the snapshot signature is invalid");
                }
            } catch (IOException e) {
                throw new SnapshotException("failed to validate the signature of the builtin snapshot file", e);
            }

            SnapshotState snapshotState = readSnapshotStatefromFile(config.getSnapshotFile());
            if (!snapshotState.hasCorrectSupply()) {
                throw new SnapshotException("the snapshot state file has an invalid supply");
            }
            if (!snapshotState.isConsistent()) {
                throw new SnapshotException("the snapshot state file is not consistent");
            }

            HashMap<Hash, Integer> solidEntryPoints = new HashMap<>();
            solidEntryPoints.put(Hash.NULL_HASH, config.getMilestoneStartIndex());

            builtinSnapshot = new SnapshotImpl(
                    snapshotState,
                    new SnapshotMetaDataImpl(
                            Hash.NULL_HASH,
                            config.getMilestoneStartIndex(),
                            config.getSnapshotTime(),
                            solidEntryPoints,
                            new HashMap<>()
                    )
            );
        }

        return new SnapshotImpl(builtinSnapshot);
    }

    /**
     * This method reads the balances from the given file and creates the corresponding SnapshotState.
     *
     * The format of the file is pairs of "address;balance" separated by newlines. It simply reads the file line by
     * line, adding the corresponding values to the map.
     *
     * @param snapshotStateFilePath location of the snapshot state file
     * @return the unserialized version of the state file
     */
    private SnapshotState readSnapshotStatefromFile(String snapshotStateFilePath) throws SnapshotException {
        BufferedReader reader = null;
        try {
            InputStream snapshotStream = SnapshotImpl.class.getResourceAsStream(snapshotStateFilePath);
            if (snapshotStream == null) {
                snapshotStream = new FileInputStream(snapshotStateFilePath);
            }
            reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(snapshotStream)));

            Map<Hash, Long> state = new HashMap<>();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";", 2);
                if (parts.length >= 2) {
                    state.put(HashFactory.ADDRESS.create(parts[0]), Long.valueOf(parts[1]));
                } else {
                    throw new SnapshotException("malformed snapshot state file at " + snapshotStateFilePath);
                }
            }

            return new SnapshotStateImpl(state);
        } catch (IOException e) {
            throw new SnapshotException("failed to read the snapshot file at " + snapshotStateFilePath, e);
        } finally {
            IotaIOUtils.closeQuietly(reader);
        }
    }

    /**
     * This method retrieves the meta data of a snapshot from a file.
     *
     * It is used by local snapshots to determine the relevant information about the saved snapshot.
     *
     * @param snapshotMetaDataFile File object with the path to the snapshot metadata file
     * @return SnapshotMetaData instance holding all the relevant details about the snapshot
     * @throws SnapshotException if anything goes wrong while reading and parsing the file
     */
    private SnapshotMetaData readSnapshotMetaDatafromFile(File snapshotMetaDataFile) throws SnapshotException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(
                new FileInputStream(snapshotMetaDataFile))))) {

            Hash hash = readMilestoneHashFromMetaDataFile(reader);
            int index = readMilestoneIndexFromMetaDataFile(reader);
            long timestamp = readMilestoneTimestampFromMetaDataFile(reader);
            int amountOfSolidEntryPoints = readAmountOfSolidEntryPointsFromMetaDataFile(reader);
            int amountOfSeenMilestones = readAmountOfSeenMilestonesFromMetaDataFile(reader);
            Map<Hash, Integer> solidEntryPoints = readSolidEntryPointsFromMetaDataFile(reader,
                    amountOfSolidEntryPoints);
            Map<Hash, Integer> seenMilestones = readSeenMilestonesFromMetaDataFile(reader, amountOfSeenMilestones);

            return new SnapshotMetaDataImpl(hash, index, timestamp, solidEntryPoints, seenMilestones);
        } catch (IOException e) {
            throw new SnapshotException("failed to read from the snapshot metadata file at " +
                    snapshotMetaDataFile.getAbsolutePath(), e);
        }
    }

    /**
     * This method reads the transaction hash of the milestone that references the {@link Snapshot} from the meta data
     * file.
     *
     * @param reader reader that is used to read the file
     * @return Hash of the milestone transaction that references the {@link Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the hash from the file
     * @throws IOException if we could not read from the file
     */
    private Hash readMilestoneHashFromMetaDataFile(BufferedReader reader) throws SnapshotException, IOException {
        String line;
        if((line = reader.readLine()) == null) {
            throw new SnapshotException("could not read the transaction hash from the meta data file");
        }

        return HashFactory.TRANSACTION.create(line);
    }

    /**
     * This method reads the milestone index of the milestone that references the {@link Snapshot} from the meta data
     * file.
     *
     * @param reader reader that is used to read the file
     * @return milestone index of the milestone that references the {@link Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the milestone index from the file
     * @throws IOException if we could not read from the file
     */
    private int readMilestoneIndexFromMetaDataFile(BufferedReader reader) throws SnapshotException, IOException {
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
     * This method reads the timestamp of the milestone that references the {@link Snapshot} from the meta data file.
     *
     * @param reader reader that is used to read the file
     * @return timestamp of the milestone that references the {@link Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the milestone timestamp from the file
     * @throws IOException if we could not read from the file
     */
    private long readMilestoneTimestampFromMetaDataFile(BufferedReader reader) throws SnapshotException, IOException {
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
     * This method reads the amount of solid entry points of the {@link Snapshot} from the
     * meta data file.
     *
     * @param reader reader that is used to read the file
     * @return amount of solid entry points of the {@link Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the amount of solid entry points from the file
     * @throws IOException if we could not read from the file
     */
    private int readAmountOfSolidEntryPointsFromMetaDataFile(BufferedReader reader) throws SnapshotException,
            IOException {

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
     * This method reads the amount of seen milestones of the {@link Snapshot} from the
     * meta data file.
     *
     * @param reader reader that is used to read the file
     * @return amount of seen milestones of the {@link Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the amount of seen milestones from the file
     * @throws IOException if we could not read from the file
     */
    private int readAmountOfSeenMilestonesFromMetaDataFile(BufferedReader reader) throws SnapshotException,
            IOException {

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
     * This method reads the solid entry points of the {@link Snapshot} from the meta data file.
     *
     * @param reader reader that is used to read the file
     * @param amountOfSolidEntryPoints the amount of solid entry points we expect
     * @return the solid entry points of the {@link Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the solid entry points from the file
     * @throws IOException if we could not read from the file
     */
    private Map<Hash, Integer> readSolidEntryPointsFromMetaDataFile(BufferedReader reader, int amountOfSolidEntryPoints)
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
     * This method reads the seen milestones of the {@link Snapshot} from the meta data file.
     *
     * @param reader reader that is used to read the file
     * @param amountOfSeenMilestones the amount of seen milestones we expect
     * @return the seen milestones of the {@link Snapshot}
     * @throws SnapshotException if anything goes wrong while reading the seen milestones from the file
     * @throws IOException if we could not read from the file
     */
    private Map<Hash, Integer> readSeenMilestonesFromMetaDataFile(BufferedReader reader, int amountOfSeenMilestones)
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
}
