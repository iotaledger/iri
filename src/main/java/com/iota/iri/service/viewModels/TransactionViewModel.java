package com.iota.iri.service.viewModels;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.*;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.utils.Converter;

public class TransactionViewModel {
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

    public static final byte[] NULL_TRANSACTION_HASH_BYTES = new byte[TransactionViewModel.HASH_SIZE];
    public static final byte[] NULL_TRANSACTION_BYTES = new byte[TransactionViewModel.SIZE];

    private static final int MIN_WEIGHT_MAGNITUDE = 13;

    public final int type;

    //public final byte[] hash;
    //public final byte[] bytes; // stores entire tx bytes. message occupies always first part named 'signatureMessageFragment'
    //public final byte[] address;

    //public final long value; // <0 spending transaction, >=0 deposit transaction / message


    //public final byte[] tag; // milestone index only for milestone tx. Otherwise, arbitrary up to the tx issuer.
    //public final long currentIndex; // index of tx in the bundle
    //public final long lastIndex; // lastIndex is curIndex of the last tx from the same bundle

    //public final byte[] bundle;
    //public final byte[] trunkTransaction;
    //public final byte[] branchTransaction;

    public long trunkTransactionPointer;
    public long branchTransactionPointer;
    //private final int getValidity;

    private int[] trits;
    public int weightMagnitude;

