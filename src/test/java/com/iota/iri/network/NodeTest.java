package com.iota.iri.network;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.NodeConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.service.snapshot.SnapshotProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class NodeTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private NodeConfig nodeConfig;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;

    private Node classUnderTest;
    private static final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @Before
    public void setUp() {
        // inject our mock appender
        logger.addAppender(mockAppender);

        // set up class under test
        nodeConfig = mock(NodeConfig.class);
        classUnderTest = new Node(null, null, null, null, null, null, nodeConfig);

        // verify config calls in Node constructor
        verify(nodeConfig).getRequestHashSize();
        verify(nodeConfig).getTransactionPacketSize();
    }

    @After
    public void tearDown() {
        logger.detachAppender(mockAppender);
    }

    @Test
    public void spawnNeighborDNSRefresherThreadTest() {
        when(nodeConfig.isDnsResolutionEnabled()).thenReturn(false);
        Runnable runnable = classUnderTest.spawnNeighborDNSRefresherThread();
        runnable.run();
        verify(nodeConfig).isDnsResolutionEnabled();
        verifyNoMoreInteractions(nodeConfig);

        // verify logging
        verify(mockAppender).doAppend(captorLoggingEvent.capture());
        final ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        assertThat("Loglevel must be info", loggingEvent.getLevel(), is(Level.INFO));
        assertThat("Invalid log message", loggingEvent.getFormattedMessage(), is("Ignoring DNS Refresher Thread... DNS_RESOLUTION_ENABLED is false"));
    }

    @Test
    public void whenProcessReceivedDataSetArrivalTimeToCurrentMillis() throws Exception {
        Node node = new Node(null, mock(SnapshotProvider.class), mock(TransactionValidator.class), null, null, null, mock(NodeConfig.class));
        TransactionViewModel transaction = mock(TransactionViewModel.class);
        when(transaction.store(any(), any())).thenReturn(true);
        Neighbor neighbor = mock(Neighbor.class, Answers.RETURNS_SMART_NULLS);

        node.processReceivedData(transaction, neighbor);
        verify(transaction).setArrivalTime(longThat(this::isCloseToCurrentMillis));
    }

    private boolean isCloseToCurrentMillis(Long arrival) {
        long now = System.currentTimeMillis();
        return arrival > now - 1000 && arrival <= now;
    }



}