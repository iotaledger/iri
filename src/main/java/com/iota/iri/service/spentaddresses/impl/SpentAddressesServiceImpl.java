package com.iota.iri.service.spentaddresses.impl;

import com.iota.iri.BundleValidator;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesService;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.dag.DAGHelper;

import java.util.HashSet;
import java.util.Set;

public class SpentAddressesServiceImpl implements SpentAddressesService {
    private Tangle tangle;

    private SnapshotProvider snapshotProvider;

    private SpentAddressesProvider spentAddressesProvider;

    public SpentAddressesServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SpentAddressesProvider spentAddressesProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.spentAddressesProvider = spentAddressesProvider;

        return this;
    }

    @Override
    public boolean wasAddressSpentFrom(Hash addressHash) throws SpentAddressesException {
        if (spentAddressesProvider.containsAddress(addressHash)) {
            return true;
        }

        try {
            Set<Hash> hashes = AddressViewModel.load(tangle, addressHash).getHashes();
            for (Hash hash : hashes) {
                final TransactionViewModel tx = TransactionViewModel.fromHash(tangle, hash);
                // Check for spending transactions
                if (tx.value() < 0) {
                    // Transaction is confirmed
                    if (tx.snapshotIndex() != 0) {
                        return true;
                    }

                    // Transaction is pending
                    Hash tail = findTail(hash);
                    if (tail != null && BundleValidator.validate(tangle, snapshotProvider.getInitialSnapshot(), tail).size() != 0) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }

        return false;
    }

    @Override
    public void calculateSpentAddresses(int fromMilestoneIndex, int toMilestoneIndex) throws SpentAddressesException {
        Set<Hash> addressesToCheck = new HashSet<>();
        try {
            for (int i = fromMilestoneIndex; i < toMilestoneIndex; i++) {
                MilestoneViewModel currentMilestone = MilestoneViewModel.get(tangle, i);
                if (currentMilestone != null) {
                    DAGHelper.get(tangle).traverseApprovees(
                        currentMilestone.getHash(),
                        transactionViewModel -> transactionViewModel.snapshotIndex() >= currentMilestone.index(),
                        transactionViewModel -> addressesToCheck.add(transactionViewModel.getAddressHash())
                    );
                }
            }
        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }

        for (Hash address : addressesToCheck) {
            if (wasAddressSpentFrom(address)) {
                spentAddressesProvider.addAddress(address);
            }
        }
    }

    /**
     * Walks back from the hash until a tail transaction has been found or transaction aprovee is not found.
     * A tail transaction is the first transaction in a bundle, thus with <code>index = 0</code>
     *
     * @param hash The transaction hash where we start the search from. If this is a tail, its hash is returned.
     * @return The transaction hash of the tail
     * @throws Exception When a model could not be loaded.
     */
    private Hash findTail(Hash hash) throws Exception {
        TransactionViewModel tx = TransactionViewModel.fromHash(tangle, hash);
        final Hash bundleHash = tx.getBundleHash();
        long index = tx.getCurrentIndex();
        boolean foundApprovee = false;

        // As long as the index is bigger than 0 and we are still traversing the same bundle
        // If the hash we asked about is already a tail, this loop never starts
        while (index-- > 0 && tx.getBundleHash().equals(bundleHash)) {
            Set<Hash> approvees = tx.getApprovers(tangle).getHashes();
            for (Hash approvee : approvees) {
                TransactionViewModel nextTx = TransactionViewModel.fromHash(tangle, approvee);
                if (nextTx.getBundleHash().equals(bundleHash)) {
                    tx = nextTx;
                    foundApprovee = true;
                    break;
                }
            }
            if (!foundApprovee) {
                break;
            }
        }

        if (tx.getCurrentIndex() == 0) {
            return tx.getHash();
        }
        return null;
    }
}
