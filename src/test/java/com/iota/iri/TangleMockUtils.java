package com.iota.iri;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.StateDiff;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class TangleMockUtils {
    //region [mockMilestone] ///////////////////////////////////////////////////////////////////////////////////////////

    public static Milestone mockMilestone(Tangle tangle, Hash hash, int index, boolean applied,
            Map<Hash, Long> balanceDiff) {

        Milestone milestone = new Milestone();
        milestone.hash = hash;
        milestone.index = new IntegerIndex(index);

        try {
            Mockito.when(tangle.load(Milestone.class, new IntegerIndex(index))).thenReturn(milestone);
            Mockito.when(tangle.getLatest(Milestone.class, IntegerIndex.class)).thenReturn(new Pair<>(milestone.index,
                    milestone));
        } catch (Exception e) {
            // the exception can not be raised since we mock
        }

        if (!balanceDiff.isEmpty()) {
            mockStateDiff(tangle, milestone.hash, balanceDiff);
        }

        Transaction milestoneTransaction = mockTransaction(tangle, milestone.hash);
        milestoneTransaction.snapshot = applied ? index : 0;
        milestoneTransaction.milestone = true;

        return milestone;
    }

    public static Milestone mockMilestone(Tangle tangle, Hash hash, int index, boolean applied) {
        return mockMilestone(tangle, hash, index, applied, new HashMap<>());
    }

    public static Milestone mockMilestone(Tangle tangle, Hash hash, int index) {
        return mockMilestone(tangle, hash, index, true, new HashMap<>());
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [mockTransaction] /////////////////////////////////////////////////////////////////////////////////////////

    public static Transaction mockTransaction(Tangle tangle, Hash hash) {
        Transaction transaction = new Transaction();
        transaction.bytes = new byte[0];
        transaction.type = TransactionViewModel.FILLED_SLOT;
        transaction.parsed = true;

        try {
            Mockito.when(tangle.load(Transaction.class, hash)).thenReturn(transaction);
            Mockito.when(tangle.getLatest(Transaction.class, Hash.class)).thenReturn(new Pair<>(hash, transaction));
        } catch (Exception e) {
            // the exception can not be raised since we mock
        }

        return transaction;
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [mockStateDiff] ///////////////////////////////////////////////////////////////////////////////////////////

    public static StateDiff mockStateDiff(Tangle tangle, Hash hash, Map<Hash, Long> balanceDiff) {
        StateDiff stateDiff = new StateDiff();
        stateDiff.state = balanceDiff;

        try {
            Mockito.when(tangle.load(StateDiff.class, hash)).thenReturn(stateDiff);
            Mockito.when(tangle.getLatest(StateDiff.class, Hash.class)).thenReturn(new Pair<>(hash, stateDiff));
        } catch (Exception e) {
            // the exception can not be raised since we mock
        }

        return stateDiff;
    }

    public static StateDiff mockStateDiff(Tangle tangle, Hash hash) {
        return mockStateDiff(tangle, hash, new HashMap<>());
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
