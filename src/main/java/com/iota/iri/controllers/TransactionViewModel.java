package com.iota.iri.controllers;

import java.util.*;

import com.iota.iri.TransactionValidator;
import com.iota.iri.model.Hashes;
import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;

public class TransactionViewModel {

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

    private HashesViewModel address;
    private HashesViewModel bundle;
    private HashesViewModel approovers;
    private TransactionViewModel trunk;
    private TransactionViewModel branch;
    private final Hash hash;


    public final static int GROUP = 0; // transactions GROUP means that's it's a non-leaf node (leafs store transaction value)
    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    private int[] hashTrits;

    private int[] trits;
    public int weightMagnitude;

    public static TransactionViewModel find(byte[] hash) throws Exception {
        return new TransactionViewModel((Transaction) Tangle.instance().find(Transaction.class, hash), new Hash(hash));
    }

    public static TransactionViewModel quietFromHash(final Hash hash) {
        try {
            return fromHash(hash);
        } catch (Exception e) {
            return new TransactionViewModel(new Transaction(), hash);
        }
    }
    public static TransactionViewModel fromHash(final Hash hash) throws Exception {
        return new TransactionViewModel((Transaction) Tangle.instance().load(Transaction.class, hash), hash);
    }

    public static boolean mightExist(Hash hash) throws Exception {
        return Tangle.instance().maybeHas(Transaction.class, hash);
    }

