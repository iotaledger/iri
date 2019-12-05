package com.iota.iri.service.spentaddresses.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.AddressHash;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.LocalSnapshotsPersistenceProvider;
import com.iota.iri.utils.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Implementation of <tt>SpentAddressesProvider</tt>.
 * Addresses are saved/found on the {@link Tangle}.
 * The folder location is provided by {@link IotaConfig#getLocalSnapshotsDbPath()}
 *
 */
public class SpentAddressesProviderImpl implements SpentAddressesProvider {

    private static final Logger log = LoggerFactory.getLogger(SpentAddressesProvider.class);

    private final SnapshotConfig config;
    private LocalSnapshotsPersistenceProvider localSnapshotsPersistenceProvider;

    /**
     * Implements the spent addresses provider interface.
     * @param configuration The snapshot configuration used for file location
     */
    public SpentAddressesProviderImpl(SnapshotConfig configuration, LocalSnapshotsPersistenceProvider localSnapshotsPersistenceProvider) {
        this.config = configuration;
        this.localSnapshotsPersistenceProvider = localSnapshotsPersistenceProvider;
    }

    /**
     * Starts the SpentAddressesProvider by reading the previous spent addresses from files.
     * @throws SpentAddressesException if we failed to create a file at the designated location
     */
    public void init(boolean assertSpentAddressesExistence) throws SpentAddressesException {
        try {
            if (assertSpentAddressesExistence && !doSpentAddressesExist(localSnapshotsPersistenceProvider)) {
                log.error("Expecting to start with a localsnapshots-db containing spent addresses when initializing " +
                        "from a local snapshot. Shutting down now");
                //explicitly exiting rather than throwing an exception
                System.exit(1);
            }
            readPreviousEpochsSpentAddresses();
        } catch (Exception e) {
            throw new SpentAddressesException("There is a problem with accessing stored spent addresses", e);
        }
    }

    private boolean doSpentAddressesExist(LocalSnapshotsPersistenceProvider provider) throws Exception {
        Pair<Indexable, Persistable> first = provider.first(SpentAddress.class, AddressHash.class);
        return first.hi != null && ((SpentAddress) first.hi).exists();
    }

    private void readPreviousEpochsSpentAddresses() throws SpentAddressesException {
        if (config.isTestnet()) {
            return;
        }

        for (String previousEpochsSpentAddressesFile : config.getPreviousEpochSpentAddressesFiles().split(" ")) {
                readSpentAddressesFromStream(
                        SpentAddressesProviderImpl.class.getResourceAsStream(previousEpochsSpentAddressesFile));
        }
    }

    private void readSpentAddressesFromStream(InputStream in) throws SpentAddressesException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                saveAddress(HashFactory.ADDRESS.create(line));
            }
        } catch (Exception e) {
            throw new SpentAddressesException("Failed to read or save spent address", e);
        }
    }

    @Override
    public boolean containsAddress(Hash addressHash) throws SpentAddressesException {
        try {
            return localSnapshotsPersistenceProvider.exists(SpentAddress.class, addressHash);
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void saveAddress(Hash addressHash) throws SpentAddressesException {
        try {
            localSnapshotsPersistenceProvider.save(new SpentAddress(), addressHash);
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void saveAddressesBatch(Collection<Hash> addressHash) throws SpentAddressesException {
        try {
            // Its bytes are always new byte[0], therefore identical in storage
            SpentAddress spentAddressModel = new SpentAddress();
            localSnapshotsPersistenceProvider.saveBatch(addressHash
                .stream()
                .map(address -> new Pair<Indexable, Persistable>(address, spentAddressModel))
                .collect(Collectors.toList())
            );
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public List<Hash> getAllAddresses() {
        List<Hash> addresses = new ArrayList<>();
        for (byte[] bytes : localSnapshotsPersistenceProvider.loadAllKeysFromTable(SpentAddress.class)) {
            addresses.add(HashFactory.ADDRESS.create(bytes));
        }
        return addresses;
    }
}
