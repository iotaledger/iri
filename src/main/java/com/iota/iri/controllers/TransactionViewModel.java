package com.iota.iri.controllers;

import com.iota.iri.model.Address;
import com.iota.iri.model.Approvee;
import com.iota.iri.model.Bundle;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Tag;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class TransactionViewModel {

    private final com.iota.iri.model.Transaction transaction;

    public static final int SIZE = 1604;
    private static final int TAG_SIZE = 17;

    public static final long SUPPLY = 2779530283277761L; // = (3^33 - 1) / 2

    public static final int SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET = 0, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE = 6561;
    public static final int ADDRESS_TRINARY_OFFSET = SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE, ADDRESS_TRINARY_SIZE = 243;
    public static final int VALUE_TRINARY_OFFSET = ADDRESS_TRINARY_OFFSET + ADDRESS_TRINARY_SIZE, VALUE_TRINARY_SIZE = 81, VALUE_USABLE_TRINARY_SIZE = 33;
    public static final int TAG_TRINARY_OFFSET = VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE, TAG_TRINARY_SIZE = 81;
    public static final int TIMESTAMP_TRINARY_OFFSET = TAG_TRINARY_OFFSET + TAG_TRINARY_SIZE, TIMESTAMP_TRINARY_SIZE = 27;
    public static final int CURRENT_INDEX_TRINARY_OFFSET = TIMESTAMP_TRINARY_OFFSET + TIMESTAMP_TRINARY_SIZE, CURRENT_INDEX_TRINARY_SIZE = 27;
    public static final int LAST_INDEX_TRINARY_OFFSET = CURRENT_INDEX_TRINARY_OFFSET + CURRENT_INDEX_TRINARY_SIZE, LAST_INDEX_TRINARY_SIZE = 27;
    public static final int BUNDLE_TRINARY_OFFSET = LAST_INDEX_TRINARY_OFFSET + LAST_INDEX_TRINARY_SIZE, BUNDLE_TRINARY_SIZE = 243;
    public static final int TRUNK_TRANSACTION_TRINARY_OFFSET = BUNDLE_TRINARY_OFFSET + BUNDLE_TRINARY_SIZE, TRUNK_TRANSACTION_TRINARY_SIZE = 243;
    public static final int BRANCH_TRANSACTION_TRINARY_OFFSET = TRUNK_TRANSACTION_TRINARY_OFFSET + TRUNK_TRANSACTION_TRINARY_SIZE, BRANCH_TRANSACTION_TRINARY_SIZE = 243;
    private static final int NONCE_TRINARY_OFFSET = BRANCH_TRANSACTION_TRINARY_OFFSET + BRANCH_TRANSACTION_TRINARY_SIZE, NONCE_TRINARY_SIZE = 243;

    public static final int TRINARY_SIZE = NONCE_TRINARY_OFFSET + NONCE_TRINARY_SIZE;

    public static final int ESSENCE_TRINARY_OFFSET = ADDRESS_TRINARY_OFFSET, ESSENCE_TRINARY_SIZE = ADDRESS_TRINARY_SIZE + VALUE_TRINARY_SIZE + TAG_TRINARY_SIZE + TIMESTAMP_TRINARY_SIZE + CURRENT_INDEX_TRINARY_SIZE + LAST_INDEX_TRINARY_SIZE;

    private ApproveeViewModel approovers;
    private TransactionViewModel trunk;
    private TransactionViewModel branch;
    private final Hash hash;


    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    private int[] trits;
    public int weightMagnitude;

    public static TransactionViewModel find(final Tangle tangle, byte[] hash) throws Exception {
        return new TransactionViewModel((Transaction) tangle.find(Transaction.class, hash), new Hash(hash));
    }

    public static TransactionViewModel quietFromHash(final Tangle tangle, final Hash hash) {
        try {
            return fromHash(tangle, hash);
        } catch (Exception e) {
            return new TransactionViewModel(new Transaction(), hash);
        }
    }

    public static TransactionViewModel fromHash(final Tangle tangle, final Hash hash) throws Exception {
        return new TransactionViewModel((Transaction) tangle.load(Transaction.class, hash), hash);
    }

    public TransactionViewModel(final Transaction transaction, final Hash hash) {
        this.transaction = transaction == null || transaction.bytes == null ? new Transaction() : transaction;
        this.hash = hash == null ? Hash.NULL_HASH : hash;
    }

    public TransactionViewModel(final int[] trits, Hash hash) {
        transaction = new com.iota.iri.model.Transaction();
        this.trits = new int[trits.length];
        System.arraycopy(trits, 0, this.trits, 0, trits.length);
        transaction.bytes = Converter.bytes(trits);
        this.hash = hash;

        transaction.type = FILLED_SLOT;

        transaction.validity = 0;
        transaction.arrivalTime = 0;
    }


    public TransactionViewModel(final byte[] bytes, Hash hash) throws RuntimeException {
        transaction = new Transaction();
        transaction.bytes = new byte[SIZE];
        System.arraycopy(bytes, 0, transaction.bytes, 0, SIZE);
        this.hash = hash;
        transaction.type = FILLED_SLOT;
    }

    public static int getNumberOfStoredTransactions(final Tangle tangle) throws Exception {
        return tangle.getCount(Transaction.class).intValue();
    }

    public boolean update(final Tangle tangle, String item) throws Exception {
        getAddressHash();
        getTrunkTransactionHash();
        getBranchTransactionHash();
        getBundleHash();
        getTagValue();
        if (hash.equals(Hash.NULL_HASH)) {
            return false;
        }
        return tangle.update(transaction, getHash(), item);
    }

    public TransactionViewModel getBranchTransaction(final Tangle tangle) throws Exception {
        if (branch == null) {
            branch = TransactionViewModel.fromHash(tangle, getBranchTransactionHash());
        }
        return branch;
    }

    public TransactionViewModel getTrunkTransaction(final Tangle tangle) throws Exception {
        if (trunk == null) {
            trunk = TransactionViewModel.fromHash(tangle, getTrunkTransactionHash());
        }
        return trunk;
    }

    public static int[] trits(byte[] transactionBytes) {
        int[] trits;
        trits = new int[TRINARY_SIZE];
        if (transactionBytes != null) {
            Converter.getTrits(transactionBytes, trits);
        }
        return trits;
    }

    public synchronized int[] trits() {
        return (trits == null) ? (trits = trits(transaction.bytes)) : trits;
    }

    public List<Pair<Indexable, Persistable>> getSaveBatch() throws Exception {
        List<Pair<Indexable, Persistable>> hashesList = new ArrayList<>();
        hashesList.add(new Pair<>(getAddressHash(), new Address(getHash())));
        hashesList.add(new Pair<>(getBundleHash(), new Bundle(getHash())));
        hashesList.add(new Pair<>(getBranchTransactionHash(), new Approvee(getHash())));
        hashesList.add(new Pair<>(getTrunkTransactionHash(), new Approvee(getHash())));
        hashesList.add(new Pair<>(getTagValue(), new Tag(getHash())));
        getBytes();
        setMetadata();
        hashesList.add(new Pair<>(getHash(), transaction));
        return hashesList;
    }

    public static TransactionViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> transactionPair = tangle.getFirst(Transaction.class, Hash.class);
        if (transactionPair != null && transactionPair.hi != null) {
            return new TransactionViewModel((Transaction) transactionPair.hi, (Hash) transactionPair.low);
        }
        return null;
    }

    public TransactionViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> transactionPair = tangle.next(Transaction.class, getHash());
        if (transactionPair != null && transactionPair.hi != null) {
            return new TransactionViewModel((Transaction) transactionPair.hi, (Hash) transactionPair.low);
        }
        return null;
    }

    public boolean store(Tangle tangle) throws Exception {
        if (!exists(tangle, getHash()) && !getHash().equals(Hash.NULL_HASH)) {
            return tangle.saveBatch(getSaveBatch());
        }
        return false;
    }

    public ApproveeViewModel getApprovers(Tangle tangle) throws Exception {
        if (approovers == null) {
            approovers = ApproveeViewModel.load(tangle, hash);
        }
        return approovers;
    }

    public final int getType() {
        return transaction.type;
    }

    public void setArrivalTime(long time) {
        transaction.arrivalTime = time;
    }

    public byte[] getBytes() {
        if (transaction.bytes == null || transaction.bytes.length != SIZE) {
            transaction.bytes = trits == null ? new byte[SIZE] : Converter.bytes(trits());
        }
        return transaction.bytes;
    }

    public Hash getHash() {
        return hash;
    }

    public Hash getAddressHash() {
        if (transaction.address == null) {
            transaction.address = new Hash(trits(), ADDRESS_TRINARY_OFFSET);
        }
        return transaction.address;
    }

    public Hash getTagValue() {
        if (transaction.tag == null) {
            transaction.tag = new Hash(Converter.bytes(trits(), TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, TAG_SIZE);
        }
        return transaction.tag;
    }

    public Hash getBundleHash() {
        if (transaction.bundle == null) {
            transaction.bundle = new Hash(trits(), BUNDLE_TRINARY_OFFSET);
        }
        return transaction.bundle;
    }

    public Hash getTrunkTransactionHash() {
        if (transaction.trunk == null) {
            transaction.trunk = new Hash(trits(), TRUNK_TRANSACTION_TRINARY_OFFSET);
        }
        return transaction.trunk;
    }

    public Hash getBranchTransactionHash() {
        if (transaction.branch == null) {
            transaction.branch = new Hash(trits(), BRANCH_TRANSACTION_TRINARY_OFFSET);
        }
        return transaction.branch;
    }

    public long value() {
        return transaction.value;
    }

    public void setValidity(final Tangle tangle, int validity) throws Exception {
        transaction.validity = validity;
        update(tangle, "validity");
    }

    public int getValidity() {
        return transaction.validity;
    }

    public long getCurrentIndex() {
        return transaction.currentIndex;
    }

    public long getTimestamp() {
        return transaction.timestamp;
    }

    public long lastIndex() {
        return transaction.lastIndex;
    }

    public void setMetadata() {
        transaction.value = Converter.longValue(trits(), VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE);
        transaction.timestamp = Converter.longValue(trits(), TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_SIZE);
        transaction.currentIndex = Converter.longValue(trits(), CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
        transaction.lastIndex = Converter.longValue(trits(), LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE);
    }

    public static boolean exists(Tangle tangle, Hash hash) throws Exception {
        return tangle.exists(Transaction.class, hash);
    }

    public static void updateSolidTransactions(final Tangle tangle, final Set<Hash> analyzedHashes) throws Exception {
        Iterator<Hash> hashIterator = analyzedHashes.iterator();
        TransactionViewModel transactionViewModel;
        while (hashIterator.hasNext()) {
            transactionViewModel = TransactionViewModel.fromHash(tangle, hashIterator.next());
            transactionViewModel.updateHeights(tangle);
            transactionViewModel.updateSolid(true);
            transactionViewModel.update(tangle, "solid|height");
        }
    }

    public boolean updateSolid(boolean solid) throws Exception {
        if (solid != transaction.solid) {
            transaction.solid = solid;
            return true;
        }
        return false;
    }

    public boolean isSolid() {
        return transaction.solid;
    }

    public int snapshotIndex() {
        return transaction.snapshot;
    }

    public void setSnapshot(final Tangle tangle, final int index) throws Exception {
        if (index != transaction.snapshot) {
            transaction.snapshot = index;
            update(tangle, "snapshot");
        }
    }

    public long getHeight() {
        return transaction.height;
    }

    private void updateHeight(long height) throws Exception {
        transaction.height = height;
    }

    public void updateHeights(final Tangle tangle) throws Exception {
        TransactionViewModel transaction = this, trunk = this.getTrunkTransaction(tangle);
        Stack<Hash> transactionViewModels = new Stack<>();
        transactionViewModels.push(transaction.getHash());
        while (trunk.getHeight() == 0 && trunk.getType() != PREFILLED_SLOT && !trunk.getHash().equals(Hash.NULL_HASH)) {
            transaction = trunk;
            trunk = transaction.getTrunkTransaction(tangle);
            transactionViewModels.push(transaction.getHash());
        }
        while (transactionViewModels.size() != 0) {
            transaction = TransactionViewModel.fromHash(tangle, transactionViewModels.pop());
            if (trunk.getHash().equals(Hash.NULL_HASH) && trunk.getHeight() == 0 && !transaction.getHash().equals(Hash.NULL_HASH)) {
                transaction.updateHeight(1L);
                transaction.update(tangle, "height");
            } else if (trunk.getType() != PREFILLED_SLOT && transaction.getHeight() == 0) {
                transaction.updateHeight(1 + trunk.getHeight());
                transaction.update(tangle, "height");
            } else {
                break;
            }
            trunk = transaction;
        }
    }

    public void updateSender(String sender) throws Exception {
        transaction.sender = sender;
    }
}