    public static TransactionViewModel fromHash(final byte[] hash) {
        com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
        try {
            transaction = ((Transaction) Tangle.instance().load(Transaction.class, hash).get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return new TransactionViewModel(transaction);
    }
    public static TransactionViewModel fromHash(final int[] hash) {
        return fromHash(Converter.bytes(hash));
    }

    public static TransactionViewModel fromHash(final Hash hash) {
        return TransactionViewModel.fromHash(hash.bytes());
    }
    public TransactionViewModel(final com.iota.iri.model.Transaction transaction) {
        if(transaction == null) {
            this.transaction = new Transaction();
            this.transaction.bytes = NULL_TRANSACTION_BYTES;
            this.transaction.hash = NULL_TRANSACTION_HASH_BYTES;
        } else {
            this.transaction = transaction;
        }
        type = AbstractStorage.FILLED_SLOT;
    }

    public TransactionViewModel(final int[] trits) {
        transaction = new com.iota.iri.model.Transaction();

        this.trits = trits;
        transaction.bytes = Converter.bytes(trits);

        final Curl curl = new Curl();
        curl.absorb(trits, 0, TRINARY_SIZE);
        final int[] hashTrits = new int[Curl.HASH_LENGTH];
        curl.squeeze(hashTrits, 0, hashTrits.length);
        transaction.hash = Arrays.copyOf(Converter.bytes(hashTrits), HASH_SIZE);

        populateTransaction(trits);

        type = AbstractStorage.FILLED_SLOT;

        transaction.validity = 0;
        transaction.arrivalTime = 0;
    }


    public TransactionViewModel(final byte[] bytes, final int[] trits, final Curl curl) {
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

        type = AbstractStorage.FILLED_SLOT;

        /*
        trunkTransactionPointer = 0;
        branchTransactionPointer = 0;
        */
        transaction.validity = 0;
        transaction.arrivalTime = 0;

    }

    public TransactionViewModel(final byte[] mainBuffer, final long pointer) {
        transaction = new com.iota.iri.model.Transaction();
        type = mainBuffer[TYPE_OFFSET];
        System.arraycopy(mainBuffer, HASH_OFFSET, this.transaction.hash = new byte[HASH_SIZE], 0, HASH_SIZE);

        System.arraycopy(mainBuffer, BYTES_OFFSET, this.transaction.bytes, 0, BYTES_SIZE);

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
        transaction.address = new Address();
        transaction.address.bytes = Converter.bytes(trits, ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE);
        transaction.tag = new Tag();
        System.arraycopy(Converter.bytes(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, transaction.tag.bytes = new byte[TAG_SIZE], 0, TAG_SIZE);
        transaction.bundle = new Bundle();
        System.arraycopy(Converter.bytes(trits, BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE), 0, transaction.bundle.hash = new byte[BUNDLE_SIZE], 0, BUNDLE_SIZE);
        transaction.trunk = new Approvee();
        System.arraycopy(Converter.bytes(trits, TRUNK_TRANSACTION_TRINARY_OFFSET, TRUNK_TRANSACTION_TRINARY_SIZE), 0, transaction.trunk.hash = new byte[TRUNK_TRANSACTION_SIZE], 0, TRUNK_TRANSACTION_SIZE);
        transaction.branch = new Approvee();
        System.arraycopy(Converter.bytes(trits, BRANCH_TRANSACTION_TRINARY_OFFSET, BRANCH_TRANSACTION_TRINARY_SIZE), 0, transaction.branch.hash = new byte[BRANCH_TRANSACTION_SIZE], 0, BRANCH_TRANSACTION_SIZE);
    }

    public void update(String item) throws Exception {
        Tangle.instance().update(transaction, item, Transaction.class.getDeclaredField(item).get(transaction));
    }

    public static void setAnalyzed(byte[] hash, boolean analyzed) {
        Transaction transaction = new Transaction();
        transaction.hash = hash;
        Tangle.instance().update(transaction, "analyzed", analyzed);
        Tangle.instance().update(transaction, "nonAnalyzed", !analyzed);
    }

    private TransactionViewModel getTransaction(byte[] hash) {
        Transaction transactionToLoad = new Transaction();
        try {
            transactionToLoad = ((Transaction) Tangle.instance().load(Transaction.class, hash).get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        //Transaction trunk = ((Transaction) Arrays.stream(Tangle.instance().query(Transaction.class, "", transaction.trunk, BUNDLE_SIZE).get()).findAny().orElse(null));
        return new TransactionViewModel(transactionToLoad);
    }

    public TransactionViewModel getBranchTransaction() {
        return getTransaction(transaction.branch.hash);
    }

    public TransactionViewModel getTrunkTransaction() {
        return getTransaction(transaction.trunk.hash);
    }

    public synchronized int[] trits() {

        if (trits == null) {
            trits = new int[TRINARY_SIZE];
            Converter.getTrits(this.transaction.bytes, trits);
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
            System.arraycopy(Converter.bytes(trits, TAG_TRINARY_OFFSET, TAG_TRINARY_SIZE), 0, mainBuffer, TAG_OFFSET, TAG_SIZE);
            AbstractStorage.setValue(mainBuffer, CURRENT_INDEX_OFFSET, transactionViewModel.getCurrentIndex());
            AbstractStorage.setValue(mainBuffer, LAST_INDEX_OFFSET, transactionViewModel.getLastIndex());
            System.arraycopy(Converter.bytes(trits, BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE), 0, mainBuffer, BUNDLE_OFFSET, BUNDLE_SIZE);
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

    public Future<Boolean> store() {
        return Tangle.instance().save(transaction);
    }

    public TransactionViewModel[] getBundleTransactions() {
        Tangle accessor = Tangle.instance();
        Future<Object[]> transactionFuture = accessor.query(com.iota.iri.model.Transaction.class, "bundle", transaction.bundle, BUNDLE_SIZE);
        com.iota.iri.model.Transaction[] transactionModels = new com.iota.iri.model.Transaction[0];
        try {
            transactionModels = Arrays.stream(transactionFuture.get()).toArray(com.iota.iri.model.Transaction[]::new);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        TransactionViewModel[] transactionViewModels = Arrays.stream(transactionModels).map(bundleTransaction -> new TransactionViewModel((com.iota.iri.model.Transaction) bundleTransaction)).toArray(TransactionViewModel[]::new);
                //.get(45, TimeUnit.MILLISECONDS))
        return transactionViewModels;
    }

    public static Hash[] hashesFromQuery(String index, Object value) {
        Hash[] transactionHashes;
        try {
            Tangle accessor = Tangle.instance();
            Future<Object[]> tipTxs = accessor.query(com.iota.iri.model.Transaction.class, index, value, BUNDLE_SIZE);
            Transaction[] transactionModels = Arrays.stream(tipTxs.get()).toArray(com.iota.iri.model.Transaction[]::new);
            transactionHashes = Arrays.stream(transactionModels).map(transaction -> transaction.hash).toArray(Hash[]::new);
        } catch (Exception e) {
            e.printStackTrace();
            transactionHashes = new Hash[0];
        }
        return transactionHashes;
    }

    public static Hash[] fromApprovers(Hash hash) {
        return TransactionViewModel.fromHash(hash).getApprovers();
    }

    public static Hash[] fromTag(Hash tag) {
        return hashesFromQuery("tag", tag.bytes());
    }
    public static Hash[] fromBundle(Hash bundle) {
        return hashesFromQuery("bundle", bundle.bytes());
    }
    public static Hash[] fromAddress(Hash address) {
        return hashesFromQuery("address", address.bytes());
    }

    public static int getTransactionAnalyzedFlag(byte[] hash) {
        int analyzedTransactionFlag = 0;
        return analyzedTransactionFlag;
    }

    public static Hash[] getTipHashes() {
        return hashesFromQuery("isTip", true);
    }

    public Hash[] getApprovers() {
        Hash[] approvers;
        Tangle accessor = Tangle.instance();
        try {
            Approvee self = ((Approvee) accessor.load(Approvee.class, transaction.hash).get());
            approvers = Arrays.stream(self.transactions).map(transaction -> new Hash(transaction.hash)).toArray(Hash[]::new);
        } catch (Exception e) {
            e.printStackTrace();
            approvers = new Hash[0];
        }
        return approvers;
    }


    public void setArrivalTime(long time) {
        transaction.arrivalTime = time;
    }

    public long getArrivalTime() {
        return transaction.arrivalTime;
    }
    public byte[] getBytes() {
        return transaction.bytes;
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

    public void setValidity(int validity, boolean update) {
        transaction.validity = validity;
        if(update) {
            try {
                update("validity");
            } catch (Exception e) {
                e.printStackTrace();
            }
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
