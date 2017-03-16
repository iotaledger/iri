package com.iota.iri.service.viewModels;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.iota.iri.Milestone;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.*;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionViewModel {
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
    public static final byte[] NULL_TRANSACTION_BYTES = new byte[TransactionViewModel.SIZE];
    public static final byte[] NULL_ADDRESS_HASH_BYTES = new byte[ADDRESS_SIZE];
    public static final byte[] NULL_TAG_HASH_BYTES = new byte[TAG_SIZE];

    private static final int MIN_WEIGHT_MAGNITUDE = 13;

    public static final AtomicLong receivedTransactionCount = new AtomicLong(0);
    public final byte[] bytes = new byte[TransactionViewModel.SIZE];

    private int[] trits;
    public int weightMagnitude;

    public static TransactionViewModel fromHash(final byte[] hash) throws Exception {
        Transaction transaction = ((Transaction) Tangle.instance().load(Transaction.class, hash).get());
        return new TransactionViewModel(transaction);
    }
    public static TransactionViewModel fromHash(final int[] hash) throws Exception{
        return fromHash(Converter.bytes(hash));
    }

    public static TransactionViewModel fromHash(final Hash hash) throws Exception {
        return TransactionViewModel.fromHash(hash.bytes());
    }

    public static boolean mightExist(byte[] hash) throws ExecutionException, InterruptedException {
        return Tangle.instance().maybeHas(Transaction.class, hash).get();
    }

    public TransactionViewModel(final Transaction transaction) {
        if(transaction == null) {
            this.transaction = new Transaction();
            this.transaction.hash = new byte[Hash.SIZE_IN_BYTES];
        } else {
            this.transaction = transaction;
        }
        populateTrits();
        System.arraycopy(Converter.bytes(trits), 0, bytes, 0, BYTES_SIZE);
    }

    private void populateTrits() {
        int[] partialTrits = null;
        this.trits = new int[TRINARY_SIZE];
        Converter.copyTrits(transaction.currentIndex, trits, CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
        Converter.copyTrits(transaction.lastIndex, trits, LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE);
        Converter.copyTrits(transaction.value, trits, VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE);
        Converter.copyTrits(transaction.timestamp.value, trits, TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_SIZE);

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
        if(transaction.address.bytes == null) {
            transaction.address.bytes = new byte[Hash.SIZE_IN_BYTES];
        }
        Converter.getTrits(transaction.address.bytes, partialTrits);
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

    public TransactionViewModel(final int[] trits) {
        transaction = new com.iota.iri.model.Transaction();

        this.trits = trits;
        System.arraycopy(Converter.bytes(trits), 0, this.bytes, 0, BYTES_SIZE);
        //transaction.value = Converter.value(trits);

        final Curl curl = new Curl();
        curl.absorb(trits, 0, TRINARY_SIZE);
        final int[] hashTrits = new int[Curl.HASH_LENGTH];
        curl.squeeze(hashTrits, 0, hashTrits.length);
        transaction.hash = Converter.bytes(hashTrits);//Arrays.copyOf(, HASH_SIZE);

        populateTransaction(trits);

        transaction.type = AbstractStorage.FILLED_SLOT;

        transaction.validity = 0;
        transaction.arrivalTime = 0;
    }


    public TransactionViewModel(final byte[] bytes, final int[] trits, final Curl curl) {
        transaction = new com.iota.iri.model.Transaction();
        //this.transaction.value = Arrays.copyOf(value, BYTES_SIZE);
        System.arraycopy(bytes, 0, this.bytes, 0, BYTES_SIZE);
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

        transaction.type = AbstractStorage.FILLED_SLOT;

        /*
        trunkTransactionPointer = 0;
        branchTransactionPointer = 0;
        */
        transaction.validity = 0;
        transaction.arrivalTime = 0;

    }

    public TransactionViewModel(final byte[] mainBuffer, final long pointer) {
        transaction = new com.iota.iri.model.Transaction();
        transaction.type = mainBuffer[TYPE_OFFSET];
        System.arraycopy(mainBuffer, HASH_OFFSET, this.transaction.hash = new byte[HASH_SIZE], 0, HASH_SIZE);

        //System.arraycopy(mainBuffer, BYTES_OFFSET, this.transaction.value, 0, BYTES_SIZE);
        System.arraycopy(mainBuffer, BYTES_OFFSET, this.bytes, 0, BYTES_SIZE);

        System.arraycopy(mainBuffer, ADDRESS_OFFSET, transaction.address.bytes = new byte[ADDRESS_SIZE], 0, ADDRESS_SIZE);
        transaction.value = AbstractStorage.value(mainBuffer, VALUE_OFFSET);
        System.arraycopy(mainBuffer, TAG_OFFSET, transaction.tag.bytes = new byte[TAG_SIZE], 0, TAG_SIZE);
        transaction.currentIndex = AbstractStorage.value(mainBuffer, CURRENT_INDEX_OFFSET);
        transaction.lastIndex = AbstractStorage.value(mainBuffer, LAST_INDEX_OFFSET);
        System.arraycopy(mainBuffer, TRUNK_TRANSACTION_OFFSET, transaction.trunk.hash = new byte[TRUNK_TRANSACTION_SIZE], 0, TRUNK_TRANSACTION_SIZE);
        System.arraycopy(mainBuffer, BRANCH_TRANSACTION_OFFSET, transaction.branch.hash = new byte[BRANCH_TRANSACTION_SIZE], 0, BRANCH_TRANSACTION_SIZE);

        /* Don't need this anymore. just get it from the db.
        trunkTransactionPointer = StorageTransactions.instance().transactionPointer(transaction.trunk);
        if (trunkTransactionPointer < 0) {
            trunkTransactionPointer = -trunkTransactionPointer;
        }
        branchTransactionPointer = StorageTransactions.instance().transactionPointer(transaction.branch);
        if (branchTransactionPointer < 0) {
            branchTransactionPointer = -branchTransactionPointer;
        }
        */

        transaction.validity = mainBuffer[VALIDITY_OFFSET];

        transaction.arrivalTime = AbstractStorage.value(mainBuffer, ARRIVAL_TIME_OFFSET);

    }

    private final void populateTransaction(int[] trits) {
        transaction.currentIndex = Converter.longValue(trits, CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
        transaction.lastIndex = Converter.longValue(trits, LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE);
        transaction.value = Converter.longValue(trits, VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE);
        transaction.signature = Converter.bytes(trits, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
        transaction.address.bytes = Converter.bytes(trits, ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE);
        System.arraycopy(Converter.bytes(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, transaction.tag.bytes = new byte[TAG_SIZE], 0, TAG_SIZE);
        transaction.bundle.hash = Converter.bytes(trits, BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE);
        transaction.trunk.hash = Converter.bytes(trits, TRUNK_TRANSACTION_TRINARY_OFFSET, TRUNK_TRANSACTION_TRINARY_SIZE);
        transaction.branch.hash = Converter.bytes(trits, BRANCH_TRANSACTION_TRINARY_OFFSET, BRANCH_TRANSACTION_TRINARY_SIZE);
        transaction.timestamp.value = Converter.longValue(trits, TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_SIZE);
        transaction.nonce = Converter.bytes(trits, NONCE_TRINARY_OFFSET, NONCE_TRINARY_SIZE);
    }

    public void update(String item) throws Exception {
        Tangle.instance().update(transaction, item, Transaction.class.getDeclaredField(item).get(transaction));
    }

    public static void setAnalyzed(byte[] hash, boolean analyzed) {
        Transaction transaction = new Transaction();
        transaction.hash = hash;
        Tangle.instance().update(transaction, "analyzed", analyzed);
    }

    private TransactionViewModel getTransaction(byte[] hash) throws ExecutionException, InterruptedException {
        Transaction transactionToLoad = new Transaction();
        transactionToLoad = ((Transaction) Tangle.instance().load(Transaction.class, hash).get());
        //Transaction trunk = ((Transaction) Arrays.stream(Tangle.instance().query(Transaction.class, "", transaction.trunk, BUNDLE_SIZE).get()).findAny().orElse(null));
        return new TransactionViewModel(transactionToLoad);
    }

    public TransactionViewModel getBranchTransaction() throws ExecutionException, InterruptedException {
        return getTransaction(transaction.branch.hash);
    }

    public TransactionViewModel getTrunkTransaction() throws ExecutionException, InterruptedException {
        return getTransaction(transaction.trunk.hash);
    }

    public synchronized int[] trits() {

        if (trits == null) {
            trits = new int[TRINARY_SIZE];
            //Converter.getTrits(this.transaction.bytes, trits);
            Converter.getTrits(this.bytes, trits);
        }
        return trits;
    }

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

    public boolean store() throws Exception {
        boolean exists = Tangle.instance().save(transaction).get();
        update(exists);
        return exists;
    }

    private void update (boolean exists) throws Exception {
        Hash[] approvers = getApprovers();
        updateApprovers(approvers);
        updateTips(approvers);
        updateReceivedTransactionCount(exists);
    }

    public void updateApprovers(Hash[] approvers) throws Exception {
        for(Hash approver: approvers) {
            Transaction approvingTransaction = (Transaction) Tangle.instance().load(Transaction.class, approver.bytes()).get();
            approvingTransaction.type = AbstractStorage.FILLED_SLOT;
            new TransactionViewModel(approvingTransaction).store();
        }
    }

    public void updateTips(Hash[] approvers) throws ExecutionException, InterruptedException {
        if(approvers.length == 0) {
            TipsViewModel.addTipHash(transaction.hash);
        } else {
            TipsViewModel.removeTipHash(transaction.hash);
        }
    }

    public void updateReceivedTransactionCount(boolean exists) {
        if(!exists) {
            receivedTransactionCount.incrementAndGet();
        }
    }


    /*
    public TransactionViewModel[] getBundleTransactions() throws ExecutionException, InterruptedException {
        Transaction[] transactionModels = Arrays.stream(Tangle.instance().query(Transaction.class, "bundle", transaction.bundle, BUNDLE_SIZE).get())
                .toArray(com.iota.iri.model.Transaction[]::new);
        TransactionViewModel[] transactionViewModels = Arrays.stream(transactionModels).map(bundleTransaction -> new TransactionViewModel((com.iota.iri.model.Transaction) bundleTransaction)).toArray(TransactionViewModel[]::new);
                //.get(45, TimeUnit.MILLISECONDS))
        return transactionViewModels;
    }
    */

    public static Hash[] hashesFromQuery(String index, Object value) throws ExecutionException, InterruptedException {
        Hash[] transactionHashes;
        Object[] transactions = Tangle.instance().query(com.iota.iri.model.Transaction.class, index, value, BUNDLE_SIZE).get();
        Transaction[] transactionModels = Arrays.stream(transactions).toArray(com.iota.iri.model.Transaction[]::new);
        transactionHashes = Arrays.stream(transactionModels).map(transaction -> transaction.hash).toArray(Hash[]::new);
        return transactionHashes;
    }

    public static Hash[] approversFromHash(Hash hash) throws Exception {
        return TransactionViewModel.fromHash(hash).getApprovers();
    }

    public static Hash[] fromTag(Hash tag) throws ExecutionException, InterruptedException {
        return hashesFromQuery("tag", tag.bytes());
    }
    public static Hash[] fromBundle(Hash bundle) throws ExecutionException, InterruptedException {
        return hashesFromQuery("bundle", bundle.bytes());
    }
    public static Hash[] fromAddress(Hash address) throws ExecutionException, InterruptedException {
        return hashesFromQuery("address", address.bytes());
    }

    public static int getTransactionAnalyzedFlag(byte[] hash) {
        int analyzedTransactionFlag = 0;
        return analyzedTransactionFlag;
    }


    public Hash[] getApprovers() throws ExecutionException, InterruptedException {
        Approvee self = ((Approvee) Tangle.instance().load(Approvee.class, transaction.hash).get());
        return self.transactions != null? Arrays.stream(self.transactions).map(transaction -> new Hash(transaction.hash)).toArray(Hash[]::new): new Hash[0];
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
        return this.bytes;
    }

    public byte[] getHash() {
        return transaction.hash;
    }

    public AddressViewModel getAddress() {
        return new AddressViewModel(transaction.address.bytes);
    }

    public TagViewModel getTag() {
        return new TagViewModel(transaction.tag);
    }

    public byte[] getBundleHash() {
        if(this.transaction.bundle == null) {
            this.transaction.bundle = new Bundle();
            this.transaction.bundle.hash = NULL_TRANSACTION_HASH_BYTES;
        }
        return transaction.bundle.hash;
    }

    public byte[] getTrunkTransactionHash() {
        if(this.transaction.trunk == null) {
            this.transaction.trunk = new Approvee();
            this.transaction.trunk.hash = NULL_TRANSACTION_HASH_BYTES;
        }
        return transaction.trunk.hash;
    }

    public byte[] getBranchTransactionHash() {
        if(this.transaction.branch == null) {
            this.transaction.branch = new Approvee();
            this.transaction.branch.hash = NULL_TRANSACTION_HASH_BYTES;
        }
        return transaction.branch.hash;
    }

    public final long getValue() {
        return transaction.value;
    }

    public long value() {
        return transaction.value;
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
        return transaction.currentIndex;
    }

    public long getLastIndex() {
        return transaction.lastIndex;
    }

}
