package com.iota.iri.service.spentaddresses.impl;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class SpentAddressesProviderImpl implements SpentAddressesProvider {
    private static final Logger log = LoggerFactory.getLogger(SpentAddressesProviderImpl.class);

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
                readSpentAddressesFromStream(SpentAddressesProviderImpl.class.getResourceAsStream(previousEpochsSpentAddressesFile));
            } catch (SpentAddressesException e) {
                log.error("failed to read spent addresses from " + previousEpochsSpentAddressesFile, e);
            }
        }
    }

    private void readLocalSpentAddresses() {
        String pathToLocalStateFile = config.getLocalSnapshotsBasePath() + ".snapshot.spentaddresses";
        try {
            readSpentAddressesFromStream(new FileInputStream(config.getLocalSnapshotsBasePath() + ".snapshot.spentaddresses"));
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
    public void writeSpentAddressesToDisk(String basePath) {
        // TODO: iterate over db and dump file
    }
}
