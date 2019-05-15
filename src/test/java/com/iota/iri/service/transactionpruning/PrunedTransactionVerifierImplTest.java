package com.iota.iri.service.transactionpruning;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.transactionpruning.impl.PrunedTransactionProviderImpl;
import com.iota.iri.service.transactionpruning.impl.PrunedTransactionVerifierImpl;

public class PrunedTransactionVerifierImplTest {
    
    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    
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
        when(config.getPrunedTransactionsDbPath()).thenReturn(dbFolder.getRoot().getAbsolutePath());
        when(config.getPrunedTransactionsDbLogPath()).thenReturn(logFolder.getRoot().getAbsolutePath());

        verifier = new PrunedTransactionVerifierImpl(provider, requester);
    }
    
    @After
    public void tearDown() {
        dbFolder.delete();
    }

    @Test
    public void isPossiblyPrunedTest() throws PrunedTransactionException {
        when(provider.containsTransaction(A)).thenReturn(true);
        
        assertTrue("A should be seen as pruned", verifier.isPossiblyPruned(A));
        assertFalse("B should not be seen as pruned", verifier.isPossiblyPruned(B));
    }
    
    @Test
    public void waitingForHashTest() throws PrunedTransactionException {
        assertFalse("A should not be seen as definitely pruned", verifier.isPruned(A));
        assertTrue("Verifier should be waiting for TVM for A", verifier.waitingForHash(A));

        assertFalse("Verifier should not be waitingfor TVM for B", verifier.waitingForHash(B));
    }
    
    @Test
    public void completePruneCheckTest() throws Exception {
        // 10 hashes in the pruner is enough to verify
        // 0 -> 1 -> 3 -> 7
        //        -> 4 -> 8
        //      2 -> 5 -> 9
        //        -> 6
            
        TransactionViewModel[] tvms = new TransactionViewModel[10];
        tvms[9] = TransactionTestUtils.createTransactionFromTrits(TransactionTestUtils.getTransactionTrits());
        tvms[8] = TransactionTestUtils.createTransactionFromTrits(TransactionTestUtils.getTransactionTrits());
        tvms[7] = TransactionTestUtils.createTransactionFromTrits(TransactionTestUtils.getTransactionTrits());
        tvms[6] = TransactionTestUtils.createTransactionFromTrits(TransactionTestUtils.getTransactionTrits());
           
        tvms[5] = c(TransactionTestUtils.getTransactionTritsWithTrunkAndBranch(tvms[9].getHash(), tvms[9].getHash()));
        tvms[4] = c(TransactionTestUtils.getTransactionTritsWithTrunkAndBranch(tvms[8].getHash(), tvms[8].getHash()));
        tvms[3] = c(TransactionTestUtils.getTransactionTritsWithTrunkAndBranch(tvms[7].getHash(), tvms[7].getHash()));
        
        tvms[2] = c(TransactionTestUtils.getTransactionTritsWithTrunkAndBranch(tvms[5].getHash(), tvms[6].getHash()));
        tvms[1] = c(TransactionTestUtils.getTransactionTritsWithTrunkAndBranch(tvms[3].getHash(), tvms[4].getHash()));
        
        tvms[0] = c(TransactionTestUtils.getTransactionTritsWithTrunkAndBranch(tvms[1].getHash(), tvms[2].getHash()));
        
        // Mock network request/response on the transactions
        for (TransactionViewModel tvm : tvms) {
            Mockito.doAnswer(new Answer<Void>() {
                public Void answer(InvocationOnMock invocation) {
                    try {
                        verifier.submitTransaction(tvm);
                    } catch (PrunedTransactionException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }).when(requester).requestTransaction(tvm.getHash(), false);
            
            // Mock as pruned
            when(provider.containsTransaction(tvm.getHash())).thenReturn(true);
        }
        
        // Requests are handled directly (DFS), so it should mark pruned!
        assertTrue("After checking 10 transactions, hash should be marked as pruned", 
                verifier.isPruned(tvms[0].getHash()));
    }
    
    private TransactionViewModel c(byte[] trits) {
        return TransactionTestUtils.createTransactionFromTrits(trits);
    }
}
