package com.iota.iri.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;

/**
 * Used as a mock for network related tests.
 */
public class FakeChannel extends SelectableChannel implements ByteChannel {

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return 0;
    }

    @Override
    public SelectorProvider provider() {
        return null;
    }

    @Override
    public int validOps() {
        return 0;
    }

    @Override
    public boolean isRegistered() {
        return false;
    }

    @Override
    public SelectionKey keyFor(Selector sel) {
        return null;
    }

    @Override
    public SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException {
        return null;
    }

    @Override
    public SelectableChannel configureBlocking(boolean block) throws IOException {
        return null;
    }

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public Object blockingLock() {
        return null;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return 0;
    }

    @Override
    protected void implCloseChannel() throws IOException {
        // do nothing
    }
}
