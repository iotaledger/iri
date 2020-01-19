package com.iota.iri.service.snapshot.impl;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.SignedFiles;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.LocalSnapshotViewModel;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.LocalSnapshot;
import com.iota.iri.service.snapshot.*;
import com.iota.iri.service.spentaddresses.SpentAddressesException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.LocalSnapshotsPersistenceProvider;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Creates a data provider for the two {@link Snapshot} instances that are relevant for the node.
 * </p>
 * <p>
 * It provides access to the two relevant {@link Snapshot} instances:
 * <ul>
 *     <li>
 *         the {@link #initialSnapshot} (the starting point of the ledger based on the last global or local Snapshot)
 *     </li>
 *     <li>
 *         the {@link #latestSnapshot} (the state of the ledger after applying all changes up till the latest confirmed
 *         milestone)
 *     </li>
 * </ul>
 * </p>
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
    private static final int SNAPSHOT_INDEX = 12;

    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(SnapshotProviderImpl.class);

    /**
     * <p>
     * Holds a cached version of the builtin snapshot.
     * </p>
     * Note: The builtin snapshot is embedded in the iri.jar and will not change. To speed up tests that need the
     *       snapshot multiple times while creating their own version of the LocalSnapshotManager, we cache the instance
     *       here so they don't have to rebuild it from the scratch every time (massively speeds up the unit tests).
     */
    @VisibleForTesting
    static SnapshotImpl builtinSnapshot = null;

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

    private LocalSnapshotsPersistenceProvider localSnapshotsDb;

    /**
     * Implements the snapshot provider interface.
     * @param configuration Snapshot configuration properties.
     */
    public SnapshotProviderImpl(SnapshotConfig configuration, LocalSnapshotsPersistenceProvider localSnapshotsDb) {
        this.config = configuration;
        this.localSnapshotsDb = localSnapshotsDb;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() throws SnapshotException, SpentAddressesException {
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
    public void persistSnapshot(Snapshot snapshot) throws SnapshotException {
        snapshot.lockRead();
        try {
            log.info("persisting local snapshot; ms hash/index: {}/{}, solid entry points: {}, seen milestones: {}, " +
                            "ledger entries: {}", snapshot.getHash().toString(), snapshot.getIndex(),
                    snapshot.getSolidEntryPoints().size(), snapshot.getSeenMilestones().size(),
                    snapshot.getBalances().size());
            // persist new one
            new LocalSnapshotViewModel(snapshot.getHash(), snapshot.getIndex(), snapshot.getTimestamp(),
                    snapshot.getSolidEntryPoints(), snapshot.getSeenMilestones(), snapshot.getBalances())
                            .store(localSnapshotsDb);
            log.info("persisted local snapshot; ms hash/index: {}/{}", snapshot.getHash().toString(),
                    snapshot.getIndex());
        } catch (Exception e) {
            throw new SnapshotException("failed to persist local snapshot", e);
        } finally {
            snapshot.unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        log.info("Shutting down local snapshots Persistence Providers... ");
        initialSnapshot = null;
        latestSnapshot = null;
        localSnapshotsDb.shutdown();
    }

    //region SNAPSHOT RELATED UTILITY METHODS //////////////////////////////////////////////////////////////////////////

    /**
     * <p>
     * Loads the snapshots that are provided by this data provider.
     * </p>
     * <p>
     * We first check if a valid local {@link Snapshot} exists by trying to load it. If we fail to load the local
     * {@link Snapshot}, we fall back to the builtin one.
     * </p>
     * <p>
     * After the {@link #initialSnapshot} was successfully loaded we create a copy of it that will act as the "working
     * copy" that will keep track of the latest changes that get applied while the node operates and processes the new
     * confirmed transactions.
     * </p>
     * @throws SnapshotException if anything goes wrong while loading the snapshots
     */
    private void loadSnapshots() throws SnapshotException, SpentAddressesException {
        initialSnapshot = loadLocalSnapshot();
        if (initialSnapshot == null) {
            initialSnapshot = loadBuiltInSnapshot();
        }

        latestSnapshot = initialSnapshot.clone();
    }

    /**
     * <p>
     * Loads the last local snapshot from the database.
     * </p>
     * <p>
     * This method returns null if no previous local snapshot was persisted.
     * </p>
     * 
     * @return local snapshot of the node
     * @throws SnapshotException if local snapshot files exist but are malformed
     */
    private Snapshot loadLocalSnapshot() throws SnapshotException {
        if (!config.getLocalSnapshotsEnabled()) {
            return null;
        }
        try {
            Pair<Indexable, Persistable> pair = localSnapshotsDb.first(LocalSnapshot.class, IntegerIndex.class);
            if (pair.hi == null) {
                log.info("no local snapshot persisted in the database");
                return null;
            }

            LocalSnapshot ls = (LocalSnapshot) pair.hi;
            log.info("loading local snapshot; ms hash/index: {}/{}, solid entry points: {}, seen milestones: {}, " +
                            "ledger entries: {}", ls.milestoneHash, ls.milestoneIndex,
                    ls.solidEntryPoints.size(), ls.seenMilestones.size(), ls.ledgerState.size());

            SnapshotState snapshotState = new SnapshotStateImpl(ls.ledgerState);
            if (!snapshotState.hasCorrectSupply()) {
                throw new SnapshotException("the snapshot state file has an invalid supply");
            }
            if (!snapshotState.isConsistent()) {
                throw new SnapshotException("the snapshot state file is not consistent");
            }
            SnapshotMetaData snapshotMetaData = new SnapshotMetaDataImpl(ls.milestoneHash, ls.milestoneIndex,
                    ls.milestoneTimestamp, ls.solidEntryPoints, ls.seenMilestones);

            log.info("resumed from local snapshot #" + snapshotMetaData.getIndex() + " ...");
            return new SnapshotImpl(snapshotState, snapshotMetaData);
        } catch (Exception e) {
            throw new SnapshotException("failed to load existing local snapshot data", e);
        }
    }

    /**
     * <p>
     * Loads the builtin snapshot (last global snapshot) that is embedded in the jar (if a different path is provided it
     * can also load from the disk).
     * </p>
     * <p>
     * We first verify the integrity of the snapshot files by checking the signature of the files and then construct
     * a {@link Snapshot} from the retrieved information.
     * </p>
     * <p>
     * We add the NULL_HASH as the only solid entry point and an empty list of seen milestones.
     * </p>
     * @return the builtin snapshot (last global snapshot) that is embedded in the jar
     * @throws SnapshotException if anything goes wrong while loading the builtin {@link Snapshot}
     */
    private Snapshot loadBuiltInSnapshot() throws SnapshotException {
        if (builtinSnapshot != null) {
            return builtinSnapshot.clone();
        }
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

        SnapshotState snapshotState;
        try {
            snapshotState = readSnapshotStateFromJAR(config.getSnapshotFile());
        } catch (SnapshotException e) {
            snapshotState = readSnapshotStatefromFile(config.getSnapshotFile());
        }
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
        return builtinSnapshot.clone();
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region SNAPSHOT STATE RELATED UTILITY METHODS ////////////////////////////////////////////////////////////////////

    /**
     * <p>
     * This method reads the balances from the given file on the disk and creates the corresponding SnapshotState.
     * </p>
     * <p>
     * It creates the corresponding reader and for the file on the given location and passes it on to
     * {@link #readSnapshotState(BufferedReader)}.
     * </p>
     * 
     * @param snapshotStateFilePath location of the snapshot state file
     * @return the unserialized version of the state file
     * @throws SnapshotException if anything goes wrong while reading the state file
     */
    private SnapshotState readSnapshotStatefromFile(String snapshotStateFilePath) throws SnapshotException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(snapshotStateFilePath))))) {
            return readSnapshotState(reader);
        } catch (IOException e) {
            throw new SnapshotException("failed to read the snapshot file at " + snapshotStateFilePath, e);
        }
    }

    /**
     * <p>
     * This method reads the balances from the given file in the JAR and creates the corresponding SnapshotState.
     * </p>
     * <p>
     * It creates the corresponding reader and for the file on the given location in the JAR and passes it on to
     * {@link #readSnapshotState(BufferedReader)}.
     * </p>
     *
     * @param snapshotStateFilePath location of the snapshot state file
     * @return the unserialized version of the state file
     * @throws SnapshotException if anything goes wrong while reading the state file
     */
    private SnapshotState readSnapshotStateFromJAR(String snapshotStateFilePath) throws SnapshotException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(SnapshotProviderImpl.class.getResourceAsStream(snapshotStateFilePath))))) {
            return readSnapshotState(reader);
        } catch (NullPointerException | IOException e) {
            throw new SnapshotException("failed to read the snapshot file from JAR at " + snapshotStateFilePath, e);
        }
    }

    /**
     * <p>
     * This method reads the balances from the given reader.
     * </p>
     * <p>
     * The format of the input is pairs of "address;balance" separated by newlines. It simply reads the input line by
     * line, adding the corresponding values to the map.
     * </p>
     * 
     * @param reader reader allowing us to retrieve the lines of the {@link SnapshotState} file
     * @return the unserialized version of the snapshot state state file
     * @throws IOException if something went wrong while trying to access the file
     * @throws SnapshotException if anything goes wrong while reading the state file
     */
    private SnapshotState readSnapshotState(BufferedReader reader) throws IOException, SnapshotException {
        Map<Hash, Long> state = new HashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(";", 2);
            if (parts.length == 2) {
                state.put(HashFactory.ADDRESS.create(parts[0]), Long.valueOf(parts[1]));
            } else {
                throw new SnapshotException("malformed snapshot state file");
            }
        }

        return new SnapshotStateImpl(state);
    }

    /**
     * <p>
     * This method dumps the current state to a file.
     * </p>
     * <p>
     * It is used by local snapshots to persist the in memory states and allow IRI to resume from the local snapshot.
     * </p>
     * 
     * @param snapshotState state object that shall be written
     * @param snapshotPath location of the file that shall be written
     * @throws SnapshotException if anything goes wrong while writing the file
     */
    private void writeSnapshotStateToDisk(SnapshotState snapshotState, String snapshotPath) throws SnapshotException {
        try {
            Files.write(
                    Paths.get(snapshotPath),
                    () -> snapshotState.getBalances().entrySet()
                            .stream()
                            .filter(entry -> entry.getValue() != 0)
                            .<CharSequence>map(entry -> entry.getKey() + ";" + entry.getValue())
                            .sorted()
                            .iterator()
            );
        } catch (IOException e) {
            throw new SnapshotException("failed to write the snapshot state file at " + snapshotPath, e);
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region SNAPSHOT METADATA RELATED UTILITY METHODS /////////////////////////////////////////////////////////////////

    /**
     * <p>
     * This method retrieves the metadata of a snapshot from a file.
     * </p>
     * <p>
     * It is used by local snapshots to determine the relevant information about the saved snapshot.
     * </p>
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
     * This method reads the transaction hash of the milestone that references the {@link Snapshot} from the metadata
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
            throw new SnapshotException("could not read the transaction hash from the metadata file");
        }

        return HashFactory.TRANSACTION.create(line);
    }

    /**
     * This method reads the milestone index of the milestone that references the {@link Snapshot} from the metadata
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
                throw new SnapshotException("could not read the milestone index from the metadata file");
            }

            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new SnapshotException("could not parse the milestone index from the metadata file", e);
        }
    }

    /**
     * This method reads the timestamp of the milestone that references the {@link Snapshot} from the metadata file.
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
                throw new SnapshotException("could not read the milestone timestamp from the metadata file");
            }

            return Long.parseLong(line);
        } catch (NumberFormatException e) {
            throw new SnapshotException("could not parse the milestone timestamp from the metadata file", e);
        }
    }

    /**
     * This method reads the amount of solid entry points of the {@link Snapshot} from the metadata file.
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
                throw new SnapshotException("could not read the amount of solid entry points from the metadata file");
            }

            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new SnapshotException("could not parse the amount of solid entry points from the metadata file", e);
        }
    }

    /**
     * This method reads the amount of seen milestones of the {@link Snapshot} from the metadata file.
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
                throw new SnapshotException("could not read the amount of seen milestones from the metadata file");
            }

            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            throw new SnapshotException("could not parse the amount of seen milestones from the metadata file", e);
        }
    }

    /**
     * This method reads the solid entry points of the {@link Snapshot} from the metadata file.
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
                throw new SnapshotException("could not read a solid entry point from the metadata file");
            }

            String[] parts = line.split(";", 2);
            if(parts.length == 2) {
                try {
                    solidEntryPoints.put(HashFactory.TRANSACTION.create(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    throw new SnapshotException("could not parse a solid entry point from the metadata file", e);
                }
            } else {
                throw new SnapshotException("could not parse a solid entry point from the metadata file");
            }
        }

        return solidEntryPoints;
    }

    /**
     * This method reads the seen milestones of the {@link Snapshot} from the metadata file.
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
                throw new SnapshotException("could not read a seen milestone from the metadata file");
            }

            String[] parts = line.split(";", 2);
            if(parts.length == 2) {
                try {
                    seenMilestones.put(HashFactory.TRANSACTION.create(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    throw new SnapshotException("could not parse a seen milestone from the metadata file", e);
                }
            } else {
                throw new SnapshotException("could not parse a seen milestone from the metadata file");
            }
        }

        return seenMilestones;
    }

    /**
     * <p>
     * This method writes a file containing a serialized version of the metadata object.
     * </p>
     * <p>
     * It can be used to store the current values and read them on a later point in time. It is used by the local
     * snapshot manager to generate and maintain the snapshot files.
     * </p>
     * @param snapshotMetaData metadata object that shall be written
     * @param filePath location of the file that shall be written
     * @throws SnapshotException if anything goes wrong while writing the file
     */
    private void writeSnapshotMetaDataToDisk(SnapshotMetaData snapshotMetaData, String filePath)
            throws SnapshotException {

        try {
            Map<Hash, Integer> solidEntryPoints = snapshotMetaData.getSolidEntryPoints();
            Map<Hash, Integer> seenMilestones = snapshotMetaData.getSeenMilestones();

            Files.write(
                    Paths.get(filePath),
                    () -> Stream.concat(
                            Stream.of(
                                    snapshotMetaData.getHash().toString(),
                                    String.valueOf(snapshotMetaData.getIndex()),
                                    String.valueOf(snapshotMetaData.getTimestamp()),
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
            throw new SnapshotException("failed to write snapshot metadata file at " + filePath, e);
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
