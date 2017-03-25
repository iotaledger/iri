package com.iota.iri.service.viewModels;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.*;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionViewModel {
    private static ExecutorService executorService = Executors.newCachedThreadPool();

    private final com.iota.iri.model.Transaction transaction;
    private static final Logger log = LoggerFactory.getLogger(TransactionViewModel.class);

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

    public static final byte[] NULL_TRANSACTION_HASH_BYTES = new byte[Hash.SIZE_IN_BYTES];
    public static final byte[] NULL_TRANSACTION_BYTES = new byte[SIZE];

    public final static int GROUP = 0; // transactions GROUP means that's it's a non-leaf node (leafs store transaction value)
    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    public AddressViewModel address;
    public BundleViewModel bundle;
    public TransactionViewModel trunk;
    public TransactionViewModel branch;

    private static Set<Hash> transactionsToRequest = new HashSet<>();
    private static volatile int requestIndex = 0;

    public int[] hashTrits;

    private static final int MIN_WEIGHT_MAGNITUDE = 9;

    private int[] trits;
    public int weightMagnitude;

    public static TransactionViewModel fromHash(final byte[] hash) throws Exception {
        return fromHash(new Hash(hash));
    }

    public static TransactionViewModel fromHash(final Hash hash) throws Exception {
        Transaction transaction = new Transaction();
        transaction.hash = hash;
        Tangle.instance().load(transaction).get();
        return new TransactionViewModel(transaction);
    }

    public static boolean mightExist(byte[] hash) throws ExecutionException, InterruptedException {
        Transaction transaction = new Transaction();
        transaction.hash = new Hash(hash);
        return Tangle.instance().maybeHas(transaction).get();
    }

    public TransactionViewModel(final Transaction transaction) {
        this.transaction = transaction == null ? new Transaction(): transaction;
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
        System.arraycopy(bytes, 0, transaction.bytes, 0, BYTES_SIZE);

        for (int i = VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE; i < VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE; i++) {

            if (trits[i] != 0) {
                throw new RuntimeException("Invalid transaction value");
            }
        }

        getHashTrits(curl);
        getHash();
        // For testnet, reduced minWeight from 13 to 9
        // if (this.transaction.hash[Hash.SIZE_IN_BYTES - 3] != 0 || this.transaction.hash[Hash.SIZE_IN_BYTES - 2] != 0 || this.transaction.hash[Hash.SIZE_IN_BYTES - 1] != 0) {
        if (this.transaction.hash.bytes()[Hash.SIZE_IN_BYTES - 2] != 0 || this.transaction.hash.bytes()[Hash.SIZE_IN_BYTES - 1] != 0) {
            log.error("Invalid transaction hash. Hash found: " + new Hash(trits).toString());
            throw new RuntimeException("Invalid transaction hash");
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
        Tangle.instance().update(transaction, item);
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
    public Future<Boolean> store() throws Exception {
        Future<Boolean> future;
        if(!Tangle.instance().exists(Transaction.class, getHash()).get()) {
            getBytes();
            getAddressHash();
            getBundleHash();
            getBranchTransactionHash();
            getTrunkTransactionHash();
            getTagValue();
            future = Tangle.instance().save(transaction);
            update();
        } else {
            future = executorService.submit(() -> false);
        }
        return future;
    }

    private void update () throws Exception {
        clearTransactionRequest(getHash());
        requestTransaction(getBranchTransactionHash());
        requestTransaction(getTrunkTransactionHash());
        if(getApprovers().length == 0) {
            TipsViewModel.addTipHash(transaction.hash);
        } else {
            TipsViewModel.removeTipHash(transaction.hash);
        }
    }

    public static void clearTransactionRequest(Hash hash) {
        synchronized (TransactionViewModel.class) {
            transactionsToRequest.remove(hash);
        }
    }

    public static void requestTransaction(Hash hash) throws ExecutionException, InterruptedException {
        if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(hash)) {
            synchronized (TransactionViewModel.class) {
                transactionsToRequest.add(hash);
            }
        }
    }

    public static Hash[] approversFromHash(Hash hash) throws Exception {
        return TransactionViewModel.fromHash(hash).getApprovers();
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

    public BundleViewModel getBundle() throws Exception {
        if(bundle == null) {
            bundle = BundleViewModel.fromHash(getBundleHash());
        }
        return bundle;
    }

    public Hash getAddressHash() {
        if(transaction.address.hash == null) {
            transaction.address.hash = new Hash(Converter.bytes(trits(), ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE));
        }
        return transaction.address.hash;
    }

    public Hash getTagValue() {
        if(transaction.tag.value == null) {
            transaction.tag.value = new Hash(Converter.bytes(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, TAG_SIZE);
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

    private static volatile long lastTime = System.currentTimeMillis();
    public static void transactionToRequest(byte[] buffer, int offset) throws ExecutionException, InterruptedException {
        final long beginningTime = System.currentTimeMillis();
        if(++requestIndex >= numberOfTransactionsToRequest())
            requestIndex = 0;
        Hash hash = ((Hash) transactionsToRequest.toArray()[requestIndex]);

        if(hash != null && hash != null && !hash.equals(Hash.NULL_HASH)) {
            System.arraycopy(hash.bytes(), 0, buffer, offset, TransactionViewModel.HASH_SIZE);
        }
        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            log.info("Transactions to request = {}", transactionsToRequest.size() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
        }
    }

    public static void updateSolidTransactions(Set<Hash> analyzedHashes) throws Exception {
        Iterator<Hash> hashIterator = analyzedHashes.iterator();
        TransactionViewModel transactionViewModel;
        while(hashIterator.hasNext()) {
            transactionViewModel = TransactionViewModel.fromHash(hashIterator.next());
            transactionViewModel.setSolid(true);
            transactionViewModel.update("solid");
        }
    }

    public void setSolid(boolean solid) {
        if(solid) {
            transaction.solid = new byte[]{1};
        } else {
            transaction.solid = new byte[]{0};
        }
    }

    public boolean isSolid() {
        return transaction.solid[0] == 1;
    }

    public static int numberOfTransactionsToRequest() {
        return transactionsToRequest.size();
    }

    public static void rescanTransactionsToRequest() throws ExecutionException, InterruptedException {
        synchronized (TransactionViewModel.class) {
            Hash[] missingTx = Arrays.stream(Tangle.instance()
                    .scanForTips(Transaction.class).get()).toArray(Hash[]::new);
            transactionsToRequest.addAll(Arrays.asList(missingTx));
        }

    }
}
/*

    private static final Logger log = LoggerFactory.getLogger(ScratchpadViewModel.class);

    static long lastTime = 0L;

    public static ScratchpadViewModel instance = new ScratchpadViewModel();

    public boolean setAnalyzedTransactionFlag(int id, Hash hash) throws ExecutionException, InterruptedException {
        return Tangle.instance().save(id, new Flag(hash)).get();
    }

    public void copyAnalyzedFlagsList(int id, int otherId) throws Exception {
        Tangle.instance().copyTransientList(id, otherId).get();
    }
    public void clearAnalyzedTransactionsFlags(int id) throws Exception {
        Tangle.instance().flushTransientFlags(id).get();
    }
    public void releaseAnalyzedTransactionsFlags(int id) throws Exception {
        Tangle.instance().releaseTransientTable(id);
    }

    public int getAnalyzedTransactionTable() throws Exception {
        return Tangle.instance().createTransientFlagList();
    }

    public int getNumberOfTransactionsToRequest() throws ExecutionException, InterruptedException {
        return Tangle.instance().getCount(Scratchpad.class).get().intValue();
    }

    public Future<Void> clearReceivedTransaction(Hash hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        return Tangle.instance().delete(scratchpad);
    }

    public void rescanTransactionsToRequest() throws Exception {
        int id = getAnalyzedTransactionTable();
        Queue<Hash> nonAnalyzedTransactions;
        nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(Milestone.latestMilestone));
        nonAnalyzedTransactions.addAll(Arrays.asList(TipsViewModel.getTipHashes()));
        Hash hash;
        Tangle.instance().flush(Scratchpad.class).get();
        while((hash = nonAnalyzedTransactions.poll()) != null) {
            if(setAnalyzedTransactionFlag(id, hash)) {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
                if(transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT && !transactionViewModel.getHash().equals(Hash.NULL_HASH)) {
                    //log.info("Trasaction Hash to Request: " + new Hash(transactionViewModel.getHash()));
                    ScratchpadViewModel.instance().requestTransaction(transactionViewModel.getHash());
                } else {
                    nonAnalyzedTransactions.add(transactionViewModel.getTrunkTransactionHash());
                    nonAnalyzedTransactions.add(transactionViewModel.getBranchTransactionHash());
                }
            }
        }
        log.info("number of analyzed tx: " + Tangle.instance().getCount(id).get());
        releaseAnalyzedTransactionsFlags(id);
    }

    public Future<Boolean> requestTransaction(Hash hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        return Tangle.instance().save(scratchpad);
    }

    public void transactionToRequest(byte[] buffer, int offset) throws ExecutionException, InterruptedException {
        final long beginningTime = System.currentTimeMillis();
        Scratchpad scratchpad = ((Scratchpad) Tangle.instance().getLatest(Scratchpad.class).get());

        if(scratchpad != null && scratchpad.hash != null && !scratchpad.hash.equals(Hash.NULL_HASH)) {
            System.arraycopy(scratchpad.hash.bytes(), 0, buffer, offset, TransactionViewModel.HASH_SIZE);
        }
        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            log.info("Transactions to request = {}", getNumberOfTransactionsToRequest() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
        }
    }

    public static ScratchpadViewModel instance() {
        return instance;
    }
}
 */
