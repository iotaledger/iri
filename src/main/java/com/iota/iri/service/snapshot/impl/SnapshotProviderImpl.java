package com.iota.iri.service.snapshot.impl;

import com.iota.iri.SignedFiles;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

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

                SnapshotState snapshotState = SnapshotStateImpl.fromFile(localSnapshotFile.getAbsolutePath());
                if (!snapshotState.hasCorrectSupply()) {
                    throw new SnapshotException("the snapshot state file has an invalid supply");
                }
                if (!snapshotState.isConsistent()) {
                    throw new SnapshotException("the snapshot state file is not consistent");
                }

                SnapshotMetaDataImpl snapshotMetaData = SnapshotMetaDataImpl.fromFile(localSnapshotMetadDataFile);

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

            SnapshotState snapshotState = SnapshotStateImpl.fromFile(config.getSnapshotFile());
            if (!snapshotState.hasCorrectSupply()) {
                throw new IllegalStateException("the snapshot state file has an invalid supply");
            }
            if (!snapshotState.isConsistent()) {
                throw new IllegalStateException("the snapshot state file is not consistent");
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
}
