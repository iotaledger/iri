package com.iota.iri.conf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZMQConfigTest {

    @Test
    public void isZmqEnabledLegacy() {
        String[] args = {
                "--zmq-enabled", "true",
        };
        IotaConfig config = ConfigFactory.createIotaConfig(false);
        config.parseConfigFromArgs(args);
        assertTrue("ZMQ must be globally enabled", config.isZmqEnabled());
        assertTrue("ZMQ TCP must be enabled", config.isZmqEnableTcp());
        assertTrue("ZMQ IPC must be enabled", config.isZmqEnableIpc());
    }

    @Test
    public void isZmqEnabled() {
        String[] args = {
                "--zmq-enable-tcp", "true",
                "--zmq-enable-ipc", "true",
        };
        IotaConfig config = ConfigFactory.createIotaConfig(false);
        config.parseConfigFromArgs(args);
        assertTrue("ZMQ must be globally enabled", config.isZmqEnabled());
        assertTrue("ZMQ TCP must be enabled", config.isZmqEnableTcp());
        assertTrue("ZMQ IPC must be enabled", config.isZmqEnableIpc());
    }

    @Test
    public void isZmqEnableTcp() {
        String[] args = {
                "--zmq-enable-tcp", "true"
        };
        IotaConfig config = ConfigFactory.createIotaConfig(false);
        config.parseConfigFromArgs(args);
        assertEquals("ZMQ port must be the default port", 5556, config.getZmqPort());
        assertTrue("ZMQ TCP must be enabled", config.isZmqEnableTcp());
    }

    @Test
    public void isZmqEnableIpc() {
        String[] args = {
                "--zmq-enable-ipc", "true"
        };
        IotaConfig config = ConfigFactory.createIotaConfig(false);
        config.parseConfigFromArgs(args);
        assertEquals("ZMQ ipc must be the default ipc", "ipc://iri", config.getZmqIpc());
        assertTrue("ZMQ IPC must be enabled", config.isZmqEnableIpc());
    }

    @Test
    public void getZmqPort() {
        String[] args = {
                "--zmq-port", "8899"
        };
        IotaConfig config = ConfigFactory.createIotaConfig(false);
        config.parseConfigFromArgs(args);
        assertTrue("ZMQ TCP must be enabled", config.isZmqEnableTcp());
        assertEquals("ZMQ port must be overridden", 8899, config.getZmqPort());
    }

    @Test
    public void getZmqThreads() {
        String[] args = {
                "--zmq-threads", "5"
        };
        IotaConfig config = ConfigFactory.createIotaConfig(false);
        config.parseConfigFromArgs(args);
        assertEquals("ZMQ threads must be overridden", 5, config.getZmqThreads());
    }

    @Test
    public void getZmqIpc() {
        String[] args = {
                "--zmq-ipc", "ipc://test"
        };
        IotaConfig config = ConfigFactory.createIotaConfig(false);
        config.parseConfigFromArgs(args);
        assertTrue("ZMQ IPC must be enabled", config.isZmqEnableIpc());
        assertEquals("ZMQ ipc must be overridden", "ipc://test", config.getZmqIpc());
    }
}