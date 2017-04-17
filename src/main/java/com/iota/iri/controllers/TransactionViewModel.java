package com.iota.iri.controllers;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.iota.iri.conf.Configuration;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Approvee;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionViewModel {
    private static ExecutorService executorService = Executors.newCachedThreadPool();

    private final com.iota.iri.model.Transaction transaction;
    private static final Logger log = LoggerFactory.getLogger(TransactionViewModel.class);

    public static final int SIZE = 1604;
    private static final int TAG_SIZE = 17;
    //public static final int HASH_SIZE = 46;

    /*
    public static final int TYPE_OFFSET = 0, TYPE_SIZE = Byte.BYTES;
    public static final int HASH_OFFSET = TYPE_OFFSET + TYPE_SIZE + ((Long.BYTES - (TYPE_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), HASH_SIZE = 46;

    public static final int BYTES_OFFSET = HASH_OFFSET + HASH_SIZE + ((Long.BYTES - (HASH_SIZE & (Long.BYTES - 1))) & (Long.BYTES - 1)), BYTES_SIZE = SIZE;

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
    */

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

    public static final byte[] NULL_TRANSACTION_HASH_BYTES = new byte[Hash.SIZE_IN_BYTES];
    public static final byte[] NULL_TRANSACTION_BYTES = new byte[SIZE];

    public final static int GROUP = 0; // transactions GROUP means that's it's a non-leaf node (leafs store transaction value)
    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    private AddressViewModel address;
    private BundleViewModel bundleViewModel;
    private TransactionViewModel trunk;
    private TransactionViewModel branch;


    private int[] hashTrits;

    private static final int TESTNET_MIN_WEIGHT_MAGNITUDE = 9;
    private static final int MAINNET_MIN_WEIGHT_MAGNITUDE = 18;
    private static int MIN_WEIGHT_MAGNITUDE = MAINNET_MIN_WEIGHT_MAGNITUDE;
    public static void init(boolean testnet) {
        if(testnet) {
            MIN_WEIGHT_MAGNITUDE = TESTNET_MIN_WEIGHT_MAGNITUDE;
        } else {
            MIN_WEIGHT_MAGNITUDE = MAINNET_MIN_WEIGHT_MAGNITUDE;
        }
    }

    private int[] trits;
    public int weightMagnitude;

    public static TransactionViewModel find(byte[] hash) throws Exception {
        Object txObject = Tangle.instance().find(Transaction.class, hash).get();
        return new TransactionViewModel(txObject instanceof Transaction ? (Transaction) txObject : null);
    }

    public static TransactionViewModel quietFromHash(final Hash hash) {
        try {
            return fromHash(hash);
        } catch (Exception e) {
            return new TransactionViewModel(new Transaction());
        }
    }
    public static TransactionViewModel fromHash(final Hash hash) throws Exception {
        Transaction transaction = new Transaction(hash);
        Tangle.instance().load(transaction).get();
        return new TransactionViewModel(transaction);
    }

    public static boolean mightExist(Hash hash) throws ExecutionException, InterruptedException {
        return Tangle.instance().maybeHas(new Transaction(hash)).get();
    }

    public TransactionViewModel(final Transaction transaction) {
        if(transaction == null) {
            this.transaction = new Transaction();
        } else {
            this.transaction = transaction;
        }
    }

    public TransactionViewModel(final int[] trits) {
        transaction = new com.iota.iri.model.Transaction();

        this.trits = trits;
        this.transaction.bytes = Converter.bytes(trits);

        transaction.type = TransactionViewModel.FILLED_SLOT;

        transaction.validity = 0;
        transaction.arrivalTime = 0;
    }


    public TransactionViewModel(final byte[] bytes, final int[] trits, final Curl curl) {
        transaction = new Transaction();
        transaction.bytes = new byte[SIZE];
        System.arraycopy(bytes, 0, transaction.bytes, 0, SIZE);

        for (int i = VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE; i < VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE; i++) {

            if (trits[i] != 0) {
                throw new RuntimeException("Invalid transaction value");
            }
        }

        getHashTrits(curl);
        getHash();
        // For testnet, reduced minWeight from 13 to 9
        // if (this.transaction.hash.bytes()[Hash.SIZE_IN_BYTES - 2] != 0 || this.transaction.hash.bytes()[Hash.SIZE_IN_BYTES - 1] != 0) {
        /*
        if (this.transaction.hash.bytes()[Hash.SIZE_IN_BYTES - 3] != 0 || this.transaction.hash.bytes()[Hash.SIZE_IN_BYTES - 2] != 0 || this.transaction.hash.bytes()[Hash.SIZE_IN_BYTES - 1] != 0) {
            log.error("Invalid transaction hash. Hash found: " + new Hash(trits).toString());
            throw new RuntimeException("Invalid transaction hash");
        }
        */
        weightMagnitude = 0;
        while(weightMagnitude++ < MIN_WEIGHT_MAGNITUDE) {
            if(hashTrits[Curl.HASH_LENGTH - weightMagnitude] != 0) {
                log.error("Invalid transaction hash. Hash found: " + new Hash(trits).toString());
                throw new RuntimeException("Invalid transaction hash");
            }
        }

        weightMagnitude = MIN_WEIGHT_MAGNITUDE;
        while (weightMagnitude < Curl.HASH_LENGTH && hashTrits[Curl.HASH_LENGTH - weightMagnitude - 1] == 0) {
            weightMagnitude++;
        }


        transaction.type = FILLED_SLOT;
        transaction.validity = 0;
        transaction.arrivalTime = 0;
    }

    public static int getNumberOfStoredTransactions() throws ExecutionException, InterruptedException {
        return Tangle.instance().getCount(Transaction.class).get().intValue();
    }

    public void update(String item) throws Exception {
        Tangle.instance().update(transaction, item).get();
    }

    public TransactionViewModel getBranchTransaction() throws Exception {
        if(branch == null) {
            branch = TransactionViewModel.fromHash(getBranchTransactionHash());
        }
        return branch;
    }

    public TransactionViewModel getTrunkTransaction() throws Exception {
        if(trunk == null) {
            trunk = TransactionViewModel.fromHash(getTrunkTransactionHash());
        }
        return trunk;
    }

    public synchronized int[] trits() {
        if (trits == null) {
            trits = new int[TRINARY_SIZE];
            if(transaction.bytes != null) {
                Converter.getTrits(getBytes(), trits);
            }
        }
        return trits;
    }

    public void delete() throws Exception {
        Tangle.instance().delete(transaction).get();
    }
    public boolean store() throws Exception {
        MissingTipTransactions.instance().clearTransactionRequest(getHash());
        if(!Tangle.instance().exists(Transaction.class, getHash()).get()) {
            //log.info("Tx To save Hash: " + getHash());
            getBytes();
            getAddressHash();
            getBundleHash();
            getBranchTransactionHash();
            getTrunkTransactionHash();
            getTagValue();
            if(MissingMilestones.instance().clearTransactionRequest(getHash())) {
                MissingMilestones.instance().requestTransaction(getBranchTransactionHash());
                MissingMilestones.instance().requestTransaction(getTrunkTransactionHash());
            } else {
                MissingTipTransactions.instance().requestTransaction(getBranchTransactionHash());
                MissingTipTransactions.instance().requestTransaction(getTrunkTransactionHash());
            }
            return Tangle.instance().save(transaction).get();
        }
        return false;
    }


    public Hash[] getApprovers() throws ExecutionException, InterruptedException {
        Approvee self = new Approvee();
        self.hash = transaction.hash;
        Tangle.instance().load(self).get();
        return self.transactions != null? self.transactions : new Hash[0];
    }

    public final int getType() {
        return transaction.type;
    }

    public void setArrivalTime(long time) {
        transaction.arrivalTime = time;
    }

    public long getArrivalTime() {
        return transaction.arrivalTime;
    }

    public byte[] getBytes() {
        if(transaction.bytes == null || transaction.bytes.length != SIZE) {
            transaction.bytes = trits == null? new byte[SIZE]: Converter.bytes(trits());
        }
        return transaction.bytes;
    }

    public Hash getHash() {
        if(transaction.hash == null) {
            transaction.hash = new Hash(Converter.bytes(getHashTrits(null)));
        }
        return transaction.hash;
    }

    public AddressViewModel getAddress() {
        if(address == null)
            address = new AddressViewModel(getAddressHash());
        return address;
    }

    public TagViewModel getTag() {
        return new TagViewModel(getTagValue());
    }

    public BundleViewModel quietGetBundle() {
        if(bundleViewModel == null) {
            bundleViewModel = BundleViewModel.quietFromHash(getBundleHash());
        }
        return bundleViewModel;
    }
    public BundleViewModel getBundle() throws Exception {
        if(bundleViewModel == null) {
            bundleViewModel = BundleViewModel.fromHash(getBundleHash());
        }
        return bundleViewModel;
    }

    public Hash getAddressHash() {
        if(transaction.address.hash == null) {
            transaction.address.hash = new Hash(Converter.bytes(trits(), ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE));
        }
        return transaction.address.hash;
    }

    public Hash getTagValue() {
        if(transaction.tag.value == null) {
            transaction.tag.value = new Hash(Converter.bytes(trits(), TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, TAG_SIZE);
        }
        return transaction.tag.value;
    }

    public Hash getBundleHash() {
        if(transaction.bundle.hash == null) {
            transaction.bundle.hash = new Hash(Converter.bytes(trits(), BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE));
        }
        return transaction.bundle.hash;
    }

    public Hash getTrunkTransactionHash() {
        if(transaction.trunk.hash == null) {
            transaction.trunk.hash = new Hash(Converter.bytes(trits(), TRUNK_TRANSACTION_TRINARY_OFFSET, TRUNK_TRANSACTION_TRINARY_SIZE));
        }
        return transaction.trunk.hash;
    }

    public Hash getBranchTransactionHash() {
        if(transaction.branch.hash == null) {
            transaction.branch.hash = new Hash(Converter.bytes(trits(), BRANCH_TRANSACTION_TRINARY_OFFSET, BRANCH_TRANSACTION_TRINARY_SIZE));
        }
        return transaction.branch.hash;
    }

    public long value() {
        return Converter.longValue(trits(), VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE);
    }

    public void setValidity(int validity, boolean update) throws Exception {
        transaction.validity = validity;
        if(update) {
            update("validity");
        }
    }

    public int getValidity() {
        return transaction.validity;
    }

    public long getCurrentIndex() {
        return Converter.longValue(trits(), CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
    }

    public byte[] getSignature() {
        return Converter.bytes(trits(), SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
    }

    public long getTimestamp() {
        return Converter.longValue(trits(), TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_SIZE);
    }

    public byte[] getNonce() {
        return Converter.bytes(trits(), NONCE_TRINARY_OFFSET, NONCE_TRINARY_SIZE);
    }

    public long getLastIndex() {
        return Converter.longValue(trits(), LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE);
    }

    public static boolean exists(Hash hash) throws ExecutionException, InterruptedException {
        return Tangle.instance().exists(Transaction.class, hash).get();
    }

    public int[] getHashTrits(Curl curl) {
        if(hashTrits == null) {
            if(curl == null) {
                curl = new Curl();
            } else {
                curl.reset();
            }
            curl.absorb(trits(), 0, TRINARY_SIZE);
            hashTrits = new int[Curl.HASH_LENGTH];
            curl.squeeze(hashTrits, 0, hashTrits.length);
        }
        return hashTrits;
    }


    public static void updateSolidTransactions(Set<Hash> analyzedHashes) throws Exception {
        Iterator<Hash> hashIterator = analyzedHashes.iterator();
        TransactionViewModel transactionViewModel;
        while(hashIterator.hasNext()) {
            transactionViewModel = TransactionViewModel.fromHash(hashIterator.next());
            transactionViewModel.setSolid();
        }
    }

    public void setSolid() throws Exception {
        if(transaction.solid[0] != 1) {
            transaction.solid = new byte[]{1};
            update("solid");
        }
    }

    public boolean isSolid() {
        return transaction.solid[0] == 1;
    }

    public boolean hasSnapshot() {
        return transaction.snapshot;
    }

    public void markSnapshot(boolean marker) throws Exception {
        if(marker ^ transaction.snapshot) {
            transaction.snapshot = marker;
            update("markedSnapshot");
        }
    }

    public static Hash[] getMissingTransactions() throws ExecutionException, InterruptedException {
        return Arrays.stream(Tangle.instance()
                .keysWithMissingReferences(Transaction.class).get()).toArray(Hash[]::new);
    }


    public static void dump(final byte[] mainBuffer, final byte[] hash, final TransactionViewModel transaction) {
        /*
        System.arraycopy(new byte[AbstractStorage.CELL_SIZE], 0, mainBuffer, 0, AbstractStorage.CELL_SIZE);
        System.arraycopy(hash, 0, mainBuffer, HASH_OFFSET, HASH_SIZE);

        if (transaction == null) {
            mainBuffer[TYPE_OFFSET] = Storage.PREFILLED_SLOT;
        } else {
            mainBuffer[TYPE_OFFSET] = (byte)transaction.type;

            System.arraycopy(transaction.bytes, 0, mainBuffer, BYTES_OFFSET, BYTES_SIZE);
            System.arraycopy(transaction.address, 0, mainBuffer, ADDRESS_OFFSET, ADDRESS_SIZE);
            Storage.setValue(mainBuffer, VALUE_OFFSET, transaction.value);
            final int[] trits = transaction.trits();
            System.arraycopy(Converter.bytes(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, mainBuffer, TAG_OFFSET, TAG_SIZE);
            Storage.setValue(mainBuffer, CURRENT_INDEX_OFFSET, transaction.currentIndex);
            Storage.setValue(mainBuffer, LAST_INDEX_OFFSET, transaction.lastIndex);
            System.arraycopy(Converter.bytes(trits, BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE), 0, mainBuffer, BUNDLE_OFFSET, BUNDLE_SIZE);
            System.arraycopy(transaction.trunkTransaction, 0, mainBuffer, TRUNK_TRANSACTION_OFFSET, TRUNK_TRANSACTION_SIZE);
            System.arraycopy(transaction.branchTransaction, 0, mainBuffer, BRANCH_TRANSACTION_OFFSET, BRANCH_TRANSACTION_SIZE);

            long approvedTransactionPointer = StorageTransactions.instance().transactionPointer(transaction.trunkTransaction);
            if (approvedTransactionPointer == 0) {
                Storage.approvedTransactionsToStore[Storage.numberOfApprovedTransactionsToStore++] = transaction.trunkTransaction;
            } else {

                if (approvedTransactionPointer < 0) {
                    approvedTransactionPointer = -approvedTransactionPointer;
                }
                final long index = (approvedTransactionPointer - (AbstractStorage.CELLS_OFFSET - AbstractStorage.SUPER_GROUPS_OFFSET)) >> 11;
                StorageTransactions.instance().transactionsTipsFlags().put(
                		(int)(index >> 3),
                		(byte)(StorageTransactions.instance().transactionsTipsFlags().get((int)(index >> 3)) & (0xFF ^ (1 << (index & 7)))));
            }
            if (!Arrays.equals(transaction.branchTransaction, transaction.trunkTransaction)) {

                approvedTransactionPointer = StorageTransactions.instance().transactionPointer(transaction.branchTransaction);
                if (approvedTransactionPointer == 0) {
                    Storage.approvedTransactionsToStore[Storage.numberOfApprovedTransactionsToStore++] = transaction.branchTransaction;
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
        */
    }

    public long getHeight() throws Exception {
        long height = getHash() == Hash.NULL_HASH? 1: transaction.height;
        if(height == 0L) {
            TransactionViewModel trunk = getTrunkTransaction();
            if(trunk.getType() != TransactionViewModel.PREFILLED_SLOT) {
                height = trunk.getHeight();
                transaction.height = 1 + trunk.getHeight();
                update("height");
            }
        }
        return height;
    }

    public void updateSender(String sender) throws Exception {
        transaction.sender = sender;
        update("sender");
    }
    public String getSender() {
        return transaction.sender;
    }
}
