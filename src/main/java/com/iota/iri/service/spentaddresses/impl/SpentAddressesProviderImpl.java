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
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

/**
 *
 * Implementation of <tt>SpentAddressesProvider</tt>.
 * Addresses are saved/found on the {@link Tangle}.
 * The folder location is provided by {@link IotaConfig#getLocalSnapshotsBasePath()}
 *
 */
public class SpentAddressesProviderImpl implements SpentAddressesProvider {

    private final SnapshotConfig config;

    private final PersistenceProvider provider;

    /**
     * Implements the spent addresses provider interface.
     * @param config The snapshot configuration used for file location
     * @param provider A persistence provider for load/save the spent addresses
     */
    public SpentAddressesProviderImpl(SnapshotConfig configuration, PersistenceProvider persistenceProvider) {
        this.config = configuration;
        this.provider = persistenceProvider;
    }

    /**
     * Starts the SpentAddressesProvider by reading the previous spent addresses from files.
     * @throws SpentAddressesException if we failed to create a file at the designated location
     */
    public void init() throws SpentAddressesException {
        try {
            this.provider.init();
            readPreviousEpochsSpentAddresses();
        } catch (Exception e) {
            throw new SpentAddressesException("There is a problem with accessing stored spent addresses", e);
        }
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
            return provider.exists(SpentAddress.class, addressHash);
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void saveAddress(Hash addressHash) throws SpentAddressesException {
        try {
            provider.save(new SpentAddress(), addressHash);
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void saveAddressesBatch(Collection<Hash> addressHash) throws SpentAddressesException {
        try {
            // Its bytes are always new byte[0], therefore identical in storage
            SpentAddress spentAddressModel = new SpentAddress();
            provider.saveBatch(addressHash
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
        for (byte[] bytes : provider.loadAllKeysFromTable(SpentAddress.class)) {
            addresses.add(HashFactory.ADDRESS.create(bytes));
        }
        return addresses;
    }
}
