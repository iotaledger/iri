package com.iota.iri.viewModel;

import java.util.Arrays;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.service.storage.Storage;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.storage.StorageTransactions;
import com.iota.iri.tangle.TangleAccessor;
import com.iota.iri.utils.Converter;

public class Transaction {
    private final com.iota.iri.model.Transaction transaction;

    public static final int SIZE = 1604;

    public static final int TYPE_OFFSET = 0, TYPE_SIZE = Byte.BYTES;
    public static final int HASH_OFFSET = TYPE_OFFSET + TYPE_SIZE + ((Long.BYTES - (TYPE_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), HASH_SIZE = 46;

    private static final int BYTES_OFFSET = HASH_OFFSET + HASH_SIZE + ((Long.BYTES - (HASH_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), BYTES_SIZE = SIZE;

    public static final int ADDRESS_OFFSET = BYTES_OFFSET + BYTES_SIZE + ((Long.BYTES - (BYTES_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), ADDRESS_SIZE = 49;
    public static final int VALUE_OFFSET = ADDRESS_OFFSET + ADDRESS_SIZE + ((Long.BYTES - (ADDRESS_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), VALUE_SIZE = Long.BYTES;
    public static final int TAG_OFFSET = VALUE_OFFSET + VALUE_SIZE + ((Long.BYTES - (VALUE_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), TAG_SIZE = 17;
    private static final int CURRENT_INDEX_OFFSET = TAG_OFFSET + TAG_SIZE + ((Long.BYTES - (TAG_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), CURRENT_INDEX_SIZE = Long.BYTES;
    private static final int LAST_INDEX_OFFSET = CURRENT_INDEX_OFFSET + CURRENT_INDEX_SIZE + ((Long.BYTES - (CURRENT_INDEX_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), LAST_INDEX_SIZE = Long.BYTES;
    public static final int BUNDLE_OFFSET = LAST_INDEX_OFFSET + LAST_INDEX_SIZE + ((Long.BYTES - (LAST_INDEX_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), BUNDLE_SIZE = 49;
    private static final int TRUNK_TRANSACTION_OFFSET = BUNDLE_OFFSET + BUNDLE_SIZE + ((Long.BYTES - (BUNDLE_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), TRUNK_TRANSACTION_SIZE = HASH_SIZE;
    private static final int BRANCH_TRANSACTION_OFFSET = TRUNK_TRANSACTION_OFFSET + TRUNK_TRANSACTION_SIZE + ((Long.BYTES - (TRUNK_TRANSACTION_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), BRANCH_TRANSACTION_SIZE = HASH_SIZE;
    public static final int VALIDITY_OFFSET = BRANCH_TRANSACTION_OFFSET + BRANCH_TRANSACTION_SIZE + ((Long.BYTES - (BRANCH_TRANSACTION_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), VALIDITY_SIZE = 1;
    public static final int ARRIVAL_TIME_OFFSET = VALIDITY_OFFSET + VALIDITY_SIZE + ((Long.BYTES - (VALIDITY_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), ARIVAL_TIME_SIZE = Long.BYTES;

    public static final long SUPPLY = 2779530283277761L; // = (3^33 - 1) / 2

    public static final int SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET = 0, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE = 6561;
    public static final int ADDRESS_TRINARY_OFFSET = SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE, ADDRESS_TRINARY_SIZE = 243;
    public static final int VALUE_TRINARY_OFFSET = ADDRESS_TRINARY_OFFSET + ADDRESS_TRINARY_SIZE, VALUE_TRINARY_SIZE = 81, VALUE_USABLE_TRINARY_SIZE = 33;
    public static final int TAG_TRINARY_OFFSET = VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE, TAG_TRINARY_SIZE = 81;
    public static final int TIMESTAMP_TRINARY_OFFSET = TAG_TRINARY_OFFSET + TAG_TRINARY_SIZE, TIMESTAMP_TRINARY_SIZE = 27;
    public static final int CURRENT_INDEX_TRINARY_OFFSET = TIMESTAMP_TRINARY_OFFSET + TIMESTAMP_TRINARY_SIZE, CURRENT_INDEX_TRINARY_SIZE = 27;
    private static final int LAST_INDEX_TRINARY_OFFSET = CURRENT_INDEX_TRINARY_OFFSET + CURRENT_INDEX_TRINARY_SIZE, LAST_INDEX_TRINARY_SIZE = 27;
    public static final int BUNDLE_TRINARY_OFFSET = LAST_INDEX_TRINARY_OFFSET + LAST_INDEX_TRINARY_SIZE, BUNDLE_TRINARY_SIZE = 243;
    public static final int TRUNK_TRANSACTION_TRINARY_OFFSET = BUNDLE_TRINARY_OFFSET + BUNDLE_TRINARY_SIZE, TRUNK_TRANSACTION_TRINARY_SIZE = 243;
    public static final int BRANCH_TRANSACTION_TRINARY_OFFSET = TRUNK_TRANSACTION_TRINARY_OFFSET + TRUNK_TRANSACTION_TRINARY_SIZE, BRANCH_TRANSACTION_TRINARY_SIZE = 243;
    private static final int NONCE_TRINARY_OFFSET = BRANCH_TRANSACTION_TRINARY_OFFSET + BRANCH_TRANSACTION_TRINARY_SIZE, NONCE_TRINARY_SIZE = 243;

    public static final int TRINARY_SIZE = NONCE_TRINARY_OFFSET + NONCE_TRINARY_SIZE;

    public static final int ESSENCE_TRINARY_OFFSET = ADDRESS_TRINARY_OFFSET, ESSENCE_TRINARY_SIZE = ADDRESS_TRINARY_SIZE + VALUE_TRINARY_SIZE + TAG_TRINARY_SIZE + TIMESTAMP_TRINARY_SIZE + CURRENT_INDEX_TRINARY_SIZE + LAST_INDEX_TRINARY_SIZE;

	public static final byte[] NULL_TRANSACTION_HASH_BYTES = new byte[Transaction.HASH_SIZE];
	
    private static final int MIN_WEIGHT_MAGNITUDE = 13;

    public final int type;
    
    //public final byte[] hash;
    //public final byte[] bytes; // stores entire tx bytes. message occupies always first part named 'signatureMessageFragment'
    //public final byte[] address;
    
    //public final long value; // <0 spending transaction, >=0 deposit transaction / message
    
    public long arrivalTime;

    //public final byte[] tag; // milestone index only for milestone tx. Otherwise, arbitrary up to the tx issuer.
    //public final long currentIndex; // index of tx in the bundle
    //public final long lastIndex; // lastIndex is curIndex of the last tx from the same bundle
    
    //public final byte[] bundle;
    //public final byte[] trunkTransaction;
    //public final byte[] branchTransaction;

    public long trunkTransactionPointer;
    public long branchTransactionPointer;
    //private final int validity;

    private int[] trits;
    public final long pointer;
    public int weightMagnitude;

    public static Transaction fromHash(final byte[] hash) {
        com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
        TangleAccessor.instance().load(transaction, hash);
        return new Transaction(transaction);
    }
    public static Transaction fromHash(final int[] hash) {
        return fromHash(Converter.bytes(hash));
    }

    public Transaction(final com.iota.iri.model.Transaction transaction) {
        this.transaction = transaction;
        type = Storage.FILLED_SLOT;
        pointer = 0;
    }

    public Transaction(final int[] trits) {
        transaction = new com.iota.iri.model.Transaction();

        this.trits = trits;
        transaction.bytes = Converter.bytes(trits);

        final Curl curl = new Curl();
        curl.absorb(trits, 0, TRINARY_SIZE);
        final int[] hashTrits = new int[Curl.HASH_LENGTH];
        curl.squeeze(hashTrits, 0, hashTrits.length);
        transaction.hash = Arrays.copyOf(Converter.bytes(hashTrits), HASH_SIZE);

        populateTransaction(trits);

        type = Storage.FILLED_SLOT;

        trunkTransactionPointer = 0;
        branchTransactionPointer = 0;
        transaction.validity = 0;
        arrivalTime = 0;
        pointer = 0;
    }


    public Transaction(final byte[] bytes, final int[] trits, final Curl curl) {
        transaction = new com.iota.iri.model.Transaction();
        this.transaction.bytes = Arrays.copyOf(bytes, BYTES_SIZE);
        Converter.getTrits(this.getBytes(), this.trits = trits);

        for (int i = VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE; i < VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE; i++) {

            if (trits[i] != 0) {
                throw new RuntimeException("Invalid transaction value");
            }
        }

        curl.reset();
        curl.absorb(trits, 0, TRINARY_SIZE);
        final int[] hashTrits = new int[Curl.HASH_LENGTH];
        curl.squeeze(hashTrits, 0, hashTrits.length);

        this.transaction.hash = Converter.bytes(hashTrits);
        if (this.transaction.hash[Hash.SIZE_IN_BYTES - 3] != 0 || this.transaction.hash[Hash.SIZE_IN_BYTES - 2] != 0 || this.transaction.hash[Hash.SIZE_IN_BYTES - 1] != 0) {
            throw new RuntimeException("Invalid transaction hash");
        }

        weightMagnitude = MIN_WEIGHT_MAGNITUDE;
        while (weightMagnitude < Curl.HASH_LENGTH && hashTrits[Curl.HASH_LENGTH - weightMagnitude - 1] == 0) {
            weightMagnitude++;
        }

        populateTransaction(trits);

        type = Storage.FILLED_SLOT;

        trunkTransactionPointer = 0;
        branchTransactionPointer = 0;
        transaction.validity = 0;
        arrivalTime = 0;

        pointer = 0;
    }

    public Transaction(final byte[] mainBuffer, final long pointer) {
        transaction = new com.iota.iri.model.Transaction();
        type = mainBuffer[TYPE_OFFSET];
        System.arraycopy(mainBuffer, HASH_OFFSET, this.transaction.hash = new byte[HASH_SIZE], 0, HASH_SIZE);

        System.arraycopy(mainBuffer, BYTES_OFFSET, this.transaction.bytes = new byte[BYTES_SIZE], 0, BYTES_SIZE);

        System.arraycopy(mainBuffer, ADDRESS_OFFSET, transaction.address = new byte[ADDRESS_SIZE], 0, ADDRESS_SIZE);
        transaction.value = AbstractStorage.value(mainBuffer, VALUE_OFFSET);
        System.arraycopy(mainBuffer, TAG_OFFSET, transaction.tag = new byte[TAG_SIZE], 0, TAG_SIZE);
        transaction.currentIndex = Storage.value(mainBuffer, CURRENT_INDEX_OFFSET);
        transaction.lastIndex = Storage.value(mainBuffer, LAST_INDEX_OFFSET);
        System.arraycopy(mainBuffer, BUNDLE_OFFSET, transaction.bundle = new byte[BUNDLE_SIZE], 0, BUNDLE_SIZE);
        System.arraycopy(mainBuffer, TRUNK_TRANSACTION_OFFSET, transaction.trunk = new byte[TRUNK_TRANSACTION_SIZE], 0, TRUNK_TRANSACTION_SIZE);
        System.arraycopy(mainBuffer, BRANCH_TRANSACTION_OFFSET, transaction.branch = new byte[BRANCH_TRANSACTION_SIZE], 0, BRANCH_TRANSACTION_SIZE);

        trunkTransactionPointer = StorageTransactions.instance().transactionPointer(transaction.trunk);
        if (trunkTransactionPointer < 0) {
            trunkTransactionPointer = -trunkTransactionPointer;
        }
        branchTransactionPointer = StorageTransactions.instance().transactionPointer(transaction.branch);
        if (branchTransactionPointer < 0) {
            branchTransactionPointer = -branchTransactionPointer;
        }

        transaction.validity = mainBuffer[VALIDITY_OFFSET];
        
        arrivalTime = Storage.value(mainBuffer, ARRIVAL_TIME_OFFSET);

        this.pointer = pointer;
    }

    private final void populateTransaction(int[] trits) {
        transaction.currentIndex = Converter.longValue(trits, CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
        transaction.lastIndex = Converter.longValue(trits, LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE);
        transaction.value = Converter.longValue(trits, VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE);
        transaction.address = Converter.bytes(trits, ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE);
        System.arraycopy(Converter.bytes(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, transaction.tag = new byte[TAG_SIZE], 0, TAG_SIZE);
        System.arraycopy(Converter.bytes(trits, BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE), 0, transaction.bundle = new byte[BUNDLE_SIZE], 0, BUNDLE_SIZE);
        System.arraycopy(Converter.bytes(trits, TRUNK_TRANSACTION_TRINARY_OFFSET, TRUNK_TRANSACTION_TRINARY_SIZE), 0, transaction.trunk = new byte[TRUNK_TRANSACTION_SIZE], 0, TRUNK_TRANSACTION_SIZE);
        System.arraycopy(Converter.bytes(trits, BRANCH_TRANSACTION_TRINARY_OFFSET, BRANCH_TRANSACTION_TRINARY_SIZE), 0, transaction.branch = new byte[BRANCH_TRANSACTION_SIZE], 0, BRANCH_TRANSACTION_SIZE);
    }

    public synchronized int[] trits() {

        if (trits == null) {
            trits = new int[TRINARY_SIZE];
            Converter.getTrits(this.transaction.bytes, trits);
        }
        return trits;
    }

    public static void dump(final byte[] mainBuffer, final byte[] hash, final Transaction transaction) {

        System.arraycopy(new byte[AbstractStorage.CELL_SIZE], 0, mainBuffer, 0, AbstractStorage.CELL_SIZE);
        System.arraycopy(hash, 0, mainBuffer, HASH_OFFSET, HASH_SIZE);

        if (transaction == null) {
            mainBuffer[TYPE_OFFSET] = Storage.PREFILLED_SLOT;
        } else {
            mainBuffer[TYPE_OFFSET] = (byte)transaction.type;
            System.arraycopy(transaction.getBytes(), 0, mainBuffer, BYTES_OFFSET, BYTES_SIZE);
            System.arraycopy(transaction.getAddress(), 0, mainBuffer, ADDRESS_OFFSET, ADDRESS_SIZE);
            Storage.setValue(mainBuffer, VALUE_OFFSET, transaction.value());
            final int[] trits = transaction.trits();
            System.arraycopy(Converter.bytes(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, mainBuffer, TAG_OFFSET, TAG_SIZE);
            Storage.setValue(mainBuffer, CURRENT_INDEX_OFFSET, transaction.getCurrentIndex());
            Storage.setValue(mainBuffer, LAST_INDEX_OFFSET, transaction.getLastIndex());
            System.arraycopy(Converter.bytes(trits, BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE), 0, mainBuffer, BUNDLE_OFFSET, BUNDLE_SIZE);
            System.arraycopy(transaction.getTrunkTransaction(), 0, mainBuffer, TRUNK_TRANSACTION_OFFSET, TRUNK_TRANSACTION_SIZE);
            System.arraycopy(transaction.getBranchTransaction(), 0, mainBuffer, BRANCH_TRANSACTION_OFFSET, BRANCH_TRANSACTION_SIZE);

            long approvedTransactionPointer = StorageTransactions.instance().transactionPointer(transaction.getTrunkTransaction());
            if (approvedTransactionPointer == 0) {
                Storage.approvedTransactionsToStore[Storage.numberOfApprovedTransactionsToStore++] = transaction.getTrunkTransaction();
            } else {

                if (approvedTransactionPointer < 0) {
                    approvedTransactionPointer = -approvedTransactionPointer;
                }
                final long index = (approvedTransactionPointer - (AbstractStorage.CELLS_OFFSET - AbstractStorage.SUPER_GROUPS_OFFSET)) >> 11;
                StorageTransactions.instance().transactionsTipsFlags().put(
                		(int)(index >> 3),
                		(byte)(StorageTransactions.instance().transactionsTipsFlags().get((int)(index >> 3)) & (0xFF ^ (1 << (index & 7)))));
            }
            if (!Arrays.equals(transaction.getBranchTransaction(), transaction.getTrunkTransaction())) {

                approvedTransactionPointer = StorageTransactions.instance().transactionPointer(transaction.getBranchTransaction());
                if (approvedTransactionPointer == 0) {
                    Storage.approvedTransactionsToStore[Storage.numberOfApprovedTransactionsToStore++] = transaction.getBranchTransaction();
                } else {

                    if (approvedTransactionPointer < 0) {
                        approvedTransactionPointer = -approvedTransactionPointer;
                    }
                    final long index = (approvedTransactionPointer - (Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET)) >> 11;
                    StorageTransactions.instance().transactionsTipsFlags().put(
                    		(int) (index >> 3),
                    		(byte) (StorageTransactions.instance().transactionsTipsFlags().get((int) (index >> 3)) & (0xFF ^ (1 << (index & 7)))));
                }
            }
        }
    }

    public byte[] getBytes() {
        return transaction.bytes;
    }

    public byte[] getHash() {
        return transaction.hash;
    }

    public byte[] getAddress() {
        return transaction.address;
    }

    public byte[] getTag() {
        return transaction.tag;
    }

    public byte[] getBundle() {
        return transaction.bundle;
    }

    public byte[] getTrunkTransaction() {
        return transaction.trunk;
    }

    public byte[] getBranchTransaction() {
        return transaction.branch;
    }

    public final long getValue() {
        return transaction.value;
    }

    public long value() {
		return transaction.value;
	}

    public int validity() {
		return transaction.validity;
	}
	public long getCurrentIndex() {
        return transaction.currentIndex;
    }

    public long getLastIndex() {
        return transaction.lastIndex;
    }
}
