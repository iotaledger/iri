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
import com.iota.iri.zmq.MessageQ;

import java.util.*;

/**
 * Represents the service that contains all the relevant business logic for modifying and calculating the ledger
 * state.<br />
 * <br />
 * This class is stateless and does not hold any domain specific models.<br />
 */
public class LedgerServiceImpl implements LedgerService {
    /**
     * Holds a reference to the service instance containing the business logic of the milestone package.<br />
     */
    private final MilestoneService milestoneService;

    /**
     * Holds a reference to the service instance containing the business logic of the snapshot package.<br />
     */
    private final SnapshotService snapshotService;

    /**
     * Creates a service instance that allows us to perform ledger state specific operations.<br />
     * <br />
     * It simply stores the passed in dependencies in the internal properties.<br />
     *
     * @param snapshotService service instance of the snapshot package that allows us to rollback ledger states
     */
    public LedgerServiceImpl(SnapshotService snapshotService, MilestoneService milestoneService) {
        this.snapshotService = snapshotService;
        this.milestoneService = milestoneService;
    }

    @Override
    public boolean applyMilestoneToLedger(Tangle tangle, SnapshotProvider snapshotProvider, MessageQ messageQ,
            MilestoneViewModel milestone) throws LedgerException {

        if(generateStateDiff(tangle, snapshotProvider, messageQ, milestone)) {
            try {
                snapshotService.replayMilestones(tangle, snapshotProvider.getLatestSnapshot(), milestone.index());
            } catch (SnapshotException e) {
                throw new LedgerException("failed to apply the balance changes to the ledger state", e);
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean tipsConsistent(Tangle tangle, SnapshotProvider snapshotProvider, List<Hash> tips) throws
            LedgerException {

        Set<Hash> visitedHashes = new HashSet<>();
        Map<Hash, Long> diff = new HashMap<>();
        for (Hash tip : tips) {
            if (!isBalanceDiffConsistent(tangle, snapshotProvider, visitedHashes, diff, tip)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isBalanceDiffConsistent(Tangle tangle, SnapshotProvider snapshotProvider, Set<Hash> approvedHashes,
            final Map<Hash, Long> diff, Hash tip) throws LedgerException {

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
        Map<Hash, Long> currentState = generateBalanceDiff(tangle, snapshotProvider, visitedHashes, tip,
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
    public Map<Hash, Long> generateBalanceDiff(Tangle tangle, SnapshotProvider snapshotProvider,
            Set<Hash> visitedTransactions, Hash milestoneHash, int milestoneIndex) throws LedgerException {
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
     * This method generates the {@link com.iota.iri.model.StateDiff} that belongs to the given milestone in the
     * database and marks all transactions that have been approved by the milestone accordingly by setting their
     * {@code snapshotIndex} value.<br />
     * <br />
     * It first checks if the {@code snapshotIndex} of the transaction belonging to the milestone was correctly set
     * already (to determine if this milestone was processed already) and proceeds to generate the {@link
     * com.iota.iri.model.StateDiff} if that is not the case. To do so, it calculates the balance changes, checks if
     * they are consistent and only then writes them to the database.<br />
     * <br />
     * If inconsistencies in the {@code snapshotIndex} are found it issues a reset of the corresponding milestone to
     * recover from this problem.<br />
     *
     * @param tangle Tangle object which acts as a database interface [dependency]
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots [dependency]
     * @param messageQ ZeroMQ interface that allows us to emit messages for external recipients [dependency]
     * @param milestone the milestone that shall have its {@link com.iota.iri.model.StateDiff} generated
     * @return {@code true} if the {@link com.iota.iri.model.StateDiff} could be generated and {@code false} otherwise
     * @throws LedgerException if anything unexpected happens while generating the {@link com.iota.iri.model.StateDiff}
     */
    private boolean generateStateDiff(Tangle tangle, SnapshotProvider snapshotProvider, MessageQ messageQ,
            MilestoneViewModel milestone) throws LedgerException {

        try {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, milestone.getHash());

            if (!transactionViewModel.isSolid()) {
                return false;
            }

            final int transactionSnapshotIndex = transactionViewModel.snapshotIndex();
            boolean successfullyProcessed = transactionSnapshotIndex == milestone.index();
            if (!successfullyProcessed) {
                // if the snapshotIndex of our transaction was set already, we have processed our milestones in
                // the wrong order (i.e. while rescanning the db)
                if (transactionSnapshotIndex != 0) {
                    milestoneService.resetCorruptedMilestone(tangle, snapshotProvider, messageQ, milestone.index());
                }

                snapshotProvider.getLatestSnapshot().lockRead();
                try {
                    Hash tail = transactionViewModel.getHash();
                    Map<Hash, Long> balanceChanges = generateBalanceDiff(tangle, snapshotProvider, new HashSet<>(),
                            tail, snapshotProvider.getLatestSnapshot().getIndex());
                    successfullyProcessed = balanceChanges != null;
                    if (successfullyProcessed) {
                        successfullyProcessed = snapshotProvider.getLatestSnapshot().patchedState(
                                new SnapshotStateDiffImpl(balanceChanges)).isConsistent();
                        if (successfullyProcessed) {
                            milestoneService.updateMilestoneIndexOfMilestoneTransactions(tangle, snapshotProvider,
                                    messageQ, milestone.getHash(), milestone.index());

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
