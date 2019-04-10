package com.iota.iri.network;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.iota.iri.conf.NodeConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NodeTest {
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
        nodeConfig = Mockito.mock(NodeConfig.class);
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
        assertThat("Invalid log message", loggingEvent.getFormattedMessage(),is("Ignoring DNS Refresher Thread... DNS_RESOLUTION_ENABLED is false"));
    }
}