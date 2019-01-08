package com.iota.iri.service.spentaddresses.impl;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Pair;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Implementation of <tt>SpentAddressesProvider</tt>.
 * Addresses are saved/found on the {@link Tangle}.
 * The addresses will be written to a file called {@value #SNAPSHOT_SPENTADDRESSES_FILE}.
 * The folder location is provided by {@link IotaConfig#getLocalSnapshotsBasePath()}
 *
 */
public class SpentAddressesProviderImpl implements SpentAddressesProvider {
    private static final Logger log = LoggerFactory.getLogger(SpentAddressesProviderImpl.class);
    private static final String SNAPSHOT_SPENTADDRESSES_FILE = ".snapshot.spentaddresses";

    private RocksDBPersistenceProvider rocksDBPersistenceProvider;

    private SnapshotConfig config;

    private File localSnapshotAddressesFile;

    /**
     * Creates a new instance of SpentAddressesProvider
     */
    public SpentAddressesProviderImpl() {
        this.rocksDBPersistenceProvider = new RocksDBPersistenceProvider("spent-addresses-db",
                "spent-addresses-log", 1000,
                new HashMap<String, Class<? extends Persistable>>(1)
                {{put("spent-addresses", SpentAddress.class);}}, null);
    }

    /**
     * Starts the SpentAddressesProvider by reading the previous spent addresses from file.
     * If {@value #SNAPSHOT_SPENTADDRESSES_FILE} already exists, these addresses will be read as well.
     *
     * @param config The snapshot configuration used for file location
     * @return the current instance
     * @throws SpentAddressesException if we failed to create a file at the designated location
     */
    public SpentAddressesProviderImpl init(SnapshotConfig config)
            throws SpentAddressesException {
        this.config = config;
        this.localSnapshotAddressesFile = new File(config.getLocalSnapshotsBasePath() + SNAPSHOT_SPENTADDRESSES_FILE);
        this.rocksDBPersistenceProvider.init();

        readPreviousEpochsSpentAddresses();
        if (localSnapshotAddressesFile.exists()) {
            readLocalSpentAddresses();
        }
        else {
            try {
                localSnapshotAddressesFile.createNewFile();
            } catch (IOException e) {
                throw new SpentAddressesException("Failed to create missing " + localSnapshotAddressesFile.getName(), e);
            }
        }

        return this;
    }

    private void readPreviousEpochsSpentAddresses() {
        if (config.isTestnet()) {
            return;
        }

        for (String previousEpochsSpentAddressesFile : config.getPreviousEpochSpentAddressesFiles().split(" ")) {
            try {
                readSpentAddressesFromStream(
                        SpentAddressesProviderImpl.class.getResourceAsStream(previousEpochsSpentAddressesFile));
            } catch (SpentAddressesException e) {
                log.error("failed to read spent addresses from " + previousEpochsSpentAddressesFile, e);
            }
        }
    }

    private void readLocalSpentAddresses() {

        try {
            readSpentAddressesFromStream(
                    new FileInputStream(localSnapshotAddressesFile));
        } catch (Exception e) {
            log.error("failed to read spent addresses from " + localSnapshotAddressesFile.getPath(), e);
        }
    }

    private void readSpentAddressesFromStream(InputStream in) throws SpentAddressesException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addAddress(HashFactory.ADDRESS.create(line));
            }
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public boolean containsAddress(Hash addressHash) throws SpentAddressesException {
        try {
            return ((SpentAddress) rocksDBPersistenceProvider.get(SpentAddress.class, addressHash)).exists;
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void addAddress(Hash addressHash) throws SpentAddressesException {
        try {
            rocksDBPersistenceProvider.save(new SpentAddress(), addressHash);
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void addAddressesBatch(Collection<Hash> addressHash) throws SpentAddressesException {
        try {
            // Its bytes are always new byte[0], therefore identical in storage
            SpentAddress spentAddressModel = new SpentAddress();

            rocksDBPersistenceProvider.saveBatch(addressHash
                .stream()
                .map(address -> new Pair<Indexable, Persistable>(address, spentAddressModel))
                .collect(Collectors.toList())
            );
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void writeSpentAddressesToDisk(String basePath) throws SpentAddressesException {
        try {
            Collection<Hash> addressHashes = getAllSpentAddresses();
            FileUtils.writeLines(localSnapshotAddressesFile, addressHashes, false);
        } catch (Exception e) {
            throw new SpentAddressesException("Failed to dump spent addresses to disk", e);
        }
    }

    private List<Hash> getAllSpentAddresses() {
        return rocksDBPersistenceProvider.loadAllKeysFromTable(SpentAddress.class).stream()
                .map(HashFactory.ADDRESS::create)
                .collect(Collectors.toList());
    }
}
