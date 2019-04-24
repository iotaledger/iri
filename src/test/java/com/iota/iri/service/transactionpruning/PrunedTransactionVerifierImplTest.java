package com.iota.iri.service.transactionpruning;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.transactionpruning.impl.PrunedTransactionProviderImpl;
import com.iota.iri.service.transactionpruning.impl.PrunedTransactionVerifierImpl;

public class PrunedTransactionVerifierImplTest {
    
    private static final Hash A = TransactionTestUtils.getRandomTransactionHash();
    private static final Hash B = TransactionTestUtils.getRandomTransactionHash();
    
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock
    public SnapshotConfig config;
    
    @Rule
    public final TemporaryFolder dbFolder = new TemporaryFolder();
    
    @Rule
    public final TemporaryFolder logFolder = new TemporaryFolder();
    
    @Mock
    PrunedTransactionProviderImpl provider;
    
    @Mock
    TransactionRequester requester;
    
    PrunedTransactionVerifierImpl verifier;
    
    @Before
    public void setUp() throws PrunedTransactionException {
        Mockito.when(config.getPrunedTransactionsDbPath()).thenReturn(dbFolder.getRoot().getAbsolutePath());
        Mockito.when(config.getPrunedTransactionsDbLogPath()).thenReturn(logFolder.getRoot().getAbsolutePath());

        verifier = new PrunedTransactionVerifierImpl(provider, requester);
    }
    
    @After
    public void tearDown() {
        dbFolder.delete();
    }

    @Test
    public void isPossiblyPrunedTest() throws PrunedTransactionException {
        Mockito.when(provider.containsTransaction(A)).thenReturn(true);
        
        assertTrue(verifier.isPossiblyPruned(A));
        assertFalse(verifier.isPossiblyPruned(B));
    }
    
    @Test
    public void waitingForHashTest() throws PrunedTransactionException {
        assertFalse(verifier.isPruned(A));
        assertTrue(verifier.waitingForHash(A));

        assertFalse(verifier.waitingForHash(B));
    }
    
    @Test
    public void completePruneCheckTest() throws PrunedTransactionException {
        // 10 hashes in the pruner is enough to verify
        // 0 -> 1 -> 3 -> 7
        //        -> 4 -> 8
        //      2 -> 5 -> 9
        //        -> 6
            
        /* This makes no sense to do now, will continue after PR #1363
        TransactionViewModel[] tvms = new TransactionViewModel[10];
        tvms[9] = TransactionTestUtils.createTransactionFromTrits(TransactionTestUtils.getRandomTransactionTrits());
        tvms[8] = TransactionTestUtils.createTransactionFromTrits(TransactionTestUtils.getRandomTransactionTrits());
        tvms[7] = TransactionTestUtils.createTransactionFromTrits(TransactionTestUtils.getRandomTransactionTrits());
        tvms[6] = TransactionTestUtils.createTransactionFromTrits(TransactionTestUtils.getRandomTransactionTrits());
           
        tvms[5] = TransactionTestUtils.createTransactionWithTrunkAndBranch(TransactionTestUtils., trunk, branch)
         */
    }
    
}
