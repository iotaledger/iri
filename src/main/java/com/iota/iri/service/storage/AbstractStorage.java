package com.iota.iri.service.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

public abstract class AbstractStorage {

    public final static int CELL_SIZE = 2048;
	
    public final static int CELLS_PER_CHUNK = 65536;
    public final static int CHUNK_SIZE = CELL_SIZE * CELLS_PER_CHUNK;
    public final static int MAX_NUMBER_OF_CHUNKS = 16384; // Limits the storage capacity to ~1 billion transactions

    public final static int TIPS_FLAGS_OFFSET = 0,
            TIPS_FLAGS_SIZE = MAX_NUMBER_OF_CHUNKS * CELLS_PER_CHUNK / Byte.SIZE;

    public final static int SUPER_GROUPS_OFFSET = TIPS_FLAGS_OFFSET + TIPS_FLAGS_SIZE,
            SUPER_GROUPS_SIZE = (Short.MAX_VALUE - Short.MIN_VALUE + 1) * CELL_SIZE;
	
    public final static int CELLS_OFFSET = SUPER_GROUPS_OFFSET + SUPER_GROUPS_SIZE;

    public final static int TRANSACTIONS_TO_REQUEST_OFFSET = 0, TRANSACTIONS_TO_REQUEST_SIZE = CHUNK_SIZE;
	
    public final static int ANALYZED_TRANSACTIONS_FLAGS_OFFSET = TRANSACTIONS_TO_REQUEST_OFFSET
            + TRANSACTIONS_TO_REQUEST_SIZE,
            ANALYZED_TRANSACTIONS_FLAGS_SIZE = MAX_NUMBER_OF_CHUNKS * CELLS_PER_CHUNK / Byte.SIZE;

    public final static int ANALYZED_TRANSACTIONS_FLAGS_COPY_OFFSET = ANALYZED_TRANSACTIONS_FLAGS_OFFSET
            + ANALYZED_TRANSACTIONS_FLAGS_SIZE,
            ANALYZED_TRANSACTIONS_FLAGS_COPY_SIZE = ANALYZED_TRANSACTIONS_FLAGS_SIZE;

    public final static int GROUP = 0; // transactions GROUP means that's it's a non-leaf node (leafs store transaction bytes)
    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    protected static final int ZEROTH_POINTER_OFFSET = 64;

    protected static final byte[] ZEROED_BUFFER = new byte[CELL_SIZE];
	
    protected static final byte[] mainBuffer = new byte[CELL_SIZE];
    protected static final byte[] auxBuffer = new byte[CELL_SIZE];
    
	public static long value(final byte[] buffer, final int offset) {
        return ((long)(buffer[offset] & 0xFF)) + (((long)(buffer[offset + 1] & 0xFF)) << 8) + (((long)(buffer[offset + 2] & 0xFF)) << 16) + (((long)(buffer[offset + 3] & 0xFF)) << 24) + (((long)(buffer[offset + 4] & 0xFF)) << 32) + (((long)(buffer[offset + 5] & 0xFF)) << 40) + (((long)(buffer[offset + 6] & 0xFF)) << 48) + (((long)(buffer[offset + 7] & 0xFF)) << 56);
    }

    public static void setValue(final byte[] buffer, final int offset, final long value) {

        buffer[offset] = (byte)value;
        buffer[offset + 1] = (byte)(value >> 8);
        buffer[offset + 2] = (byte)(value >> 16);
        buffer[offset + 3] = (byte)(value >> 24);
        buffer[offset + 4] = (byte)(value >> 32);
        buffer[offset + 5] = (byte)(value >> 40);
        buffer[offset + 6] = (byte)(value >> 48);
        buffer[offset + 7] = (byte)(value >> 56);
    }
	
    protected static boolean flush(final ByteBuffer buffer) {

        try {
            ((MappedByteBuffer) buffer).force();
            return true;
            
        } catch (final Exception e) {
            return false;
        }
    }
	
	protected void emptyMainBuffer() {
        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
	}

    public abstract void init() throws IOException;

    public abstract void shutdown();
}
