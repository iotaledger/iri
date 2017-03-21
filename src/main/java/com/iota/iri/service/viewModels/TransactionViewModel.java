package com.iota.iri.service.viewModels;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.*;
import com.iota.iri.service.ScratchpadViewModel;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
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

    public AddressViewModel address;
    public BundleViewModel bundle;
    public TagViewModel tag;
    public TransactionViewModel trunk;
    public TransactionViewModel branch;

    public int[] hashTrits;

    private static final int MIN_WEIGHT_MAGNITUDE = 13;

    private int[] trits;
    public int weightMagnitude;

    public static TransactionViewModel fromHash(final byte[] hash) throws Exception {
        Transaction transaction = new Transaction();
        transaction.hash = Arrays.copyOf(hash, hash.length);
        Tangle.instance().load(transaction).get();
        return new TransactionViewModel(transaction);
    }

    public static TransactionViewModel fromHash(final int[] hash) throws Exception{
        return fromHash(Converter.bytes(hash));
    }

    public static TransactionViewModel fromHash(final Hash hash) throws Exception {
        return TransactionViewModel.fromHash(hash.bytes());
    }

    public static boolean mightExist(byte[] hash) throws ExecutionException, InterruptedException {
        Transaction transaction = new Transaction();
        transaction.hash = hash;
        return Tangle.instance().maybeHas(transaction).get();
    }

    public TransactionViewModel(final Transaction transaction) {
        this.transaction = transaction == null ? new Transaction(): transaction;
    }

    public TransactionViewModel(final int[] trits) {
        transaction = new com.iota.iri.model.Transaction();

        this.trits = trits;
        this.transaction.bytes = Converter.bytes(trits);

        transaction.type = AbstractStorage.FILLED_SLOT;

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
        if (this.transaction.hash[Hash.SIZE_IN_BYTES - 3] != 0 || this.transaction.hash[Hash.SIZE_IN_BYTES - 2] != 0 || this.transaction.hash[Hash.SIZE_IN_BYTES - 1] != 0) {
            log.error("Invalid transaction hash. Hash found: " + new Hash(trits).toString());
            throw new RuntimeException("Invalid transaction hash");
        }

        weightMagnitude = MIN_WEIGHT_MAGNITUDE;
        while (weightMagnitude < Curl.HASH_LENGTH && hashTrits[Curl.HASH_LENGTH - weightMagnitude - 1] == 0) {
            weightMagnitude++;
        }


        transaction.type = AbstractStorage.FILLED_SLOT;
        transaction.validity = 0;
        transaction.arrivalTime = 0;
    }

    public TransactionViewModel(final byte[] mainBuffer, final long pointer) {
        transaction = new Transaction();
        transaction.type = mainBuffer[TYPE_OFFSET];
        System.arraycopy(mainBuffer, HASH_OFFSET, this.transaction.hash = new byte[HASH_SIZE], 0, HASH_SIZE);
        System.arraycopy(mainBuffer, BYTES_OFFSET, transaction.bytes, 0, BYTES_SIZE);
        transaction.validity = mainBuffer[VALIDITY_OFFSET];
        transaction.arrivalTime = AbstractStorage.value(mainBuffer, ARRIVAL_TIME_OFFSET);
    }

    public static int getNumberOfStoredTransactions() throws ExecutionException, InterruptedException {
        return Tangle.instance().getNumberOfStoredTransactions().get().intValue();
    }


    public void update(String item) throws Exception {
        Tangle.instance().update(transaction, item, Transaction.class.getDeclaredField(item).get(transaction));
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

    public Future<Boolean> store() throws Exception {
        Future<Boolean> future;
        if(!Tangle.instance().transactionExists(getHash())) {
            getBytes();
            getAddressHash();
            getBundleHash();
            getBranchTransactionHash();
            getTrunkTransactionHash();
            getTagBytes();
            future = Tangle.instance().save(transaction);
            for(Future<?> updateFuture: update()) {
                if(updateFuture != null)
                    updateFuture.get();
            }
        } else {
            future = executorService.submit(() -> false);
        }
        return future;
    }

    private Set<Future> update () throws Exception {
        Set<Future> futures = new HashSet<>();
        ScratchpadViewModel.instance().clearReceivedTransaction(transaction.hash);
        if (!Arrays.equals(getBranchTransactionHash(), TransactionViewModel.NULL_TRANSACTION_HASH_BYTES))
            futures.add(ScratchpadViewModel.instance().requestTransaction(getBranchTransactionHash()));
        if (!Arrays.equals(getTrunkTransactionHash(), TransactionViewModel.NULL_TRANSACTION_HASH_BYTES))
            futures.add(ScratchpadViewModel.instance().requestTransaction(getTrunkTransactionHash()));
        if(getApprovers().length == 0) {
            TipsViewModel.addTipHash(transaction.hash);
        } else {
            TipsViewModel.removeTipHash(transaction.hash);
        }
        return futures;
    }

    private static Future<Void> updateType(Hash hash) {
        Transaction transaction = new Transaction();
        transaction.hash = hash.bytes();
        transaction.type = AbstractStorage.FILLED_SLOT;
        return Tangle.instance().updateType(transaction);
    }

    public static Hash[] approversFromHash(Hash hash) throws Exception {
        return TransactionViewModel.fromHash(hash).getApprovers();
    }

    public Hash[] getApprovers() throws ExecutionException, InterruptedException {
        Approvee self = new Approvee();
        self.hash = transaction.hash;
        Tangle.instance().load(self).get();
        return self.transactions != null? Arrays.stream(self.transactions).map(transaction -> new Hash(transaction.bytes())).toArray(Hash[]::new): new Hash[0];
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

    public byte[] getHash() {
        if(transaction.hash == null) {
            transaction.hash = Converter.bytes(getHashTrits(null));
        }
        return transaction.hash;
    }

    public AddressViewModel getAddress() {
        if(address == null)
            address = new AddressViewModel(getAddressHash());
        return address;
    }

    public TagViewModel getTag() {
        return new TagViewModel(getTagBytes());
    }

    public BundleViewModel getBundle() throws ExecutionException, InterruptedException {
        if(bundle == null) {
            bundle = BundleViewModel.fromHash(getBundleHash());
        }
        return bundle;
    }

    public byte[] getAddressHash() {
        if(transaction.address.hash == null) {
            transaction.address.hash = Converter.bytes(trits(), ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE);
        }
        return transaction.address.hash;
    }

    public byte[] getTagBytes() {
        if(transaction.tag.bytes == null) {
            transaction.tag.bytes= Converter.bytes(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE);
            if(transaction.tag.bytes.length < Hash.SIZE_IN_BYTES) {
                transaction.tag.bytes = ArrayUtils.addAll(transaction.tag.bytes, Arrays.copyOf(NULL_TRANSACTION_HASH_BYTES, Hash.SIZE_IN_BYTES - transaction.tag.bytes.length));
            }
        }
        return transaction.tag.bytes;
    }

    public byte[] getBundleHash() {
        if(transaction.bundle.hash == null) {
            transaction.bundle.hash = Converter.bytes(trits(), BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE);
        }
        return transaction.bundle.hash;
    }

    public byte[] getTrunkTransactionHash() {
        if(transaction.trunk.hash == null) {
            transaction.trunk.hash = Converter.bytes(trits(), TRUNK_TRANSACTION_TRINARY_OFFSET, TRUNK_TRANSACTION_TRINARY_SIZE);
        }
        return transaction.trunk.hash;
    }

    public byte[] getBranchTransactionHash() {
        if(transaction.branch.hash == null) {
            transaction.branch.hash = Converter.bytes(trits(), BRANCH_TRANSACTION_TRINARY_OFFSET, BRANCH_TRANSACTION_TRINARY_SIZE);
        }
        return transaction.branch.hash;
    }

    public long value() {
        return Converter.longValue(trits, VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE);
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

    public static boolean exists(byte[] hash) throws ExecutionException, InterruptedException {
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

    /*
    private void populateTrits() {
        int[] partialTrits = null;
        this.trits = new int[TRINARY_SIZE];
        Converter.copyTrits(transaction.currentIndex, trits, CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
        Converter.copyTrits(transaction.lastIndex, trits, LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE);
        Converter.copyTrits(transaction.value, trits, VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE);
        Converter.copyTrits(transaction.timestamp, trits, TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_SIZE);

        populateSignatureTrits(partialTrits);
        populateAddressTrits(partialTrits);
        populateTagTrits(partialTrits);
        populateBundleTrits(partialTrits);
        populateTrunkTrits(partialTrits);
        populateBranchTrits(partialTrits);
        populateNonceTrits(partialTrits);
    }

    private void populateSignatureTrits(int[] partialTrits) {
        if(this.transaction.signature == null) {
            this.transaction.signature = new byte[TransactionViewModel.SIZE];
        }
        partialTrits = new int[SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE];
        Converter.getTrits(transaction.signature, partialTrits);
        System.arraycopy(partialTrits, 0, trits, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
    }

    private void populateBranchTrits(int[] partialTrits) {
        if(transaction.branch.hash == null) {
            transaction.branch.hash = new byte[Hash.SIZE_IN_BYTES];
        }
        partialTrits = new int[BRANCH_TRANSACTION_TRINARY_SIZE];
        Converter.getTrits(transaction.branch.hash, partialTrits);
        System.arraycopy(partialTrits, 0, trits, BRANCH_TRANSACTION_TRINARY_OFFSET, BRANCH_TRANSACTION_TRINARY_SIZE);
    }

    private void populateTrunkTrits(int[] partialTrits) {
        if(transaction.trunk.hash == null) {
            transaction.trunk.hash = new byte[Hash.SIZE_IN_BYTES];
        }
        partialTrits = new int[TRUNK_TRANSACTION_TRINARY_SIZE];
        Converter.getTrits(transaction.trunk.hash, partialTrits);
        System.arraycopy(partialTrits, 0, trits, TRUNK_TRANSACTION_TRINARY_OFFSET, TRUNK_TRANSACTION_TRINARY_SIZE);
    }

    private void populateBundleTrits(int[] partialTrits) {
        if(transaction.bundle.hash == null) {
            transaction.bundle.hash = new byte[Hash.SIZE_IN_BYTES];
        }
        partialTrits = new int[BUNDLE_TRINARY_SIZE];
        Converter.getTrits(transaction.bundle.hash, partialTrits);
        System.arraycopy(partialTrits, 0, trits, BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE);
    }

    private void populateAddressTrits(int[] partialTrits) {
        partialTrits = new int[ADDRESS_TRINARY_SIZE];
        if(transaction.address.hash == null) {
            transaction.address.hash = new byte[Hash.SIZE_IN_BYTES];
        }
        Converter.getTrits(transaction.address.hash, partialTrits);
        System.arraycopy(partialTrits, 0, trits, ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE);

    }


    private void populateTagTrits(int[] partialTrits) {
        if(transaction.tag.bytes == null) {
            transaction.tag.bytes = new byte[TAG_SIZE];
        }
        partialTrits = new int[TAG_TRINARY_SIZE];
        Converter.getTrits(transaction.tag.bytes, partialTrits);
        System.arraycopy(partialTrits, 0, trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE);

    }

    private void populateNonceTrits(int[] partialTrits) {
        if(this.transaction.nonce == null) {
            this.transaction.nonce = new byte[Hash.SIZE_IN_BYTES];
        }
        partialTrits = new int[NONCE_TRINARY_SIZE];
        Converter.getTrits(transaction.nonce, partialTrits);
        System.arraycopy(partialTrits, 0, trits, NONCE_TRINARY_OFFSET, NONCE_TRINARY_SIZE);
    }
    */
    public static void dump(final byte[] mainBuffer, final byte[] hash, final TransactionViewModel transactionViewModel) {

        /*
        System.arraycopy(new byte[AbstractStorage.CELL_SIZE], 0, mainBuffer, 0, AbstractStorage.CELL_SIZE);
        System.arraycopy(hash, 0, mainBuffer, HASH_OFFSET, HASH_SIZE);

        if (transactionViewModel == null) {
            mainBuffer[TYPE_OFFSET] = AbstractStorage.PREFILLED_SLOT;
        } else {
            mainBuffer[TYPE_OFFSET] = (byte) transactionViewModel.type;
            System.arraycopy(transactionViewModel.getBytes(), 0, mainBuffer, BYTES_OFFSET, BYTES_SIZE);
            System.arraycopy(transactionViewModel.getAddress(), 0, mainBuffer, ADDRESS_OFFSET, ADDRESS_SIZE);
            AbstractStorage.setValue(mainBuffer, VALUE_OFFSET, transactionViewModel.value());
            final int[] trits = transactionViewModel.trits();
            System.arraycopy(Converter.value(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, mainBuffer, TAG_OFFSET, TAG_SIZE);
            AbstractStorage.setValue(mainBuffer, CURRENT_INDEX_OFFSET, transactionViewModel.getCurrentIndex());
            AbstractStorage.setValue(mainBuffer, LAST_INDEX_OFFSET, transactionViewModel.getLastIndex());
            System.arraycopy(Converter.value(trits, BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE), 0, mainBuffer, BUNDLE_OFFSET, BUNDLE_SIZE);
            System.arraycopy(transactionViewModel.getTrunkTransactionHash(), 0, mainBuffer, TRUNK_TRANSACTION_OFFSET, TRUNK_TRANSACTION_SIZE);
            System.arraycopy(transactionViewModel.getBranchTransactionHash(), 0, mainBuffer, BRANCH_TRANSACTION_OFFSET, BRANCH_TRANSACTION_SIZE);

            long approvedTransactionPointer = StorageTransactions.instance().transactionPointer(transactionViewModel.getTrunkTransactionHash());
            if (approvedTransactionPointer == 0) {
                Storage.approvedTransactionsToStore[Storage.numberOfApprovedTransactionsToStore++] = transactionViewModel.getTrunkTransactionHash();
            } else {

                if (approvedTransactionPointer < 0) {
                    approvedTransactionPointer = -approvedTransactionPointer;
                }
                final long index = (approvedTransactionPointer - (AbstractStorage.CELLS_OFFSET - AbstractStorage.SUPER_GROUPS_OFFSET)) >> 11;
                StorageTransactions.instance().transactionsTipsFlags().put(
                        (int)(index >> 3),
                        (byte)(StorageTransactions.instance().transactionsTipsFlags().get((int)(index >> 3)) & (0xFF ^ (1 << (index & 7)))));
            }
            if (!Arrays.equals(transactionViewModel.getBranchTransactionHash(), transactionViewModel.getTrunkTransactionHash())) {

                approvedTransactionPointer = StorageTransactions.instance().transactionPointer(transactionViewModel.getBranchTransactionHash());
                if (approvedTransactionPointer == 0) {
                    Storage.approvedTransactionsToStore[Storage.numberOfApprovedTransactionsToStore++] = transactionViewModel.getBranchTransactionHash();
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


}
