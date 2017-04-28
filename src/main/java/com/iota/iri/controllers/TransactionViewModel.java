package com.iota.iri.controllers;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.iota.iri.model.Approvee;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;

public class TransactionViewModel {
    private static ExecutorService executorService = Executors.newCachedThreadPool();

    private final com.iota.iri.model.Transaction transaction;

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

    private AddressViewModel address;
    private BundleViewModel bundleViewModel;
    private TransactionViewModel trunk;
    private TransactionViewModel branch;


    public final static int GROUP = 0; // transactions GROUP means that's it's a non-leaf node (leafs store transaction value)
    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    private int[] hashTrits;

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

    public TransactionViewModel(final int[] trits, Hash hash) {
        transaction = new com.iota.iri.model.Transaction();
        this.trits = new int[trits.length];
        System.arraycopy(trits, 0, this.trits, 0, trits.length);
        transaction.bytes = Converter.bytes(trits);
        transaction.hash = hash;

        transaction.type = FILLED_SLOT;

        transaction.validity = 0;
        transaction.arrivalTime = 0;
    }


    public TransactionViewModel(final byte[] bytes, Hash hash) throws RuntimeException {
        transaction = new Transaction();
        transaction.bytes = new byte[SIZE];
        System.arraycopy(bytes, 0, transaction.bytes, 0, SIZE);
        transaction.hash = hash;
        transaction.type = FILLED_SLOT;
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

    public static int[] trits(byte[] transactionBytes) {
        int[] trits;
        trits = new int[TRINARY_SIZE];
        if(transactionBytes != null) {
            Converter.getTrits(transactionBytes, trits);
        }
        return trits;
    }

    public synchronized int[] trits() {
        return (trits == null) ? (trits = trits(transaction.bytes)) : trits;
    }

    public void delete() throws Exception {
        Tangle.instance().delete(transaction).get();
    }
    public boolean store() throws Exception {
        TransactionRequester.instance().clearTransactionRequest(getHash());
        if(!exists(getHash())) {
            //log.info("Tx To save Hash: " + getHash());
            getBytes();
            getAddressHash();
            getBundleHash();
            getBranchTransactionHash();
            getTrunkTransactionHash();
            getTagValue();
            TransactionRequester.instance().requestTransaction(getBranchTransactionHash(), false);
            TransactionRequester.instance().requestTransaction(getTrunkTransactionHash(), false);
            if(getApprovers().length == 0) {
                TipsViewModel.addTipHash(getHash());
            }
            TipsViewModel.removeTipHash(getBranchTransactionHash());
            TipsViewModel.removeTipHash(getTrunkTransactionHash());
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

    public void setValidity(int validity) throws Exception {
        transaction.validity = validity;
        update("validity");
    }

    public int getValidity() {
        return transaction.validity;
    }

    public long getCurrentIndex() {
        return Converter.longValue(trits(), CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
    }

    public int[] getSignature() {
        return Arrays.copyOfRange(trits(), SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
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

    public static void updateSolidTransactions(Set<Hash> analyzedHashes) throws Exception {
        Iterator<Hash> hashIterator = analyzedHashes.iterator();
        TransactionViewModel transactionViewModel;
        while(hashIterator.hasNext()) {
            transactionViewModel = TransactionViewModel.fromHash(hashIterator.next());
            transactionViewModel.updateHeights();
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

    public long getHeight() {
        return transaction.height;
    }

    public void updateHeight(long height) throws Exception {
        transaction.height = height;
        update("height");
    }

    public long recursiveHeightUpdate() throws Exception {
        long height = transaction.height;
        if(height == 0L) {
            if(getTrunkTransactionHash().equals(Hash.NULL_HASH)) {
                height = 1;
            } else {
                TransactionViewModel trunk = getTrunkTransaction();
                if(trunk.getType() != TransactionViewModel.PREFILLED_SLOT) {
                    height = 1 + trunk.recursiveHeightUpdate();
                }
            }
            if(height != 0L) {
                transaction.height = height;
                update("height");
            }
        }
        return height;
    }

    public void updateHeights() throws Exception {
        TransactionViewModel transaction = this, trunk = this.getTrunkTransaction();
        Stack<TransactionViewModel> transactionViewModels = new Stack<>();
        transactionViewModels.push(transaction);
        while(trunk.getHeight() == 0 && trunk.getType() != PREFILLED_SLOT && !trunk.getHash().equals(Hash.NULL_HASH)) {
            transaction = trunk;
            trunk = transaction.getTrunkTransaction();
            transactionViewModels.push(transaction);
        }
        while(transactionViewModels.size() != 0) {
            transaction = transactionViewModels.pop();
            if(trunk.getHash().equals(Hash.NULL_HASH)) {
                transaction.updateHeight(1L);
            } else if ( trunk.getType() != PREFILLED_SLOT ){
                transaction.updateHeight(1 + trunk.getHeight());
            } else {
                break;
            }
            trunk = transaction;
        }
    }

    public void updateSender(String sender) throws Exception {
        transaction.sender = sender;
        update("sender");
    }
    public String getSender() {
        return transaction.sender;
    }
}
