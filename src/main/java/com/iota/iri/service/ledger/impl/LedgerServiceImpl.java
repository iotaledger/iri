package com.iota.iri.service.ledger.impl;

import com.iota.iri.BundleValidator;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.StateDiffViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.ledger.LedgerException;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.snapshot.impl.SnapshotStateDiffImpl;
import com.iota.iri.storage.Tangle;

import java.util.*;

/**
 * Creates a service instance that allows us to perform ledger state specific operations.<br />
 * <br />
 * This class is stateless and does not hold any domain specific models.<br />
 */
public class LedgerServiceImpl implements LedgerService {
    /**
     * Holds the tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the service instance containing the business logic of the snapshot package.<br />
     */
    private SnapshotService snapshotService;

    /**
     * Holds a reference to the service instance containing the business logic of the milestone package.<br />
     */
    private MilestoneService milestoneService;

    /**
     * Initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code LedgerService ledgerService = new LedgerServiceImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots
     * @param snapshotService service instance of the snapshot package that gives us access to packages' business logic
     * @param milestoneService contains the important business logic when dealing with milestones
     * @return the initialized instance itself to allow chaining
     */
    public LedgerServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService,
            MilestoneService milestoneService) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
        this.milestoneService = milestoneService;

        return this;
    }

    @Override
    public boolean applyMilestoneToLedger(MilestoneViewModel milestone) throws LedgerException {
        if(generateStateDiff(milestone)) {
            try {
                snapshotService.replayMilestones(snapshotProvider.getLatestSnapshot(), milestone.index());
            } catch (SnapshotException e) {
                throw new LedgerException("failed to apply the balance changes to the ledger state", e);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean tipsConsistent(List<Hash> tips) throws LedgerException {
        Set<Hash> visitedHashes = new HashSet<>();
        Map<Hash, Long> diff = new HashMap<>();
        for (Hash tip : tips) {
            if (!isBalanceDiffConsistent(visitedHashes, diff, tip)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isBalanceDiffConsistent(Set<Hash> approvedHashes, Map<Hash, Long> diff, Hash tip) throws
            LedgerException {

        try {
            if (!TransactionViewModel.fromHash(tangle, tip).isSolid()) {
                return false;
            }
        } catch (Exception e) {
            throw new LedgerException("failed to check the consistency of the balance changes", e);
        }

        if (approvedHashes.contains(tip)) {
            return true;
        }
        Set<Hash> visitedHashes = new HashSet<>(approvedHashes);
        Map<Hash, Long> currentState = generateBalanceDiff(visitedHashes, tip,
                snapshotProvider.getLatestSnapshot().getIndex());
        if (currentState == null) {
            return false;
        }
        diff.forEach((key, value) -> {
            if (currentState.computeIfPresent(key, ((hash, aLong) -> value + aLong)) == null) {
                currentState.putIfAbsent(key, value);
            }
        });
        boolean isConsistent = snapshotProvider.getLatestSnapshot().patchedState(new SnapshotStateDiffImpl(
                currentState)).isConsistent();
        if (isConsistent) {
            diff.putAll(currentState);
            approvedHashes.addAll(visitedHashes);
        }
        return isConsistent;
    }

    @Override
    public Map<Hash, Long> generateBalanceDiff(Set<Hash> visitedTransactions, Hash milestoneHash, int milestoneIndex)
            throws LedgerException {

        Map<Hash, Long> state = new HashMap<>();
        Set<Hash> countedTx = new HashSet<>();

        snapshotProvider.getInitialSnapshot().getSolidEntryPoints().keySet().forEach(solidEntryPointHash -> {
            visitedTransactions.add(solidEntryPointHash);
            countedTx.add(solidEntryPointHash);
        });

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(milestoneHash));
        Hash transactionPointer;
        while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {
            if (visitedTransactions.add(transactionPointer)) {

                try {
                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle,
                            transactionPointer);

                    if (milestoneService.transactionBelongsToMilestone(transactionViewModel, milestoneIndex)) {

                        if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                            return null;
                        } else {
                            if (transactionViewModel.getCurrentIndex() == 0) {
                                boolean validBundle = false;

                                final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(
                                        tangle, snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

                                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {

                                    if (BundleValidator.isInconsistent(bundleTransactionViewModels)) {
                                        break;
                                    }

                                    if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {
                                        validBundle = true;

                                        for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {

                                            if (bundleTransactionViewModel.value() != 0 && countedTx.add(bundleTransactionViewModel.getHash())) {

                                                final Hash address = bundleTransactionViewModel.getAddressHash();
                                                final Long value = state.get(address);
                                                state.put(address, value == null ? bundleTransactionViewModel.value()
                                                        : Math.addExact(value, bundleTransactionViewModel.value()));
                                            }
                                        }

                                        break;
                                    }
                                }
                                if (!validBundle) {
                                    return null;
                                }
                            }

                            nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                            nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                        }
                    }
                } catch (Exception e) {
                    throw new LedgerException("unexpected error while generating the balance diff", e);
                }
            }
        }

        return state;
    }

    /**
     * Generates the {@link com.iota.iri.model.StateDiff} that belongs to the given milestone in the database and marks
     * all transactions that have been approved by the milestone accordingly by setting their {@code snapshotIndex}
     * value.<br />
     * <br />
     * It first checks if the {@code snapshotIndex} of the transaction belonging to the milestone was correctly set
     * already (to determine if this milestone was processed already) and proceeds to generate the {@link
     * com.iota.iri.model.StateDiff} if that is not the case. To do so, it calculates the balance changes, checks if
     * they are consistent and only then writes them to the database.<br />
     * <br />
     * If inconsistencies in the {@code snapshotIndex} are found it issues a reset of the corresponding milestone to
     * recover from this problem.<br />
     *
     * @param milestone the milestone that shall have its {@link com.iota.iri.model.StateDiff} generated
     * @return {@code true} if the {@link com.iota.iri.model.StateDiff} could be generated and {@code false} otherwise
     * @throws LedgerException if anything unexpected happens while generating the {@link com.iota.iri.model.StateDiff}
     */
    private boolean generateStateDiff(MilestoneViewModel milestone) throws LedgerException {

        try {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, milestone.getHash());

            if (!transactionViewModel.isSolid()) {
                return false;
            }

            final int transactionSnapshotIndex = transactionViewModel.snapshotIndex();
            boolean successfullyProcessed = transactionSnapshotIndex == milestone.index();
            if (!successfullyProcessed) {
                snapshotProvider.getLatestSnapshot().lockRead();
                try {
                    Hash tail = transactionViewModel.getHash();
                    Map<Hash, Long> balanceChanges = generateBalanceDiff(new HashSet<>(), tail,
                            snapshotProvider.getLatestSnapshot().getIndex());
                    successfullyProcessed = balanceChanges != null;
                    if (successfullyProcessed) {
                        successfullyProcessed = snapshotProvider.getLatestSnapshot().patchedState(
                                new SnapshotStateDiffImpl(balanceChanges)).isConsistent();
                        if (successfullyProcessed) {
                            milestoneService.updateMilestoneIndexOfMilestoneTransactions(milestone.getHash(),
                                    milestone.index());

                            if (!balanceChanges.isEmpty()) {
                                new StateDiffViewModel(balanceChanges, milestone.getHash()).store(tangle);
                            }
                        }
                    }
                } finally {
                    snapshotProvider.getLatestSnapshot().unlockRead();
                }
            }

            return successfullyProcessed;
        } catch (Exception e) {
            throw new LedgerException("unexpected error while generating the StateDiff for " + milestone, e);
        }
    }
}
