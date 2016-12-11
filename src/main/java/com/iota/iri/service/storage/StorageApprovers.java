package com.iota.iri.service.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.model.Transaction;

public class StorageApprovers extends AbstractStorage {

	private static final Logger log = LoggerFactory.getLogger(StorageApprovers.class);

	private static final StorageApprovers instance = new StorageApprovers();

	private static final String APPROVERS_FILE_NAME = "approvers.iri";
	private FileChannel approversChannel;
	private final ByteBuffer[] approversChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
	private volatile long approversNextPointer = SUPER_GROUPS_SIZE;

	@Override
	public void init() throws IOException {
		approversChannel = FileChannel.open(Paths.get(APPROVERS_FILE_NAME), 
				StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
		
		approversChunks[0] = approversChannel.map(FileChannel.MapMode.READ_WRITE, 0, SUPER_GROUPS_SIZE);
		final long approversChannelSize = approversChannel.size();
		while (true) {

			if ((approversNextPointer & (CHUNK_SIZE - 1)) == 0) {
				approversChunks[(int) (approversNextPointer >> 27)] = approversChannel
				        .map(FileChannel.MapMode.READ_WRITE, approversNextPointer, CHUNK_SIZE);
			}
			if (approversChannelSize - approversNextPointer > CHUNK_SIZE) {
				approversNextPointer += CHUNK_SIZE;
			} else {
				approversChunks[(int) (approversNextPointer >> 27)].get(mainBuffer);
				boolean empty = true;
				for (final int value : mainBuffer) {

					if (value != 0) {
						empty = false;
						break;
					}
				}
				if (empty) {
					break;
				}
				approversNextPointer += CELL_SIZE;
			}
		}
	}

	@Override
	public void shutdown() {
        for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && approversChunks[i] != null; i++) {
        	log.info("Flushing approvers chunk #" + i);
            flush(approversChunks[i]);
        }

        try {
            approversChannel.close();
        } catch (final Exception e) {
        	log.error("Shutting down Storage Approvers error: ", e);
        }
	}
	
	public long approveePointer(final byte[] hash) {
		synchronized (Storage.class) {

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.HASH_SIZE; depth++) {

            ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {
                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {
                    return 0;
                }

            } else {

                for (; depth < Transaction.HASH_SIZE; depth++) {
                    if (mainBuffer[Transaction.HASH_OFFSET + depth] != hash[depth]) {
                        return 0;
                    }
                }

                return pointer;
            }
        }
        }
        throw new IllegalStateException("Corrupted storage");
    }

    public List<Long> approveeTransactions(final long pointer) {

    	synchronized (Storage.class) {
        final List<Long> approveeTransactions = new LinkedList<>();

        if (pointer != 0) {

            ((ByteBuffer) approversChunks[(int) (pointer >> 27)].position((int) (pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            int offset = ZEROTH_POINTER_OFFSET - Long.BYTES;
            while (true) {

                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES) {

                    final long transactionPointer = value(mainBuffer, offset);
                    if (transactionPointer == 0) {
                        break;
                    } else {
                        approveeTransactions.add(transactionPointer);
                    }
                }
                if (offset == CELL_SIZE - Long.BYTES) {

                    final long nextCellPointer = value(mainBuffer, offset);
                    if (nextCellPointer == 0) {
                        break;
                    } else {
                        ((ByteBuffer) approversChunks[(int) (nextCellPointer >> 27)].position((int) (nextCellPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        offset = -Long.BYTES;
                    }
                } else {
                    break;
                }
            }
        }

        return approveeTransactions;
    	}
    }

    private void appendToApprovers() {

        ((ByteBuffer)approversChunks[(int)(approversNextPointer >> 27)].position((int)(approversNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((approversNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {
                approversChunks[(int)(approversNextPointer >> 27)] = approversChannel.map(FileChannel.MapMode.READ_WRITE, approversNextPointer, CHUNK_SIZE);
            } catch (final IOException e) {
            	log.error("Caught exception on appendToApprovers:", e);
            }
        }
    }
    
    public void updateApprover(final byte[] hash, final long transactionPointer) {

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11, prevPointer = 0;
        for (int depth = 2; depth < Transaction.HASH_SIZE; depth++) {

            ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                prevPointer = pointer;
                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {

                    setValue(mainBuffer, (hash[depth] + 128) << 3, approversNextPointer);
                    ((ByteBuffer)approversChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                    System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                    mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                    System.arraycopy(hash, 0, mainBuffer, 8, Transaction.HASH_SIZE);
                    setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                    appendToApprovers();

                    return;
                }

            } else {

                for (int i = depth; i < Transaction.HASH_SIZE; i++) {

                    if (mainBuffer[Transaction.HASH_OFFSET + i] != hash[i]) {

                        final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + i];

                        ((ByteBuffer)approversChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        setValue(mainBuffer, (hash[depth - 1] + 128) << 3, approversNextPointer);
                        ((ByteBuffer)approversChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                        for (int j = depth; j < i; j++) {

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, (hash[j] + 128) << 3, approversNextPointer + CELL_SIZE);
                            appendToApprovers();
                        }

                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                        setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                        setValue(mainBuffer, (hash[i] + 128) << 3, approversNextPointer + CELL_SIZE);
                        appendToApprovers();

                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                        mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                        System.arraycopy(hash, 0, mainBuffer, 8, Transaction.HASH_SIZE);
                        setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                        appendToApprovers();

                        return;
                    }
                }

                int offset = ZEROTH_POINTER_OFFSET;
                while (true) {
                    while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES && value(mainBuffer, offset) != 0) {
                        // Do nothing
                    }
                    if (offset == CELL_SIZE - Long.BYTES) {

                        final long nextCellPointer = value(mainBuffer, offset);
                        if (nextCellPointer == 0) {

                            setValue(mainBuffer, offset, approversNextPointer);
                            ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, 0, transactionPointer);
                            appendToApprovers();

                            return;
                        } else {
                            pointer = nextCellPointer;
                            ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                            offset = -Long.BYTES;
                        }
                    } else {
                        setValue(mainBuffer, offset, transactionPointer);
                        ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
                        return;
                    }
                }
            }
        }
    }

	public static StorageApprovers instance() {
		return instance;
	}
}
