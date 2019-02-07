package com.iota.iri.service.spentaddresses.impl;

import com.iota.iri.BundleValidator;
import com.iota.iri.conf.MilestoneConfig;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesService;
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.service.tipselection.impl.TailFinderImpl;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.dag.DAGHelper;


import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;

import pl.touk.throwing.ThrowingPredicate;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Implementation of <tt>SpentAddressesService</tt> that calculates and checks spent addresses using the {@link Tangle}
 *
 */
public class SpentAddressesServiceImpl implements SpentAddressesService {
    private static final Logger log = LoggerFactory.getLogger(SpentAddressesServiceImpl.class);

    /**
     * Value for batch processing that was determined by testing
     */
    private static final int BATCH_INTERVAL = 2500;

    private Tangle tangle;

    private SnapshotProvider snapshotProvider;

    private SpentAddressesProvider spentAddressesProvider;

    private TailFinder tailFinder;

    private MilestoneConfig config;


    /**
     * Creates a Spent address service using the Tangle
     *
     * @param tangle                 Tangle object which is used to load models of addresses
     * @param snapshotProvider       {@link SnapshotProvider} to find the genesis, used to verify tails
     * @param spentAddressesProvider Provider for loading/saving addresses to a database.
     * @return this instance
     */
    public SpentAddressesServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SpentAddressesProvider spentAddressesProvider,
                                          MilestoneConfig config) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.spentAddressesProvider = spentAddressesProvider;
        this.tailFinder = new TailFinderImpl(tangle);
        this.config = config;

        return this;
    }


    @Override
    public boolean wasAddressSpentFrom(Hash addressHash) throws SpentAddressesException {
        return wasAddressSpentFrom(addressHash, getInitialUnspentAddresses());
    }

    @Override
    public void persistSpentAddresses(int fromMilestoneIndex, int toMilestoneIndex) throws SpentAddressesException {
        try{
            processInBatches(fromMilestoneIndex, toMilestoneIndex, new HashSet<>(), getInitialUnspentAddresses());
        } catch(Exception e){
            throw new SpentAddressesException(e);
        }
    }

    @Override
    public void persistSpentAddresses(Collection<TransactionViewModel> transactions) throws SpentAddressesException {
        try {
            Collection<Hash> spentAddresses = transactions.stream()
                    .filter(ThrowingPredicate.unchecked(this::wasTransactionSpentFrom))
                    .map(TransactionViewModel::getAddressHash)
                    .collect(Collectors.toSet());

            spentAddressesProvider.saveAddressesBatch(spentAddresses);
        } catch (RuntimeException e) {
            throw new SpentAddressesException("Exception while persisting spent addresses", e);
        }
    }

    private boolean wasTransactionSpentFrom(TransactionViewModel tx) throws Exception {
        Optional<Hash> tailFromTx = tailFinder.findTailFromTx(tx);
        if (tailFromTx.isPresent() && tx.value() < 0) {
            // Transaction is confirmed
            if (tx.snapshotIndex() != 0) {
                return true;
            }

            // transaction is pending
            Hash tailHash = tailFromTx.get();
            return isBundleValid(tailHash);
        }

        return false;
    }

    private boolean isBundleValid(Hash tailHash) throws Exception {
        List<List<TransactionViewModel>> validation =
                BundleValidator.validate(tangle, snapshotProvider.getInitialSnapshot(), tailHash);
        return (CollectionUtils.isNotEmpty(validation) && validation.get(0).get(0).getValidity() == 1);
    }

    /**
     *
     * @param addressHash the address in question
     * @param checkedAddresses known unspent addresses, used to skip calculations.
     *                         Must contain at least {@link Hash#NULL_HASH} and the coordinator address.
     * @return {@code true} if address was spent from, else {@code false}
     * @throws SpentAddressesException
     * @see #wasAddressSpentFrom(Hash)
     */
    private boolean wasAddressSpentFrom(Hash addressHash, Collection<Hash> checkedAddresses)
            throws SpentAddressesException {
        if (addressHash == null) {
            return false;
        }

        if (spentAddressesProvider.containsAddress(addressHash)) {
            return true;
        }

        //If address has already been checked this session, return false
        if (checkedAddresses.contains(addressHash)){
            return false;
        }

        try {
            Set<Hash> hashes = AddressViewModel.load(tangle, addressHash).getHashes();
            int setSizeLimit = 100_000;

            //If the hash set returned contains more than 100 000 entries, it likely will not be a spent address.
            //To avoid unnecessary overhead while processing, the loop will return false
            if (hashes.size() > setSizeLimit){
                checkedAddresses.add(addressHash);
                return false;
            }

            for (Hash hash: hashes) {
                TransactionViewModel tx = TransactionViewModel.fromHash(tangle, hash);
                // Check for spending transactions
                if (wasTransactionSpentFrom(tx)) {
                    return true;
                }
            }

        } catch (Exception e) {
            throw new SpentAddressesException(e);
        }

        checkedAddresses.add(addressHash);
        return false;
    }

    //Processing in batches in order to avoid OOM errors
    private void processInBatches(int fromMilestoneIndex, int toMilestoneIndex, Collection<Hash> addressesToCheck,
                                  Set<Hash> checkedAddresses) throws SpentAddressesException {
        try {
            int interval = BATCH_INTERVAL;
            double numBatches = Math.ceil(((double) toMilestoneIndex - fromMilestoneIndex) / interval);

            for (int batch = 0; batch < numBatches; batch++) {
                int batchStart = batch * interval + fromMilestoneIndex;
                int batchStop = batchStart + interval <= toMilestoneIndex ? batchStart + interval : toMilestoneIndex;

                for (int i = batchStart; i < batchStop; i++) {
                    try {
                        MilestoneViewModel currentMilestone = MilestoneViewModel.get(tangle, i);
                        if (currentMilestone != null) {
                            DAGHelper.get(tangle).traverseApprovees(
                                    currentMilestone.getHash(),
                                    transactionViewModel -> transactionViewModel.snapshotIndex() >= currentMilestone.index(),
                                    transactionViewModel -> addressesToCheck.add(transactionViewModel.getAddressHash())
                            );
                        }
                    } catch (Exception e) {
                        throw new SpentAddressesException(e);
                    }
                }
                checkAddresses(addressesToCheck, checkedAddresses);
            }
        }catch(SpentAddressesException e) {
            throw e;
        }
    }

    private void checkAddresses(Collection<Hash> addressesToCheck, Collection<Hash> checkedAddresses)
            throws SpentAddressesException {
        //Can only throw runtime exceptions in streams
        try {
            spentAddressesProvider.saveAddressesBatch(addressesToCheck.stream()
                    .filter(ThrowingPredicate.unchecked(address -> wasAddressSpentFrom(address, checkedAddresses)))
                    .collect(Collectors.toList()));

            //Clear addressesToCheck for next batch
            addressesToCheck.clear();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SpentAddressesException) {
                throw (SpentAddressesException) e.getCause();
            } else {
                throw e;
            }
        }

    }

    private Set<Hash> getInitialUnspentAddresses() {
        return Stream.of(Hash.NULL_HASH, HashFactory.ADDRESS.create(config.getCoordinator()))
                .collect(Collectors.toSet());
    }
}
