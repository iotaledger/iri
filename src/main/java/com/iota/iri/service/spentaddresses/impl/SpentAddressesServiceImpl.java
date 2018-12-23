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
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.service.tipselection.impl.TailFinderImpl;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.dag.DAGHelper;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 
 * Implementation of <tt>SpentAddressesService</tt> that calculates and checks spent addresses using the {@link Tangle}
 *
 */
public class SpentAddressesServiceImpl implements SpentAddressesService {
    private Tangle tangle;

    private SnapshotProvider snapshotProvider;

    private SpentAddressesProvider spentAddressesProvider;
    
    private TailFinder tailFinder;

    /**
     * Creates a Spent address service using the Tangle
     * 
     * @param tangle Tangle object which is used to load models of addresses
     * @param snapshotProvider {@link SnapshotProvider} to find the genesis, used to verify tails
     * @param spentAddressesProvider Provider for loading/saving addresses to a database.
     * @return this instance
     */
    public SpentAddressesServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SpentAddressesProvider spentAddressesProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.spentAddressesProvider = spentAddressesProvider;
        this.tailFinder = new TailFinderImpl(tangle);
        
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

        //Can only throw runtime exceptions in streams
        try {
            spentAddressesProvider.addAddressesBatch(addressesToCheck.stream()
                .filter(address -> {
                    try {
                        return wasAddressSpentFrom(address);
                    } catch (SpentAddressesException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList()));
            
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SpentAddressesException) {
                throw (SpentAddressesException) e.getCause();
            } else {
                throw e;
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
        Optional<Hash> optionalTail = tailFinder.findTail(hash);
        return optionalTail.isPresent() ? optionalTail.get() : null;
    }
}
