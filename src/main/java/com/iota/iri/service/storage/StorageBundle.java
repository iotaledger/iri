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

public class StorageBundle extends AbstractStorage {

	private static final Logger log = LoggerFactory.getLogger(StorageBundle.class);

	private static final StorageBundle instance = new StorageBundle();
	private static final String BUNDLES_FILE_NAME = "bundles.iri";

	private FileChannel bundlesChannel;
	private final ByteBuffer[] bundlesChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
	private volatile long bundlesNextPointer = SUPER_GROUPS_SIZE;

	@Override
	public void init() throws IOException {

		bundlesChannel = FileChannel.open(Paths.get(BUNDLES_FILE_NAME), StandardOpenOption.CREATE,
		        StandardOpenOption.READ, StandardOpenOption.WRITE);
		bundlesChunks[0] = bundlesChannel.map(FileChannel.MapMode.READ_WRITE, 0, SUPER_GROUPS_SIZE);
		final long bundlesChannelSize = bundlesChannel.size();
		while (true) {

			if ((bundlesNextPointer & (CHUNK_SIZE - 1)) == 0) {
				bundlesChunks[(int) (bundlesNextPointer >> 27)] = bundlesChannel.map(FileChannel.MapMode.READ_WRITE,
				        bundlesNextPointer, CHUNK_SIZE);
			}

			if (bundlesChannelSize - bundlesNextPointer > CHUNK_SIZE) {
				bundlesNextPointer += CHUNK_SIZE;

			} else {

				bundlesChunks[(int) (bundlesNextPointer >> 27)].get(mainBuffer);
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
				bundlesNextPointer += CELL_SIZE;
			}
		}

	}

	@Override
	public void shutdown() {
		for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && bundlesChunks[i] != null; i++) {
			log.info("Flushing bundles chunk #" + i);
			flush(bundlesChunks[i]);
		}

		try {
			bundlesChannel.close();
		} catch (IOException e) {
			log.error("Shutting down Storage Bundle error: ", e);
		}

	}
	
	public long bundlePointer(final byte[] hash) {
		synchronized (Storage.class) {
        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.BUNDLE_SIZE; depth++) {

            ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {
                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {
                    return 0;
                }
            } else {
                for (; depth < Transaction.BUNDLE_SIZE; depth++) {
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


    public List<Long> bundleTransactions(final long pointer) {
    	synchronized (Storage.class) {
        final List<Long> bundleTransactions = new LinkedList<>();

        if (pointer != 0) {

            ((ByteBuffer) bundlesChunks[(int) (pointer >> 27)].position((int) (pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            int offset = ZEROTH_POINTER_OFFSET - Long.BYTES;
            while (true) {

                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES) {

                    final long transactionPointer = value(mainBuffer, offset);
                    if (transactionPointer == 0) {
                        break;
                    } else {
                        bundleTransactions.add(transactionPointer);
                    }
                }
                if (offset == CELL_SIZE - Long.BYTES) {

                    final long nextCellPointer = value(mainBuffer, offset);
                    if (nextCellPointer == 0) {
                        break;
                    } else {
                        ((ByteBuffer) bundlesChunks[(int) (nextCellPointer >> 27)].position((int) (nextCellPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        offset = -Long.BYTES;
                    }
                } else {
                    break;
                }
            }
        }
        return bundleTransactions;
    	}
    }
    
    public void updateBundle(final long transactionPointer, final Transaction transaction) {
		{
            long pointer = ((transaction.bundle[0] + 128) + ((transaction.bundle[1] + 128) << 8)) << 11, prevPointer = 0;
            for (int depth = 2; depth < Transaction.BUNDLE_SIZE; depth++) {

                ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

                if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                    prevPointer = pointer;
                    if ((pointer = value(mainBuffer, (transaction.bundle[depth] + 128) << 3)) == 0) {

                        setValue(mainBuffer, (transaction.bundle[depth] + 128) << 3, bundlesNextPointer);
                        ((ByteBuffer)bundlesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                        emptyMainBuffer();
                        mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                        System.arraycopy(transaction.bundle, 0, mainBuffer, 8, Transaction.BUNDLE_SIZE);
                        setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                        appendToBundles();
                        break;
                    }

                } else {

                    boolean sameBundle = true;

                    for (int i = depth; i < Transaction.BUNDLE_SIZE; i++) {

                        if (mainBuffer[Transaction.HASH_OFFSET + i] != transaction.bundle[i]) {

                            final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + i];

                            ((ByteBuffer)bundlesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                            setValue(mainBuffer, (transaction.bundle[depth - 1] + 128) << 3, bundlesNextPointer);
                            ((ByteBuffer)bundlesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                            for (int j = depth; j < i; j++) {
                                emptyMainBuffer();
                                setValue(mainBuffer, (transaction.bundle[j] + 128) << 3, bundlesNextPointer + CELL_SIZE);
                                appendToBundles();
                            }

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                            setValue(mainBuffer, (transaction.bundle[i] + 128) << 3, bundlesNextPointer + CELL_SIZE);
                            appendToBundles();

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                            System.arraycopy(transaction.bundle, 0, mainBuffer, 8, Transaction.BUNDLE_SIZE);
                            setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                            appendToBundles();

                            sameBundle = false;

                            break;
                        }
                    }

                    if (sameBundle) {

                        int offset = ZEROTH_POINTER_OFFSET;
                        while (true) {

                            while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES && value(mainBuffer, offset) != 0) {

                                // Do nothing
                            }
                            if (offset == CELL_SIZE - Long.BYTES) {

                                final long nextCellPointer = value(mainBuffer, offset);
                                if (nextCellPointer == 0) {

                                    setValue(mainBuffer, offset, bundlesNextPointer);
                                    ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                    System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                    setValue(mainBuffer, 0, transactionPointer);
                                    appendToBundles();

                                    break;

                                } else {
                                    pointer = nextCellPointer;
                                    ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                                    offset = -Long.BYTES;
                                }

                            } else {
                                setValue(mainBuffer, offset, transactionPointer);
                                ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
                                break;
                            }
                        }
                    }

                    break;
                }
            }
        }
	}

	private void appendToBundles() {

        ((ByteBuffer)bundlesChunks[(int)(bundlesNextPointer >> 27)].position((int)(bundlesNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((bundlesNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {
                bundlesChunks[(int)(bundlesNextPointer >> 27)] = bundlesChannel.map(FileChannel.MapMode.READ_WRITE, bundlesNextPointer, CHUNK_SIZE);
            } catch (final IOException e) {
            	log.error("Caught exception on appendToBundles:", e);
            }
        }
    }

	public static StorageBundle instance() {
		return instance;
	}
	
	private StorageBundle() {}
}

