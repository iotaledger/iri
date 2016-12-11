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

public class StorageTags extends AbstractStorage {

	private static final Logger log = LoggerFactory.getLogger(StorageTags.class);
	
	private static final StorageTags instance = new StorageTags();
	
	private FileChannel tagsChannel;
	private final ByteBuffer[] tagsChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
	private volatile long tagsNextPointer = SUPER_GROUPS_SIZE;

	private static final String TAGS_FILE_NAME = "tags.iri";

	@Override
	public void init() throws IOException {
		tagsChannel = FileChannel.open(Paths.get(TAGS_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.READ,
		        StandardOpenOption.WRITE);
		tagsChunks[0] = tagsChannel.map(FileChannel.MapMode.READ_WRITE, 0, SUPER_GROUPS_SIZE);
		final long tagsChannelSize = tagsChannel.size();
		while (true) {

			if ((tagsNextPointer & (CHUNK_SIZE - 1)) == 0) {
				tagsChunks[(int) (tagsNextPointer >> 27)] = tagsChannel.map(FileChannel.MapMode.READ_WRITE,
				        tagsNextPointer, CHUNK_SIZE);
			}

			if (tagsChannelSize - tagsNextPointer > CHUNK_SIZE) {
				tagsNextPointer += CHUNK_SIZE;
			} else {
				tagsChunks[(int) (tagsNextPointer >> 27)].get(mainBuffer);
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

				tagsNextPointer += CELL_SIZE;
			}
		}
	}

	@Override
	public void shutdown() {
        for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && tagsChunks[i] != null; i++) {
        	log.info("Flushing tags chunk #" + i);
            flush(tagsChunks[i]);
        }
        try {
            tagsChannel.close();
        } catch (final Exception e) {
        	log.error("Shutting down Storage Tag error: ", e);
        }
	}
	
	public long tagPointer(final byte[] hash) {
		synchronized (Storage.class) {
        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.TAG_SIZE; depth++) {

            ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {
                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {
                    return 0;
                }
            } else {

                for (; depth < Transaction.TAG_SIZE; depth++) {
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
	
	public List<Long> tagTransactions(final long pointer) {

        synchronized (Storage.class) {
        final List<Long> tagTransactions = new LinkedList<>();

        if (pointer != 0) {
            ((ByteBuffer) tagsChunks[(int) (pointer >> 27)].position((int) (pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            int offset = ZEROTH_POINTER_OFFSET - Long.BYTES;
            while (true) {

                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES) {

                    final long transactionPointer = value(mainBuffer, offset);
                    if (transactionPointer == 0) {
                        break;
                    } else {
                        tagTransactions.add(transactionPointer);
                    }
                }
                if (offset == CELL_SIZE - Long.BYTES) {

                    final long nextCellPointer = value(mainBuffer, offset);
                    if (nextCellPointer == 0) {
                        break;
                    } else {
                        ((ByteBuffer) tagsChunks[(int) (nextCellPointer >> 27)].position((int) (nextCellPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        offset = -Long.BYTES;
                    }
                } else {
                    break;
                }
            }
        }
        return tagTransactions;
        }
    }
	
	private void appendToTags() {

        ((ByteBuffer) tagsChunks[(int)(tagsNextPointer >> 27)].position((int)(tagsNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((tagsNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {
                tagsChunks[(int)(tagsNextPointer >> 27)] = tagsChannel.map(FileChannel.MapMode.READ_WRITE, tagsNextPointer, CHUNK_SIZE);
            } catch (final IOException e) {
            	log.error("Caught exception on appendToTags:", e);
            }
        }
    }
	
	public void updateTags(final long transactionPointer, final Transaction transaction) {
		for (int i = 0; i < Transaction.TAG_SIZE; i++) {

            if (transaction.tag[i] != 0) {

                long pointer = ((transaction.tag[0] + 128) + ((transaction.tag[1] + 128) << 8)) << 11, prevPointer = 0;
                for (int depth = 2; depth < Transaction.TAG_SIZE; depth++) {

                    ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

                    if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                        prevPointer = pointer;
                        if ((pointer = value(mainBuffer, (transaction.tag[depth] + 128) << 3)) == 0) {

                            setValue(mainBuffer, (transaction.tag[depth] + 128) << 3, tagsNextPointer);
                            ((ByteBuffer) tagsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                            System.arraycopy(transaction.tag, 0, mainBuffer, 8, Transaction.TAG_SIZE);
                            setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                            appendToTags();

                            break;
                        }

                    } else {

                        boolean sameTag = true;

                        for (int j = depth; j < Transaction.TAG_SIZE; j++) {

                            if (mainBuffer[Transaction.HASH_OFFSET + j] != transaction.tag[j]) {

                                final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + j];

                                ((ByteBuffer) tagsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                                setValue(mainBuffer, (transaction.tag[depth - 1] + 128) << 3, tagsNextPointer);
                                ((ByteBuffer) tagsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                for (int k = depth; k < j; k++) {

                                    System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                    setValue(mainBuffer, (transaction.tag[k] + 128) << 3, tagsNextPointer + CELL_SIZE);
                                    appendToTags();
                                }

                                System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                                setValue(mainBuffer, (transaction.tag[j] + 128) << 3, tagsNextPointer + CELL_SIZE);
                                appendToTags();

                                System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                                System.arraycopy(transaction.tag, 0, mainBuffer, 8, Transaction.TAG_SIZE);
                                setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                                appendToTags();

                                sameTag = false;

                                break;
                            }
                        }

                        if (sameTag) {

                            int offset = ZEROTH_POINTER_OFFSET;
                            while (true) {

                                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES && value(mainBuffer, offset) != 0) {
                                    // Do nothing
                                }
                                if (offset == CELL_SIZE - Long.BYTES) {

                                    final long nextCellPointer = value(mainBuffer, offset);
                                    if (nextCellPointer == 0) {

                                        setValue(mainBuffer, offset, tagsNextPointer);
                                        ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                        setValue(mainBuffer, 0, transactionPointer);
                                        appendToTags();
                                        break;

                                    } else {
                                        pointer = nextCellPointer;
                                        ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                                        offset = -Long.BYTES;
                                    }
                                } else {
                                    setValue(mainBuffer, offset, transactionPointer);
                                    ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
                break;
            }
        }
	}

	public static StorageTags instance() {
		return instance;
	}
}