    public TransactionViewModel(final Transaction transaction, final Hash hash) {
        this.transaction = transaction == null || transaction.bytes == null ? new Transaction(): transaction;
        this.hash = hash == null? Hash.NULL_HASH: hash;
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

    public static int getNumberOfStoredTransactions() throws Exception {
        return Tangle.instance().getCount(Transaction.class).intValue();
    }

    public boolean update(String item) throws Exception {
        if(hash.equals(Hash.NULL_HASH)) {
            return false;
        }
        return Tangle.instance().update(transaction, getHash(), item);
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
        Tangle.instance().delete(Transaction.class, getHash());
    }

    public Map<Indexable, Persistable> getSaveBatch() throws Exception {
        Map<Indexable, Persistable> hashesMap = storeHashes(Arrays.asList(getAddressHash(), getBundleHash(),
                getBranchTransactionHash(), getTrunkTransactionHash(), getTagValue()));
        getBytes();
        setMetadata();
        hashesMap.put(getHash(), transaction);
        return hashesMap;
    }

    public static TransactionViewModel first() throws Exception {
        Pair<Indexable, Persistable> transactionPair = Tangle.instance().getFirst(Transaction.class, Hash.class);
        if(transactionPair != null) {
            return new TransactionViewModel((Transaction) transactionPair.hi, (Hash) transactionPair.low);
        }
        return null;
    }

    public TransactionViewModel next() throws Exception {
        Pair<Indexable, Persistable> transactionPair = Tangle.instance().next(Transaction.class, getHash());
        if(transactionPair != null) {
            return new TransactionViewModel((Transaction) transactionPair.hi, (Hash) transactionPair.low);
        }
        return null;
    }

    public boolean store() throws Exception {
        if(!exists(getHash())) {
            return Tangle.instance().saveBatch(getSaveBatch());
        }
        return false;
    }

    public void updateStatus() throws Exception {
        TransactionRequester.instance().clearTransactionRequest(getHash());
        if(quickSetSolid()) {
            TransactionValidator.addSolidTransaction(getHash());
        }
        if(getApprovers().size() == 0) {
            TipsViewModel.addTipHash(getHash());
        }
    }

    public boolean quietQuickSetSolid() {
        try {
            return quickSetSolid();
        } catch (Exception e) {
            return false;
        }
    }

    boolean quickSetSolid() throws Exception {
        boolean solid = true;
        if(!checkApproovee(getTrunkTransaction())) {
            solid = false;
        }
        if(!checkApproovee(getBranchTransaction())) {
            solid = false;
        }
        if(solid) {
            if(updateSolid(true)) {
                updateHeights();
                return true;
            }
        }
        return false;
    }

    private boolean checkApproovee(TransactionViewModel approovee) throws Exception {
        if(approovee.getType() == PREFILLED_SLOT) {
            TransactionRequester.instance().requestTransaction(approovee.getHash(), false);
            if(approovee.getHash().equals(Hash.NULL_HASH)) {
                return true;
            }
            return false;
        }
        return approovee.isSolid();
    }

    private Map<Indexable, Persistable> storeHashes(Collection<Hash> hashesToSave) throws Exception {
        Map<Indexable, Persistable> map = new HashMap<>();
        for(Hash hash: hashesToSave) {
            Map.Entry<Indexable, Persistable> entry = HashesViewModel.getEntry(hash, getHash());
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    public HashesViewModel getApprovers() throws Exception {
        if(approovers == null) {
            approovers = HashesViewModel.load(hash);
        }
        return approovers;
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
        return hash;
    }

    public HashesViewModel getAddress() throws Exception {
        if(address == null) {
            address = HashesViewModel.load(getAddressHash());
        }
        return address;
    }

    public HashesViewModel getTag() throws Exception {
        return HashesViewModel.load(getTagValue());
    }

    public HashesViewModel getBundle() throws Exception {
        if(bundle == null) {
            bundle = HashesViewModel.load(getBundleHash());
        }
        return bundle;
    }

    public Hash getAddressHash() {
        if(transaction.address == null) {
            transaction.address = new Hash(trits(), ADDRESS_TRINARY_OFFSET);
        }
        return transaction.address;
    }

    public Hash getTagValue() {
        if(transaction.tag == null) {
            transaction.tag = new Hash(Converter.bytes(trits(), TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, TAG_SIZE);
        }
        return transaction.tag;
    }

    public Hash getBundleHash() {
        if(transaction.bundle == null) {
            transaction.bundle = new Hash(trits(), BUNDLE_TRINARY_OFFSET);
        }
        return transaction.bundle;
    }

    public Hash getTrunkTransactionHash() {
        if(transaction.trunk == null) {
            transaction.trunk = new Hash(trits(), TRUNK_TRANSACTION_TRINARY_OFFSET);
        }
        return transaction.trunk;
    }

    public Hash getBranchTransactionHash() {
        if(transaction.branch == null) {
            transaction.branch = new Hash(trits(), BRANCH_TRANSACTION_TRINARY_OFFSET);
        }
        return transaction.branch;
    }

    public long value() {
        return transaction.value;
    }

    public void setValidity(int validity) throws Exception {
        transaction.validity = validity;
        update("validity");
    }

    public int getValidity() {
        return transaction.validity;
    }

    public long getCurrentIndex() {
        return transaction.currentIndex;
    }

    public int[] getSignature() {
        return Arrays.copyOfRange(trits(), SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
    }

    public long getTimestamp() {
        return transaction.timestamp;
    }

    public byte[] getNonce() {
        return Converter.bytes(trits(), NONCE_TRINARY_OFFSET, NONCE_TRINARY_SIZE);
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

    public static boolean exists(Hash hash) throws Exception {
        return Tangle.instance().exists(Transaction.class, hash);
    }

    public static void updateSolidTransactions(final Set<Hash> analyzedHashes) throws Exception {
        Iterator<Hash> hashIterator = analyzedHashes.iterator();
        TransactionViewModel transactionViewModel;
        while(hashIterator.hasNext()) {
            transactionViewModel = TransactionViewModel.fromHash(hashIterator.next());
            transactionViewModel.updateHeights();
            transactionViewModel.updateSolid(true);
        }
    }

    public boolean updateSolid(final boolean solid) throws Exception {
        if(solid != transaction.solid) {
            transaction.solid = solid;
            update("solid");
            return solid;
        }
        return false;
    }

    /*
    public HashesViewModel nonSolidGroup() throws Exception {
        return HashesViewModel.load(new IntegerIndex(-Math.abs(transaction.group)));
    }
    */

    public boolean isSolid() throws Exception {
        return transaction.solid;
    }

    public int snapshotIndex() {
        return transaction.snapshot;
    }

    public void setSnapshot(final int index) throws Exception {
        if ( index != transaction.snapshot ) {
            transaction.snapshot = index;
            update("snapshot");
        }
    }

    public long getHeight() {
        return transaction.height;
    }

    private void updateHeight(long height) throws Exception {
        transaction.height = height;
        update("height");
    }

    void updateHeights() throws Exception {
        TransactionViewModel transaction = this, trunk = this.getTrunkTransaction();
        Stack<Hash> transactionViewModels = new Stack<>();
        transactionViewModels.push(transaction.getHash());
        while(trunk.getHeight() == 0 && trunk.getType() != PREFILLED_SLOT && !trunk.getHash().equals(Hash.NULL_HASH)) {
            transaction = trunk;
            trunk = transaction.getTrunkTransaction();
            transactionViewModels.push(transaction.getHash());
        }
        while(transactionViewModels.size() != 0) {
            transaction = TransactionViewModel.fromHash(transactionViewModels.pop());
            if(trunk.getHash().equals(Hash.NULL_HASH) && trunk.getHeight() == 0 && !transaction.getHash().equals(Hash.NULL_HASH)) {
                transaction.updateHeight(1L);
            } else if ( trunk.getType() != PREFILLED_SLOT && transaction.getHeight() == 0){
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
