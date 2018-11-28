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

/**
 * Contains utilities that help to mock the retrieval of database entries from the tangle.<br />
 * <br />
 * Mocking the tangle allows us to write unit tests that perform much faster than spinning up a new database for every
 * test.<br />
 */
public class TangleMockUtils {
    //region [mockMilestone] ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Registers a {@link Milestone} in the mocked tangle that can consequently be accessed by the tested classes.<br />
     * <br />
     * It first creates the {@link Milestone} with the given details and then mocks the retrieval methods of the tangle
     * to return this object. In addition to mocking the specific retrieval method for the given hash, we also mock the
     * retrieval method for the "latest" entity so the mocked tangle returns the elements in the order that they were
     * mocked / created (which allows the mocked tangle to behave just like a normal one).<br />
     * <br />
     * Note: We return the mocked object which allows us to set additional fields or modify it after "injecting" it into
     *       the mocked tangle.<br />
     *
     * @param tangle mocked tangle object that shall retrieve a milestone object when being queried for it
     * @param hash transaction hash of the milestone
     * @param index milestone index of the milestone
     * @return the Milestone object that be returned by the mocked tangle upon request
     */
    public static Milestone mockMilestone(Tangle tangle, Hash hash, int index) {
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

        return milestone;
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

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
