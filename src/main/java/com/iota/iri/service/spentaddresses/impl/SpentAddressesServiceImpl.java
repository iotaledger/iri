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
import com.iota.iri.conf.IotaConfig;


import java.util.*;
import java.util.stream.Collectors;

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

    private Tangle tangle;

    private SnapshotProvider snapshotProvider;

    private SpentAddressesProvider spentAddressesProvider;

    private TailFinder tailFinder;

    private IotaConfig config;


    /**
     * Creates a Spent address service using the Tangle
     *
     * @param tangle                 Tangle object which is used to load models of addresses
     * @param snapshotProvider       {@link SnapshotProvider} to find the genesis, used to verify tails
     * @param spentAddressesProvider Provider for loading/saving addresses to a database.
     * @return this instance
     */
    public SpentAddressesServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SpentAddressesProvider spentAddressesProvider,
                                          IotaConfig config) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.spentAddressesProvider = spentAddressesProvider;
        this.tailFinder = new TailFinderImpl(tangle);
        this.config = config;

        return this;
    }

    @Override
    public boolean wasAddressSpentFrom(Hash addressHash, Set<Hash> addressesChecked) throws SpentAddressesException {
        if (spentAddressesProvider.containsAddress(addressHash)) {
            return true;
        }

        //If address has already been checked this session, return false
        if (addressesChecked.contains(addressHash)){
            return false;
        }

        //If address is either null or equal to the coordinator address
        if (addressHash.toString().equals(Hash.NULL_HASH.toString()) ||
                addressHash.toString().equals(config.getCoordinator())) {
            return false;
        }


        try {
            Set<Hash> hashes = AddressViewModel.load(tangle, addressHash).getHashes();
            int setSizeLimit = 100000;

            //If the hash set returned contains more than 100 000 entries, it likely will not be a spent address.
            //To avoid unnecessary overhead while processing, the loop will return false
            if (hashes.size() > setSizeLimit){
                addressesChecked.add(addressHash);
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
        addressesChecked.add(addressHash);
        return false;
    }

    @Override
    public void persistSpentAddresses(int fromMilestoneIndex, int toMilestoneIndex) throws SpentAddressesException {
        Set<Hash> addressesToCheck = new HashSet<>();
        Set<Hash> addressesChecked = new HashSet<>();
        int interval = 2500;
        try{
            processInBatches(fromMilestoneIndex, toMilestoneIndex, addressesToCheck, addressesChecked);
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

    private void processInBatches(int fromMilestoneIndex, int toMilestoneIndex, Set<Hash> addressesToCheck,
                                  Set<Hash> addressesChecked) throws SpentAddressesException {
        try {
            //Process 2500 milestones at a time
            int interval = 2500;
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
            }
            checkAddresses(addressesToCheck, addressesChecked);
        }catch(SpentAddressesException e) {
            throw e;
        }
    }

    private void checkAddresses(Set<Hash> addressesToCheck, Set<Hash> addressesChecked) throws SpentAddressesException {
        //Can only throw runtime exceptions in streams
        try {
            spentAddressesProvider.saveAddressesBatch(addressesToCheck.stream()
                    .filter(ThrowingPredicate.unchecked(address -> wasAddressSpentFrom(address, addressesChecked)))
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
}
