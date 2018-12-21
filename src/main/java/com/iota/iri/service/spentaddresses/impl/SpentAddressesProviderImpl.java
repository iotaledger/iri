package com.iota.iri.service.spentaddresses.impl;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.storage.Tangle;

import java.io.*;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpentAddressesProviderImpl implements SpentAddressesProvider {
    private static final Logger log = LoggerFactory.getLogger(SpentAddressesProviderImpl.class);
    private static final String SNAPSHOT_SPENTADDRESSES_FILE = ".snapshot.spentaddresses";

    private Tangle tangle;

    private IotaConfig config;

    public SpentAddressesProviderImpl() {}

    public SpentAddressesProviderImpl init(Tangle tangle, IotaConfig config) {
        this.tangle = tangle;
        this.config = config;

        readPreviousEpochsSpentAddresses();
        readLocalSpentAddresses();

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
        String pathToLocalStateFile = config.getLocalSnapshotsBasePath() + SNAPSHOT_SPENTADDRESSES_FILE;
        try {
            readSpentAddressesFromStream(
                    new FileInputStream(config.getLocalSnapshotsBasePath() + SNAPSHOT_SPENTADDRESSES_FILE));
        } catch (Exception e) {
            log.error("failed to read spent addresses from " + pathToLocalStateFile, e);
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
            return ((SpentAddress) tangle.load(SpentAddress.class, addressHash)).exists;
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void addAddress(Hash addressHash) throws SpentAddressesException {
        try {
            tangle.save(new SpentAddress(), addressHash);
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void writeSpentAddressesToDisk(String basePath) throws SpentAddressesException {
        try {
            Collection<Hash> addressHashes = getAllSpentAddresses();
            File snapshotFile = new File(config.getLocalSnapshotsBasePath() + SNAPSHOT_SPENTADDRESSES_FILE);
            FileUtils.writeLines(snapshotFile, addressHashes, false);
        } catch (Exception e) {
            throw new SpentAddressesException("Failed to dump spent addresses to disk", e);
        }
    }

    private List<Hash> getAllSpentAddresses() {
        return tangle.loadAllKeysFromTable(SpentAddress.class, HashFactory.ADDRESS::create);
    }
}
