package com.iota.iri.service.milestone.impl;

import com.iota.iri.BundleValidator;
import com.iota.iri.conf.MilestoneConfig;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.ISS;
import com.iota.iri.crypto.ISSInPlace;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.StateDiff;
import com.iota.iri.service.milestone.MilestoneException;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneValidity;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.dag.DAGHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.iota.iri.controllers.TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET;
import static com.iota.iri.service.milestone.MilestoneValidity.*;

/**
 * <p>
 * Creates a service instance that allows us to perform milestone specific operations.
 * </p>
 * <p>
 * This class is stateless and does not hold any domain specific models.
 * </p>
 */
public class MilestoneServiceImpl implements MilestoneService {
    /**
     * Holds the logger of this class.
     */
    private final static Logger log = LoggerFactory.getLogger(MilestoneServiceImpl.class);

    /**
     * Holds the tangle object which acts as a database interface.
     */
    private Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the service instance of the snapshot package that allows us to roll back ledger states.
     */
    private SnapshotService snapshotService;

    /**
     * Configurations for milestone
     */
    private MilestoneConfig config;

    private BundleValidator bundleValidator;

    /**
     * <p>
     * This method initializes the instance and registers its dependencies.
     * </p>
     * <p>
     * It stores the passed in values in their corresponding private properties.
     * </p>
     * <p>
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:
     * </p>
     *       {@code milestoneService = new MilestoneServiceImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots
     * @param snapshotService service for modifying and generating snapshots
     * @param bundleValidator Validator to use when checking milestones
     * @param config config with important milestone specific settings
     * @return the initialized instance itself to allow chaining
     */
    public MilestoneServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService,
            BundleValidator bundleValidator, MilestoneConfig config) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
        this.bundleValidator = bundleValidator;
        this.config = config;

        return this;
    }

    //region {PUBLIC METHODS] //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     * 
     * <p>
     * We first check the trivial case where the node was fully synced. If no processed solid milestone could be found
     * within the last two milestones of the node, we perform a binary search from present to past, which reduces the
     * amount of database requests to a minimum (even with a huge amount of milestones in the database).
     * </p>
     */
    @Override
    public Optional<MilestoneViewModel> findLatestProcessedSolidMilestoneInDatabase() throws MilestoneException {
        try {
            // if we have no milestone in our database -> abort
            MilestoneViewModel latestMilestone = MilestoneViewModel.latest(tangle);
            if (latestMilestone == null) {
                return Optional.empty();
            }

             // trivial case #1: the node was fully synced
            if (wasMilestoneAppliedToLedger(latestMilestone)) {
                return Optional.of(latestMilestone);
            }

             // trivial case #2: the node was fully synced but the last milestone was not processed, yet
            MilestoneViewModel latestMilestonePredecessor = MilestoneViewModel.findClosestPrevMilestone(tangle,
                    latestMilestone.index(), snapshotProvider.getInitialSnapshot().getIndex());
            if (latestMilestonePredecessor != null && wasMilestoneAppliedToLedger(latestMilestonePredecessor)) {
                return Optional.of(latestMilestonePredecessor);
            }

             // non-trivial case: do a binary search in the database
            return binarySearchLatestProcessedSolidMilestoneInDatabase(latestMilestone);
        } catch (Exception e) {
            throw new MilestoneException(
                    "unexpected error while trying to find the latest processed solid milestone in the database", e);
        }
    }

    @Override
    public void updateMilestoneIndexOfMilestoneTransactions(Hash milestoneHash, int index) throws MilestoneException {
        if (index <= 0) {
            throw new MilestoneException("the new index needs to be bigger than 0 " +
                    "(use resetCorruptedMilestone to reset the milestone index)");
        }

        updateMilestoneIndexOfMilestoneTransactions(milestoneHash, index, index, new HashSet<>());
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * We redirect the call to {@link #resetCorruptedMilestone(int, Set)} while initiating the set of {@code
     * processedTransactions} with an empty {@link HashSet} which will ensure that we reset all found
     * transactions.
     * </p>
     */
    @Override
    public void resetCorruptedMilestone(int index) throws MilestoneException {
        resetCorruptedMilestone(index, new HashSet<>());
    }

    @Override
    public MilestoneValidity validateMilestone(TransactionViewModel transactionViewModel, int milestoneIndex)
            throws MilestoneException {

        if (milestoneIndex < 0 || milestoneIndex >= config.getMaxMilestoneIndex()) {
            return INVALID;
        }

        try {
            if (MilestoneViewModel.get(tangle, milestoneIndex) != null) {
                // Already validated.
                return VALID;
            }

            final List<List<TransactionViewModel>> bundleTransactions = bundleValidator.validate(tangle,
                    snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

            if (bundleTransactions.isEmpty()) {
                return INCOMPLETE;
            } else {
                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {
                    final TransactionViewModel tail = bundleTransactionViewModels.get(0);
                    if (tail.getHash().equals(transactionViewModel.getHash())) {
                        //the signed transaction - which references the confirmed transactions and contains
                        // the Merkle tree siblings.
                        int coordinatorSecurityLevel = config.getCoordinatorSecurityLevel();
                        final TransactionViewModel siblingsTx =
                                bundleTransactionViewModels.get(coordinatorSecurityLevel);

                        if (isMilestoneBundleStructureValid(bundleTransactionViewModels, coordinatorSecurityLevel)) {
                            //milestones sign the normalized hash of the sibling transaction.
                            byte[] signedHash = ISS.normalizedBundle(siblingsTx.getHash().trits());

                            //validate leaf signature
                            ByteBuffer bb = ByteBuffer.allocate(Curl.HASH_LENGTH * coordinatorSecurityLevel);
                            byte[] digest = new byte[Curl.HASH_LENGTH];

                            SpongeFactory.Mode coordinatorSignatureMode = config.getCoordinatorSignatureMode();
                            for (int i = 0; i < coordinatorSecurityLevel; i++) {
                                ISSInPlace.digest(coordinatorSignatureMode, signedHash,
                                        ISS.NUMBER_OF_FRAGMENT_CHUNKS * i,
                                        bundleTransactionViewModels.get(i).getSignature(), 0, digest);
                                bb.put(digest);
                            }

                            byte[] digests = bb.array();
                            byte[] address = ISS.address(coordinatorSignatureMode, digests);

                            //validate Merkle path
                            byte[] merkleRoot = ISS.getMerkleRoot(coordinatorSignatureMode, address,
                                    siblingsTx.trits(), 0, milestoneIndex, config.getNumberOfKeysInMilestone());
                            boolean skipValidation = config.isTestnet() && config.isDontValidateTestnetMilestoneSig();
                            if (skipValidation || config.getCoordinator().equals(HashFactory.ADDRESS.create(merkleRoot))) {
                                MilestoneViewModel newMilestoneViewModel = new MilestoneViewModel(milestoneIndex,
                                        transactionViewModel.getHash());
                                newMilestoneViewModel.store(tangle);

                                // if we find a NEW milestone that should have been processed before our latest solid
                                // milestone -> reset the ledger state and check the milestones again
                                //
                                // NOTE: this can happen if a new subtangle becomes solid before a previous one while
                                //       syncing
                                if (milestoneIndex < snapshotProvider.getLatestSnapshot().getIndex() &&
                                        milestoneIndex > snapshotProvider.getInitialSnapshot().getIndex()) {

                                     resetCorruptedMilestone(milestoneIndex);
                                }

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
    public boolean isTransactionConfirmed(TransactionViewModel transaction, int milestoneIndex) {
        return transaction.snapshotIndex() != 0 && transaction.snapshotIndex() <= milestoneIndex;
    }

    @Override
    public boolean isTransactionConfirmed(TransactionViewModel transaction) {
        return isTransactionConfirmed(transaction, snapshotProvider.getLatestSnapshot().getIndex());
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [PRIVATE UTILITY METHODS] /////////////////////////////////////////////////////////////////////////////////

    /**
     * <p>
     * Performs a binary search for the latest solid milestone which was already processed by the node and applied to
     * the ledger state at some point in the past (i.e. before IRI got restarted).
     * </p>
     * <p>
     * It searches from present to past using a binary search algorithm which quickly narrows down the amount of
     * candidates even for big databases.
     * </p>
     *
     * @param latestMilestone the latest milestone in the database (used to define the search range)
     * @return the latest solid milestone that was previously processed by IRI or an empty value if no previously
     *         processed solid milestone can be found
     * @throws Exception if anything unexpected happens while performing the search
     */
    private Optional<MilestoneViewModel> binarySearchLatestProcessedSolidMilestoneInDatabase(
            MilestoneViewModel latestMilestone) throws Exception {

        Optional<MilestoneViewModel> lastAppliedCandidate = Optional.empty();

        int rangeEnd = latestMilestone.index();
        int rangeStart = snapshotProvider.getInitialSnapshot().getIndex() + 1;
        while (rangeEnd - rangeStart >= 0) {
            // if no candidate found in range -> return last candidate
            MilestoneViewModel currentCandidate = getMilestoneInMiddleOfRange(rangeStart, rangeEnd);
            if (currentCandidate == null) {
                return lastAppliedCandidate;
            }

             // if the milestone was applied -> continue to search for "later" ones that might have also been applied
            if (wasMilestoneAppliedToLedger(currentCandidate)) {
                rangeStart = currentCandidate.index() + 1;

                lastAppliedCandidate = Optional.of(currentCandidate);
            }

             // if the milestone was not applied -> continue to search for "earlier" ones
            else {
                rangeEnd = currentCandidate.index() - 1;
            }
        }

        return lastAppliedCandidate;
    }

    /**
     * <p>
     * Determines the milestone in the middle of the range defined by {@code rangeStart} and {@code rangeEnd}.
     * </p>
     * <p>
     * It is used by the binary search algorithm of {@link #findLatestProcessedSolidMilestoneInDatabase()}. It first
     * calculates the index that represents the middle of the range and then tries to find the milestone that is closest
     * to this index.
     * </p>
     * Note: We start looking for younger milestones first, because most of the times the latest processed solid
     *       milestone is close to the end.
     *
     * @param rangeStart the milestone index representing the start of our search range
     * @param rangeEnd the milestone index representing the end of our search range
     * @return the milestone that is closest to the middle of the given range or {@code null} if no milestone can be
     *         found
     * @throws Exception if anything unexpected happens while trying to get the milestone
     */
    private MilestoneViewModel getMilestoneInMiddleOfRange(int rangeStart, int rangeEnd) throws Exception {
        int range = rangeEnd - rangeStart;
        int middleOfRange = rangeEnd - range / 2;

        MilestoneViewModel milestone = MilestoneViewModel.findClosestNextMilestone(tangle, middleOfRange - 1, rangeEnd);
        if (milestone == null) {
            milestone = MilestoneViewModel.findClosestPrevMilestone(tangle, middleOfRange, rangeStart);
        }

        return milestone;
    }

    /**
     * <p>
     * Checks if the milestone was applied to the ledger at some point in the past (before a restart of IRI).
     * </p>
     * <p>
     * Since the {@code snapshotIndex} value is used as a flag to determine if the milestone was already applied to the
     * ledger, we can use it to determine if it was processed by IRI in the past. If this value is set we should also
     * have a corresponding {@link StateDiff} entry in the database.
     * </p>
     *
     * @param milestone the milestone that shall be checked
     * @return {@code true} if the milestone has been processed by IRI before and {@code false} otherwise
     * @throws Exception if anything unexpected happens while checking the milestone
     */
    private boolean wasMilestoneAppliedToLedger(MilestoneViewModel milestone) throws Exception {
        TransactionViewModel milestoneTransaction = TransactionViewModel.fromHash(tangle, milestone.getHash());
        return milestoneTransaction.getType() != TransactionViewModel.PREFILLED_SLOT &&
                milestoneTransaction.snapshotIndex() != 0;
    }

    /**
     * This method implements the logic described by {@link #updateMilestoneIndexOfMilestoneTransactions(Hash, int)} but
     * accepts some additional parameters that allow it to be reused by different parts of this service.
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

        Set<Integer> inconsistentMilestones = new HashSet<>();
        Set<TransactionViewModel> transactionsToUpdate = new HashSet<>();

        try {
            prepareMilestoneIndexUpdate(TransactionViewModel.fromHash(tangle, milestoneHash), correctIndex, newIndex,
                    inconsistentMilestones, transactionsToUpdate);

            DAGHelper.get(tangle).traverseApprovees(
                milestoneHash,
                currentTransaction -> {
                    // if the transaction was confirmed by a PREVIOUS milestone -> check if we have a back-referencing
                    // transaction and abort the traversal
                    if (isTransactionConfirmed(currentTransaction, correctIndex - 1)) {
                        patchSolidEntryPointsIfNecessary(snapshotProvider.getInitialSnapshot(), currentTransaction);

                        return false;
                    }

                    return true;
                },
                currentTransaction -> prepareMilestoneIndexUpdate(currentTransaction, correctIndex, newIndex,
                        inconsistentMilestones, transactionsToUpdate),
                processedTransactions
            );
        } catch (Exception e) {
            throw new MilestoneException("error while updating the milestone index", e);
        }

        for(int inconsistentMilestoneIndex : inconsistentMilestones) {
            resetCorruptedMilestone(inconsistentMilestoneIndex, processedTransactions);
        }

        for (TransactionViewModel transactionToUpdate : transactionsToUpdate) {
            updateMilestoneIndexOfSingleTransaction(transactionToUpdate, newIndex);
        }
    }

    /**
     * <p>
     * This method resets the {@code milestoneIndex} of a single transaction.
     * </p>
     * <p>
     * In addition to setting the corresponding value, we also publish a message to the ZeroMQ message provider, which
     * allows external recipients to get informed about this change.
     * </p>
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

        tangle.publish("%s %s %d sn", transaction.getAddressHash(), transaction.getHash(), index);
        tangle.publish("sn %d %s %s %s %s %s", index, transaction.getHash(), transaction.getAddressHash(),
                transaction.getTrunkTransactionHash(), transaction.getBranchTransactionHash(),
                transaction.getBundleHash());
    }

    /**
     * <p>
     * This method prepares the update of the milestone index by checking the current {@code snapshotIndex} of the given
     * transaction.
     * </p>
     * <p>
     * If the {@code snapshotIndex} is higher than the "correct one", we know that we applied the milestones in the
     * wrong order and need to reset the corresponding milestone that wrongly approved this transaction. We therefore
     * add its index to the {@code corruptMilestones} set.
     * </p>
     * <p>
     * If the milestone does not have the new value set already we add it to the set of {@code transactionsToUpdate} so
     * it can be updated by the caller accordingly.
     * </p>
     *
     * @param transaction the transaction that shall get its milestoneIndex updated
     * @param correctMilestoneIndex the milestone index that this transaction should be associated to (the index of the
     *                              milestone that should "confirm" this transaction)
     * @param newMilestoneIndex the new milestone index that we want to assign (either 0 or the correctMilestoneIndex)
     * @param corruptMilestones a set that is used to collect all corrupt milestones indexes [output parameter]
     * @param transactionsToUpdate a set that is used to collect all transactions that need to be updated [output
     *                             parameter]
     */
    private void prepareMilestoneIndexUpdate(TransactionViewModel transaction, int correctMilestoneIndex,
            int newMilestoneIndex, Set<Integer> corruptMilestones, Set<TransactionViewModel> transactionsToUpdate) {

        if(transaction.snapshotIndex() > correctMilestoneIndex) {
            corruptMilestones.add(transaction.snapshotIndex());
        }
        if (transaction.snapshotIndex() != newMilestoneIndex) {
            transactionsToUpdate.add(transaction);
        }
    }

    /**
     * <p>
     * This method patches the solid entry points if a back-referencing transaction is detected.
     * </p>
     * <p>
     * While we iterate through the approvees of a milestone we stop as soon as we arrive at a transaction that has a
     * smaller {@code snapshotIndex} than the milestone. If this {@code snapshotIndex} is also smaller than the index of
     * the milestone of our local snapshot, we have detected a back-referencing transaction.
     * </p>
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
     * <p>
     * This method is a utility method that checks if the transactions belonging to the potential milestone bundle have
     * a valid structure (used during the validation of milestones).
     * </p>
     * <p>
     * It first checks if the bundle has enough transactions to conform to the given {@code securityLevel} and then
     * verifies that the {@code branchTransactionsHash}es are pointing to the {@code trunkTransactionHash} of the head
     * transactions.
     * </p>
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

    /**
     * <p>
     * This method does the same as {@link #resetCorruptedMilestone(int)} but additionally receives a set of {@code
     * processedTransactions} that will allow us to not process the same transactions over and over again while
     * resetting additional milestones in recursive calls.
     * </p>
     * <p>
     * It first checks if the desired {@code milestoneIndex} is reachable by this node and then triggers the reset
     * by:
     * </p>
     * <ol>
     * <li>resetting the ledger state if it addresses a milestone before the current latest solid milestone</li>
     * <li>resetting the {@code milestoneIndex} of all transactions that were confirmed by the current milestone</li>
     * <li>deleting the corresponding {@link StateDiff} entry from the database</li>
     * </ol>
     * 
     * @param index milestone index that shall be reverted
     * @param processedTransactions a set of transactions that have been processed already
     * @throws MilestoneException if anything goes wrong while resetting the corrupted milestone
     */
    private void resetCorruptedMilestone(int index, Set<Hash> processedTransactions) throws MilestoneException {
        if(index <= snapshotProvider.getInitialSnapshot().getIndex()) {
            return;
        }
         log.info("resetting corrupted milestone #" + index);
         try {
            MilestoneViewModel milestoneToRepair = MilestoneViewModel.get(tangle, index);
            if(milestoneToRepair != null) {
                if(milestoneToRepair.index() <= snapshotProvider.getLatestSnapshot().getIndex()) {
                    snapshotService.rollBackMilestones(snapshotProvider.getLatestSnapshot(), milestoneToRepair.index());
                }
                 updateMilestoneIndexOfMilestoneTransactions(milestoneToRepair.getHash(), milestoneToRepair.index(), 0,
                        processedTransactions);
                tangle.delete(StateDiff.class, milestoneToRepair.getHash());
            }
        } catch (Exception e) {
            throw new MilestoneException("failed to repair corrupted milestone with index #" + index, e);
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
