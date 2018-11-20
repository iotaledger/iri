package com.iota.iri.service.milestone.impl;

import com.iota.iri.BundleValidator;
import com.iota.iri.conf.ConsensusConfig;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.ISS;
import com.iota.iri.crypto.ISSInPlace;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.service.milestone.MilestoneException;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneValidity;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.dag.DAGHelper;
import com.iota.iri.zmq.MessageQ;

import java.nio.ByteBuffer;
import java.util.*;

import static com.iota.iri.controllers.TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET;
import static com.iota.iri.service.milestone.MilestoneValidity.INCOMPLETE;
import static com.iota.iri.service.milestone.MilestoneValidity.INVALID;
import static com.iota.iri.service.milestone.MilestoneValidity.VALID;

/**
 * Creates a service instance that allows us to perform milestone specific operations.<br />
 * <br />
 * This class is stateless and does not hold any domain specific models.<br />
 */
public class MilestoneServiceImpl implements MilestoneService {
    /**
     * Holds the tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds the ZeroMQ interface that allows us to emit messages for external recipients.<br />
     */
    private MessageQ messageQ;

    /**
     * Holds the config with important milestone specific settings.<br />
     */
    private ConsensusConfig config;

    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code milestoneService = new MilestoneServiceImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots
     * @param messageQ ZeroMQ interface that allows us to emit messages for external recipients
     * @param config config with important milestone specific settings
     * @return the initialized instance itself to allow chaining
     */
    public MilestoneServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, MessageQ messageQ,
            ConsensusConfig config) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.messageQ = messageQ;
        this.config = config;

        return this;
    }

    //region {PUBLIC METHODS] //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void updateMilestoneIndexOfMilestoneTransactions(Hash milestoneHash, int index) throws MilestoneException {
        if (index <= 0) {
            throw new MilestoneException("the new index needs to be bigger than 0 " +
                    "(use resetCorruptedMilestone to reset the milestone index)");
        }

        updateMilestoneIndexOfMilestoneTransactions(milestoneHash, index, index, new HashSet<>());
    }

    @Override
    public MilestoneValidity validateMilestone(TransactionViewModel transactionViewModel, SpongeFactory.Mode mode,
            int securityLevel) throws MilestoneException {

        int milestoneIndex = getMilestoneIndex(transactionViewModel);
        if (milestoneIndex < 0 || milestoneIndex >= 0x200000) {
            return INVALID;
        }

        try {
            if (MilestoneViewModel.get(tangle, milestoneIndex) != null) {
                // Already validated.
                return VALID;
            }

            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle,
                    snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

            if (bundleTransactions.isEmpty()) {
                return INCOMPLETE;
            } else {
                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {
                    final TransactionViewModel tail = bundleTransactionViewModels.get(0);
                    if (tail.getHash().equals(transactionViewModel.getHash())) {
                        //the signed transaction - which references the confirmed transactions and contains
                        // the Merkle tree siblings.
                        final TransactionViewModel siblingsTx = bundleTransactionViewModels.get(securityLevel);

                        if (isMilestoneBundleStructureValid(bundleTransactionViewModels, securityLevel)) {
                            //milestones sign the normalized hash of the sibling transaction.
                            byte[] signedHash = ISS.normalizedBundle(siblingsTx.getHash().trits());

                            //validate leaf signature
                            ByteBuffer bb = ByteBuffer.allocate(Curl.HASH_LENGTH * securityLevel);
                            byte[] digest = new byte[Curl.HASH_LENGTH];

                            for (int i = 0; i < securityLevel; i++) {
                                ISSInPlace.digest(mode, signedHash, ISS.NUMBER_OF_FRAGMENT_CHUNKS * i,
                                        bundleTransactionViewModels.get(i).getSignature(), 0, digest);
                                bb.put(digest);
                            }

                            byte[] digests = bb.array();
                            byte[] address = ISS.address(mode, digests);

                            //validate Merkle path
                            byte[] merkleRoot = ISS.getMerkleRoot(mode, address,
                                    siblingsTx.trits(), 0, milestoneIndex, config.getNumberOfKeysInMilestone());
                            if ((config.isTestnet() && config.isDontValidateTestnetMilestoneSig()) ||
                                    (HashFactory.ADDRESS.create(merkleRoot)).equals(
                                            HashFactory.ADDRESS.create(config.getCoordinator()))) {

                                MilestoneViewModel newMilestoneViewModel = new MilestoneViewModel(milestoneIndex,
                                        transactionViewModel.getHash());
                                newMilestoneViewModel.store(tangle);

                                return VALID;
                            } else {
                                return INVALID;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new MilestoneException("error while checking milestone status of " + transactionViewModel, e);
        }

        return INVALID;
    }

    @Override
    public int getMilestoneIndex(TransactionViewModel milestoneTransaction) {
        return (int) Converter.longValue(milestoneTransaction.trits(), OBSOLETE_TAG_TRINARY_OFFSET, 15);
    }

    @Override
    public boolean transactionBelongsToMilestone(TransactionViewModel transaction, int milestoneIndex) {
        return transaction.snapshotIndex() == 0 || transaction.snapshotIndex() >= milestoneIndex;
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [PRIVATE UTILITY METHODS] /////////////////////////////////////////////////////////////////////////////////

    /**
     * This method implements the logic described by {@link #updateMilestoneIndexOfMilestoneTransactions(Hash, int)} but
     * accepts some additional parameters that allow it to be reused by different parts of this service.<br />
     *
     * @param milestoneHash the hash of the transaction
     * @param correctIndex the milestone index of the milestone that would be set if all transactions are marked
     *                     correctly
     * @param newIndex the milestone index that shall be set
     * @throws MilestoneException if anything unexpected happens while updating the milestone index
     * @param processedTransactions a set of transactions that have been processed already (for the recursive calls)
     */
    private void updateMilestoneIndexOfMilestoneTransactions(Hash milestoneHash, int correctIndex, int newIndex,
            Set<Hash> processedTransactions) throws MilestoneException {

        processedTransactions.add(milestoneHash);

        Set<TransactionViewModel> transactionsToUpdate = new HashSet<>();

        try {
            prepareMilestoneIndexUpdate(TransactionViewModel.fromHash(tangle, milestoneHash), newIndex,
                    transactionsToUpdate);

            DAGHelper.get(tangle).traverseApprovees(
                milestoneHash,
                currentTransaction -> {
                    if (transactionBelongsToMilestone(currentTransaction, correctIndex)) {
                        return true;
                    } else {
                        patchSolidEntryPointsIfNecessary(snapshotProvider.getInitialSnapshot(), currentTransaction);

                        return false;
                    }
                },
                currentTransaction -> prepareMilestoneIndexUpdate(currentTransaction, newIndex, transactionsToUpdate),
                processedTransactions
            );
        } catch (Exception e) {
            throw new MilestoneException("error while updating the milestone index", e);
        }

        for (TransactionViewModel transactionToUpdate : transactionsToUpdate) {
            updateMilestoneIndexOfSingleTransaction(transactionToUpdate, newIndex);
        }
    }

    /**
     * This method resets the {@code milestoneIndex} of a single transaction.<br />
     * <br />
     * In addition to setting the corresponding value, we also publish a message to the ZeroMQ message provider, which
     * allows external recipients to get informed about this change.<br />
     *
     * @param transaction the transaction that shall have its {@code milestoneIndex} reset
     * @param index the milestone index that is set for the given transaction
     * @throws MilestoneException if anything unexpected happens while updating the transaction
     */
    private void updateMilestoneIndexOfSingleTransaction(TransactionViewModel transaction, int index) throws
            MilestoneException {

        try {
            transaction.setSnapshot(tangle, snapshotProvider.getInitialSnapshot(), index);
        } catch (Exception e) {
            throw new MilestoneException("error while updating the snapshotIndex of " + transaction, e);
        }

        messageQ.publish("%s %s %d sn", transaction.getAddressHash(), transaction.getHash(), index);
        messageQ.publish("sn %d %s %s %s %s %s", index, transaction.getHash(), transaction.getAddressHash(),
                transaction.getTrunkTransactionHash(), transaction.getBranchTransactionHash(),
                transaction.getBundleHash());
    }

    /**
     * This method prepares the update of the milestone index by checking the current {@code snapshotIndex} of the given
     * transaction.<br />
     * <br />
     * If the milestone does not have the new value set already we add it to the set of {@code transactionsToUpdate} so
     * it can be updated by the caller accordingly.<br />
     *
     * @param transaction the transaction that shall get its milestoneIndex updated
     * @param newMilestoneIndex the new milestone index that we want to assign (either 0 or the correctMilestoneIndex)
     * @param transactionsToUpdate a set that is used to collect all transactions that need to be updated [output
     *                             parameter]
     */
    private void prepareMilestoneIndexUpdate(TransactionViewModel transaction, int newMilestoneIndex,
            Set<TransactionViewModel> transactionsToUpdate) {

        if (transaction.snapshotIndex() != newMilestoneIndex) {
            transactionsToUpdate.add(transaction);
        }
    }

    /**
     * This method patches the solid entry points if a back-referencing transaction is detected.<br />
     * <br />
     * While we iterate through the approvees of a milestone we stop as soon as we arrive at a transaction that has a
     * smaller {@code snapshotIndex} than the milestone. If this {@code snapshotIndex} is also smaller than the index of
     * the milestone of our local snapshot, we have detected a back-referencing transaction.<br />
     *
     * @param initialSnapshot the initial snapshot holding the solid entry points
     * @param transaction the transactions that was referenced by the processed milestone
     */
    private void patchSolidEntryPointsIfNecessary(Snapshot initialSnapshot, TransactionViewModel transaction) {
        if (transaction.snapshotIndex() <= initialSnapshot.getIndex() && !initialSnapshot.hasSolidEntryPoint(
                transaction.getHash())) {

            initialSnapshot.getSolidEntryPoints().put(transaction.getHash(), initialSnapshot.getIndex());
        }
    }

    /**
     * This method is a utility method that checks if the transactions belonging to the potential milestone bundle have
     * a valid structure (used during the validation of milestones).<br />
     * <br />
     * It first checks if the bundle has enough transactions to conform to the given {@code securityLevel} and then
     * verifies that the {@code branchTransactionsHash}es are pointing to the {@code trunkTransactionHash} of the head
     * transactions.<br />
     *
     * @param bundleTransactions all transactions belonging to the milestone
     * @param securityLevel the security level used for the signature
     * @return {@code true} if the basic structure is valid and {@code false} otherwise
     */
    private boolean isMilestoneBundleStructureValid(List<TransactionViewModel> bundleTransactions, int securityLevel) {
        if (bundleTransactions.size() <= securityLevel) {
            return false;
        }

        Hash headTransactionHash = bundleTransactions.get(securityLevel).getTrunkTransactionHash();
        return bundleTransactions.stream()
                .limit(securityLevel)
                .map(TransactionViewModel::getBranchTransactionHash)
                .allMatch(branchTransactionHash -> branchTransactionHash.equals(headTransactionHash));
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
