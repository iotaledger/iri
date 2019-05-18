package com.iota.iri.service;

import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.service.snapshot.SnapshotProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class APITest {

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private TransactionValidator transactionValidator;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Mock
    private IotaConfig config;

    @Test
    public void whenStoreTransactionsStatementThenSetArrivalTimeToCurrentMillis() throws Exception {
        TransactionViewModel transaction = mock(TransactionViewModel.class);
        when(transactionValidator.validateTrits(any(), anyInt())).thenReturn(transaction);
        when(transaction.store(any(), any())).thenReturn(true);

        API api = new API(config, null, null, null,
                null, null,
                snapshotProvider, null, null, null, null,
                transactionValidator, null);

        api.storeTransactionsStatement(Collections.singletonList("FOO"));

        verify(transaction).setArrivalTime(longThat(this::isCloseToCurrentMillis));
    }

    private boolean isCloseToCurrentMillis(Long arrival) {
        long now = System.currentTimeMillis();
        return arrival > now - 1000 && arrival < now;
    }

}