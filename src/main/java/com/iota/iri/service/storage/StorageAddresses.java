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

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;

public class StorageAddresses extends AbstractStorage {

	private static final Logger log = LoggerFactory.getLogger(StorageAddresses.class);

	private static final StorageAddresses instance = new StorageAddresses();
	private static final String ADDRESSES_FILE_NAME = "addresses.iri";

	private FileChannel addressesChannel;
	private final ByteBuffer[] addressesChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
	private volatile long addressesNextPointer = SUPER_GROUPS_SIZE;

	@Override
	public void init() throws IOException {
		addressesChannel = FileChannel.open(Paths.get(ADDRESSES_FILE_NAME), StandardOpenOption.CREATE,
		        StandardOpenOption.READ, StandardOpenOption.WRITE);
		addressesChunks[0] = addressesChannel.map(FileChannel.MapMode.READ_WRITE, 0, SUPER_GROUPS_SIZE);
		final long addressesChannelSize = addressesChannel.size();
		while (true) {

			if ((addressesNextPointer & (CHUNK_SIZE - 1)) == 0) {
				addressesChunks[(int) (addressesNextPointer >> 27)] = addressesChannel
				        .map(FileChannel.MapMode.READ_WRITE, addressesNextPointer, CHUNK_SIZE);
			}

			if (addressesChannelSize - addressesNextPointer > CHUNK_SIZE) {
				addressesNextPointer += CHUNK_SIZE;
			} else {

				addressesChunks[(int) (addressesNextPointer >> 27)].get(mainBuffer);
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
				addressesNextPointer += CELL_SIZE;
			}
		}
	}

	@Override
	public void shutdown() {
		for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && addressesChunks[i] != null; i++) {
			log.info("Flushing addresses chunk #" + i);
			flush(addressesChunks[i]);
		}
		try {
			addressesChannel.close();
		} catch (IOException e) {
			log.error("Shutting down Storage Addresses error: ", e);
		}
	}

	public long addressPointer(final byte[] hash) {
        synchronized (Storage.class) {
        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.ADDRESS_SIZE; depth++) {

            ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {
                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {
                    return 0;
                }
            } else {

                for (; depth < Transaction.ADDRESS_SIZE; depth++) {
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
	
	public List<Long> addressTransactions(final long pointer) {

        synchronized (Storage.class) {
        final List<Long> addressTransactions = new LinkedList<>();

        if (pointer != 0) {

            ((ByteBuffer) addressesChunks[(int) (pointer >> 27)].position((int) (pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            int offset = ZEROTH_POINTER_OFFSET - Long.BYTES;
            while (true) {

                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES) {

                    final long transactionPointer = value(mainBuffer, offset);
                    if (transactionPointer == 0) {
                        break;
                    } else {
                        addressTransactions.add(transactionPointer);
                    }
                }
                if (offset == CELL_SIZE - Long.BYTES) {

                    final long nextCellPointer = value(mainBuffer, offset);
                    if (nextCellPointer == 0) {
                        break;
                    } else {
                        ((ByteBuffer) addressesChunks[(int) (nextCellPointer >> 27)].position((int) (nextCellPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        offset = -Long.BYTES;
                    }
                } else {
                    break;
                }
            }
        }
        
        return addressTransactions;
        }
    }
	
	public void updateAddresses(final long transactionPointer, final Transaction transaction) {
		{
            long pointer = ((transaction.address[0] + 128) + ((transaction.address[1] + 128) << 8)) << 11, prevPointer = 0;
            for (int depth = 2; depth < Transaction.ADDRESS_SIZE; depth++) {

                ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

                if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                    prevPointer = pointer;
                    if ((pointer = value(mainBuffer, (transaction.address[depth] + 128) << 3)) == 0) {

                        setValue(mainBuffer, (transaction.address[depth] + 128) << 3, addressesNextPointer);
                        ((ByteBuffer)addressesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                        mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                        System.arraycopy(transaction.address, 0, mainBuffer, 8, Transaction.ADDRESS_SIZE);
                        setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                        appendToAddresses();

                        break;
                    }

                } else {

                    boolean sameAddress = true;

                    for (int i = depth; i < Transaction.ADDRESS_SIZE; i++) {

                        if (mainBuffer[Transaction.HASH_OFFSET + i] != transaction.address[i]) {

                            final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + i];

                            ((ByteBuffer)addressesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                            setValue(mainBuffer, (transaction.address[depth - 1] + 128) << 3, addressesNextPointer);
                            ((ByteBuffer)addressesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                            for (int j = depth; j < i; j++) {

                                System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                setValue(mainBuffer, (transaction.address[j] + 128) << 3, addressesNextPointer + CELL_SIZE);
                                appendToAddresses();
                            }

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                            setValue(mainBuffer, (transaction.address[i] + 128) << 3, addressesNextPointer + CELL_SIZE);
                            appendToAddresses();

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                            System.arraycopy(transaction.address, 0, mainBuffer, 8, Transaction.ADDRESS_SIZE);
                            setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                            appendToAddresses();

                            sameAddress = false;

                            break;
                        }
                    }

                    if (sameAddress) {

                        int offset = ZEROTH_POINTER_OFFSET;
                        while (true) {

                            while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES && value(mainBuffer, offset) != 0) {

                                // Do nothing
                            }
                            if (offset == CELL_SIZE - Long.BYTES) {

                                final long nextCellPointer = value(mainBuffer, offset);
                                if (nextCellPointer == 0) {

                                    setValue(mainBuffer, offset, addressesNextPointer);
                                    ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                    System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                    setValue(mainBuffer, 0, transactionPointer);
                                    appendToAddresses();
                                    break;

                                } else {
                                    pointer = nextCellPointer;
                                    ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                                    offset = -Long.BYTES;
                                }
                            } else {
                                setValue(mainBuffer, offset, transactionPointer);
                                ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
	}
	
    private void appendToAddresses() {

        ((ByteBuffer)addressesChunks[(int)(addressesNextPointer >> 27)].position((int)(addressesNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((addressesNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {
                addressesChunks[(int)(addressesNextPointer >> 27)] = addressesChannel.map(FileChannel.MapMode.READ_WRITE, addressesNextPointer, CHUNK_SIZE);
            } catch (final IOException e) {
            	log.error("Caught exception on appendToAddresses:", e);
            }
        }
    }

	
	public static StorageAddresses instance() {
		return instance;
	}

	public List<Long> addressesOf(final Hash hash) {
		return addressTransactions(addressPointer(hash.bytes()));
	}
}
