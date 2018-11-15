package com.iota.iri.controllers;

import com.iota.iri.model.*;
import com.iota.iri.model.persistables.Address;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.model.persistables.ObsoleteTag;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;

import java.util.*;

/**
 * Controller class for transaction objects. A {@code TransactionViewModel} stores a controller for each component of
 * the transaction within it.
 */

public class TransactionViewModel {

    /**Base transaction object to be controlled*/
    private final Transaction transaction;

    /**Length of a transaction object in trytes*/
    public static final int SIZE = 1604;
    private static final int TAG_SIZE_IN_BYTES = 17; // = ceil(81 TRITS / 5 TRITS_PER_BYTE)

    /**Total supply of IOTA available in the network. Used for ensuring a balanced ledger state and bundle balances*/
    public static final long SUPPLY = 2779530283277761L; // = (3^33 - 1) / 2

    /**The predefined trit offset position for the varying components of a transaction object*/
    public static final int SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET = 0, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE = 6561;
    public static final int ADDRESS_TRINARY_OFFSET = SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE, ADDRESS_TRINARY_SIZE = 243;
    public static final int VALUE_TRINARY_OFFSET = ADDRESS_TRINARY_OFFSET + ADDRESS_TRINARY_SIZE, VALUE_TRINARY_SIZE = 81, VALUE_USABLE_TRINARY_SIZE = 33;
    public static final int OBSOLETE_TAG_TRINARY_OFFSET = VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE, OBSOLETE_TAG_TRINARY_SIZE = 81;
    public static final int TIMESTAMP_TRINARY_OFFSET = OBSOLETE_TAG_TRINARY_OFFSET + OBSOLETE_TAG_TRINARY_SIZE, TIMESTAMP_TRINARY_SIZE = 27;
    public static final int CURRENT_INDEX_TRINARY_OFFSET = TIMESTAMP_TRINARY_OFFSET + TIMESTAMP_TRINARY_SIZE, CURRENT_INDEX_TRINARY_SIZE = 27;
    public static final int LAST_INDEX_TRINARY_OFFSET = CURRENT_INDEX_TRINARY_OFFSET + CURRENT_INDEX_TRINARY_SIZE, LAST_INDEX_TRINARY_SIZE = 27;
    public static final int BUNDLE_TRINARY_OFFSET = LAST_INDEX_TRINARY_OFFSET + LAST_INDEX_TRINARY_SIZE, BUNDLE_TRINARY_SIZE = 243;
    public static final int TRUNK_TRANSACTION_TRINARY_OFFSET = BUNDLE_TRINARY_OFFSET + BUNDLE_TRINARY_SIZE, TRUNK_TRANSACTION_TRINARY_SIZE = 243;
    public static final int BRANCH_TRANSACTION_TRINARY_OFFSET = TRUNK_TRANSACTION_TRINARY_OFFSET + TRUNK_TRANSACTION_TRINARY_SIZE, BRANCH_TRANSACTION_TRINARY_SIZE = 243;

    public static final int TAG_TRINARY_OFFSET = BRANCH_TRANSACTION_TRINARY_OFFSET + BRANCH_TRANSACTION_TRINARY_SIZE, TAG_TRINARY_SIZE = 81;
    public static final int ATTACHMENT_TIMESTAMP_TRINARY_OFFSET = TAG_TRINARY_OFFSET + TAG_TRINARY_SIZE, ATTACHMENT_TIMESTAMP_TRINARY_SIZE = 27;
    public static final int ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET = ATTACHMENT_TIMESTAMP_TRINARY_OFFSET + ATTACHMENT_TIMESTAMP_TRINARY_SIZE, ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE = 27;
    public static final int ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET = ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET + ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE, ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE = 27;
    private static final int NONCE_TRINARY_OFFSET = ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET + ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE, NONCE_TRINARY_SIZE = 81;

    public static final int TRINARY_SIZE = NONCE_TRINARY_OFFSET + NONCE_TRINARY_SIZE;
    public static final int TRYTES_SIZE = TRINARY_SIZE / 3;

    public static final int ESSENCE_TRINARY_OFFSET = ADDRESS_TRINARY_OFFSET, ESSENCE_TRINARY_SIZE = ADDRESS_TRINARY_SIZE + VALUE_TRINARY_SIZE + OBSOLETE_TAG_TRINARY_SIZE + TIMESTAMP_TRINARY_SIZE + CURRENT_INDEX_TRINARY_SIZE + LAST_INDEX_TRINARY_SIZE;

    /**Stores the controllers for the transaction components here*/
    private AddressViewModel address;
    private ApproveeViewModel approovers;
    private TransactionViewModel trunk;
    private TransactionViewModel branch;
    private final Hash hash;

    /**Transaction Types*/
    public final static int GROUP = 0; // transactions GROUP means that's it's a non-leaf node (leafs store transaction value)
    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    private byte[] trits;
    public int weightMagnitude;

    /**
     * Populates the meta data of the controller. If the controller hash identifier is <tt>null</tt>, it will
     * return with no response. If the transaction object has not been parsed, and the controller type is
     * <tt>FILLED_SLOT</tt>, the saved metadata batch will be saved to the input tangle.
     *
     * @param tangle The tangle instance the transaction resides in.
     * @param transactionViewModel The controller whose Metadata is to be filled.
     * @throws Exception In the event the method cannot be completed, an exception will be thrown.
     */
    public static void fillMetadata(Tangle tangle, TransactionViewModel transactionViewModel) throws Exception {
        if (Hash.NULL_HASH.equals(transactionViewModel.getHash())) {
            return;
        }
        if(transactionViewModel.getType() == FILLED_SLOT && !transactionViewModel.transaction.parsed) {
            tangle.saveBatch(transactionViewModel.getMetadataSaveBatch());
        }
    }

    /**
     * Creates a new controller from a referenced transaction. The given byte array is converted to a hash
     * identifier, and the new controller is created from it, provided its hash identifier exists in the database.
     *
     * @param tangle The tangle instance reference for the database
     * @param hash The hash byte array to be converted to a hash identifier and searched for
     * @return The transaction controller with its Metadata filled in.
     * @throws Exception If no transaction can be found 
     */
    public static TransactionViewModel find(Tangle tangle, byte[] hash) throws Exception {
        TransactionViewModel transactionViewModel = new TransactionViewModel((Transaction) tangle.find(Transaction.class, hash), HashFactory.TRANSACTION.create(hash));
        fillMetadata(tangle, transactionViewModel);
        return transactionViewModel;
    }

    /**
     * Creates a new controller from a given hash identifier, provided it exists in the database.
     *
     * @param tangle The tangle instance reference for the database
     * @param hash The Hash identifier to search for
     * @return The transaction controller with its Metadata filled in.
     * @throws Exception If no transaction can be found
     */
    public static TransactionViewModel fromHash(Tangle tangle, final Hash hash) throws Exception {
        TransactionViewModel transactionViewModel = new TransactionViewModel((Transaction) tangle.load(Transaction.class, hash), hash);
        fillMetadata(tangle, transactionViewModel);
        return transactionViewModel;
    }

    public static boolean mightExist(Tangle tangle, Hash hash) throws Exception {
        return tangle.maybeHas(Transaction.class, hash);
    }

    /**
     * Constructor for a controller interface. This controller is used to interact with a transaction model.
     *
     * @param transaction Transaction object that the interface will be created for
     * @param hash
     */
    public TransactionViewModel(final Transaction transaction, Hash hash) {
        this.transaction = transaction == null || transaction.bytes == null ? new Transaction(): transaction;
        this.hash = hash == null? Hash.NULL_HASH: hash;
        weightMagnitude = this.hash.trailingZeros();
    }

    public TransactionViewModel(final byte[] trits, Hash hash) {
        transaction = new Transaction();

        if(trits.length == 8019) {
            this.trits = new byte[trits.length];
            System.arraycopy(trits, 0, this.trits, 0, trits.length);
            transaction.bytes = Converter.allocateBytesForTrits(trits.length);
            Converter.bytes(trits, 0, transaction.bytes, 0, trits.length);

            transaction.validity = 0;
            transaction.arrivalTime = 0;
        } else {
            transaction.bytes = new byte[SIZE];
            System.arraycopy(trits, 0, transaction.bytes, 0, SIZE);
        }

        this.hash = hash;
        weightMagnitude = this.hash.trailingZeros();
        transaction.type = FILLED_SLOT;
    }

    /**
     * Returns the total number of transactions stored in a database. If no transaction objects are found,
     * an exception will be thrown.
     *
     * @param tangle The tangle reference for the database.
     * @return The integer count of total transactions in the database
     * @throws Exception Thrown if no transactions found
     */
    public static int getNumberOfStoredTransactions(Tangle tangle) throws Exception {
        return tangle.getCount(Transaction.class).intValue();
    }

    public boolean update(Tangle tangle, String item) throws Exception {
        getAddressHash();
        getTrunkTransactionHash();
        getBranchTransactionHash();
        getBundleHash();
        getTagValue();
        getObsoleteTagValue();
        setAttachmentData();
        setMetadata();
        if(hash.equals(Hash.NULL_HASH)) {
            return false;
        }
        return tangle.update(transaction, hash, item);
    }

    /**
     * Retrieves the <tt>Branch Transaction</tt> controller. If the controller doesn't already exist, a new one is
     * created from the <tt>Branch Transaction</tt> hash identifier. If nothing is found, an exception will be thrown.
     *
     * @param tangle The tangle reference for the database.
     * @return The <tt>Branch Transaction</tt> controller
     * @throws Exception Thrown if no branch is found when creating a <tt>Transaction</tt> controller
     */
    public TransactionViewModel getBranchTransaction(Tangle tangle) throws Exception {
        if(branch == null) {
            branch = TransactionViewModel.fromHash(tangle, getBranchTransactionHash());
        }
        return branch;
    }

    /**
     * Retrieves the <tt>Trunk Transaction</tt> controller. If the controller doesn't already exist, a new one is
     * created from the <tt>Trunk Transaction</tt> hash identifier. If nothing is found, an exception will be thrown.
     *
     * @param tangle The tangle reference for the database.
     * @return The <tt>Trunk Transaction</tt> controller
     * @throws Exception Thrown if no trunk is found when creating a <tt>Transaction</tt> controller
     */
    public TransactionViewModel getTrunkTransaction(Tangle tangle) throws Exception {
        if(trunk == null) {
            trunk = TransactionViewModel.fromHash(tangle, getTrunkTransactionHash());
        }
        return trunk;
    }

    /**
     * Converts the given byte array to the a new trit array of length {@value TRINARY_SIZE}.
     * @param transactionBytes The byte array to be converted to trits
     * @return The trit conversion of the byte array
     */
    public static byte[] trits(byte[] transactionBytes) {
        byte[] trits;
        trits = new byte[TRINARY_SIZE];
        if(transactionBytes != null) {
            Converter.getTrits(transactionBytes, trits);
        }
        return trits;
    }

    /**
     * @return The trits stored in the controller. If the trits aren't available in the controller, the
     * trits are generated from the transaction object and stored in the controller trits variable.
     */
    public synchronized byte[] trits() {
        return (trits == null) ? (trits = trits(transaction.bytes)) : trits;
    }

    /**
     *
     * @param tangle
     * @throws Exception
     */
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Transaction.class, hash);
    }

    public List<Pair<Indexable, Persistable>> getMetadataSaveBatch() throws Exception {
        List<Pair<Indexable, Persistable>> hashesList = new ArrayList<>();
        hashesList.add(new Pair<>(getAddressHash(), new Address(hash)));
        hashesList.add(new Pair<>(getBundleHash(), new Bundle(hash)));
        hashesList.add(new Pair<>(getBranchTransactionHash(), new Approvee(hash)));
        hashesList.add(new Pair<>(getTrunkTransactionHash(), new Approvee(hash)));
        hashesList.add(new Pair<>(getObsoleteTagValue(), new ObsoleteTag(hash)));
        hashesList.add(new Pair<>(getTagValue(), new Tag(hash)));
        setAttachmentData();
        setMetadata();
        return hashesList;
    }

    public List<Pair<Indexable, Persistable>> getSaveBatch() throws Exception {
        List<Pair<Indexable, Persistable>> hashesList = new ArrayList<>();
        hashesList.addAll(getMetadataSaveBatch());
        getBytes();
        hashesList.add(new Pair<>(hash, transaction));
        return hashesList;
    }


    public static TransactionViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> transactionPair = tangle.getFirst(Transaction.class, Hash.class);
        if(transactionPair != null && transactionPair.hi != null) {
            return new TransactionViewModel((Transaction) transactionPair.hi, (Hash) transactionPair.low);
        }
        return null;
    }

    public TransactionViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> transactionPair = tangle.next(Transaction.class, hash);
        if(transactionPair != null && transactionPair.hi != null) {
            return new TransactionViewModel((Transaction) transactionPair.hi, (Hash) transactionPair.low);
        }
        return null;
    }

    public boolean store(Tangle tangle) throws Exception {
        if (hash.equals(Hash.NULL_HASH) || exists(tangle, hash)) {
            return false;
        }

        List<Pair<Indexable, Persistable>> batch = getSaveBatch();
        if (exists(tangle, hash)) {
            return false;
        }
        return tangle.saveBatch(batch);
    }

    public ApproveeViewModel getApprovers(Tangle tangle) throws Exception {
        if(approovers == null) {
            approovers = ApproveeViewModel.load(tangle, hash);
        }
        return approovers;
    }

    /**
     * This is a getter for the type of a transaction controller. The type can be one of 3:
     * <ul>
     *     <li>PREFILLED_SLOT: 1</li>
     *     <li>FILLED_SLOT: -1</li>
     *     <li>GROUP: 0</li>
     * </ul>
     *
     * @return The current type of the transaction.
     */
    public final int getType() {
        return transaction.type;
    }

    /**
     * This is a setter for the transaction's arrival time.
     * @param time The time to be set in the transaction
     */
    public void setArrivalTime(long time) {
        transaction.arrivalTime = time;
    }

    /**@return The transaction arrival time*/
    public long getArrivalTime() {
        return transaction.arrivalTime;
    }

    /**
     * This is a getter for the stored transaction bytes. If the transaction bytes are null, a byte array is
     * created and stored from the stored trits. If the trits are also null, then a null byte array is returned.
     *
     * @return The stored transaction byte array
     */
    public byte[] getBytes() {
        if(transaction.bytes == null || transaction.bytes.length != SIZE) {
            transaction.bytes = new byte[SIZE];
            if(trits != null) {
                Converter.bytes(trits(), 0, transaction.bytes, 0, trits().length);
            }
        }
        return transaction.bytes;
    }

    /**@return The transaction hash identifier*/
    public Hash getHash() {
        return hash;
    }

    /**
     * This is a getter for the <tt>Address</tt> controller associated with this transaction.
     * @see AddressViewModel
     *
     * @param tangle The tangle reference for the database.
     * @return The controller for the <tt>Address</tt> of the transaction.
     * @throws Exception If the address cannot be found in the database, an exception is thrown.
     */
    public AddressViewModel getAddress(Tangle tangle) throws Exception {
        if(address == null) {
            address = AddressViewModel.load(tangle, getAddressHash());
        }
        return address;
    }

    /**
     * This is a getter for the <tt>Tag</tt> controller associated with this transaction.
     * @see TagViewModel
     *
     * @param tangle The tangle reference for the database.
     * @return The controller for the <tt>Tag</tt> of the transaction.
     * @throws Exception If the tag cannot be found in the database, an exception is thrown.
     */
    public TagViewModel getTag(Tangle tangle) throws Exception {
        return TagViewModel.load(tangle, getTagValue());
    }

    /**
     * This is a getter for the <tt>Address</tt> hash identifier of a transaction. If the hash is null, a new one is
     * created from the stored transaction trits.
     *
     * @return The <tt>Address</tt> hash identifier.
     */
    public Hash getAddressHash() {
        if(transaction.address == null) {
            transaction.address = HashFactory.ADDRESS.create(trits(), ADDRESS_TRINARY_OFFSET);
        }
        return transaction.address;
    }

    /**
     * This is a getter for the <tt>Obsolete Tag</tt> hash identifier of a transaction. If the hash is null, a new
     * one is created from the stored transaction trits.
     *
     * @return The <tt>Obsolete Tag</tt> hash identifier.
     */
    public Hash getObsoleteTagValue() {
        if(transaction.obsoleteTag == null) {
            byte[] tagBytes = Converter.allocateBytesForTrits(OBSOLETE_TAG_TRINARY_SIZE);
            Converter.bytes(trits(), OBSOLETE_TAG_TRINARY_OFFSET, tagBytes, 0, OBSOLETE_TAG_TRINARY_SIZE);

            transaction.obsoleteTag = HashFactory.OBSOLETETAG.create(tagBytes, 0, TAG_SIZE_IN_BYTES);
        }
        return transaction.obsoleteTag;
    }

    /**
     * This is a getter for the <tt>Bundle</tt> hash identifier of a transaction. If the hash is null, a new one is
     * created from the stored transaction trits.
     *
     * @return The <tt>Bundle</tt> hash identifier.
     */
    public Hash getBundleHash() {
        if(transaction.bundle == null) {
            transaction.bundle = HashFactory.BUNDLE.create(trits(), BUNDLE_TRINARY_OFFSET);
        }
        return transaction.bundle;
    }

    /**
     * This is a getter for the <tt>Trunk Transaction</tt> hash identifier of a transaction. If the hash is null,
     * a new one is created from the stored transaction trits.
     *
     * @return The <tt>Trunk Transaction</tt> hash identifier.
     */
    public Hash getTrunkTransactionHash() {
        if(transaction.trunk == null) {
            transaction.trunk = HashFactory.TRANSACTION.create(trits(), TRUNK_TRANSACTION_TRINARY_OFFSET);
        }
        return transaction.trunk;
    }

    /**
     * This is a getter for the <tt>Branch Transaction</tt> hash identifier of a transaction. If the hash is null,
     * a new one is created from the stored transaction trits.
     *
     * @return The <tt>Branch Transaction</tt> hash identifier.
     */
    public Hash getBranchTransactionHash() {
        if(transaction.branch == null) {
            transaction.branch = HashFactory.TRANSACTION.create(trits(), BRANCH_TRANSACTION_TRINARY_OFFSET);
        }
        return transaction.branch;
    }

    public Hash getTagValue() {
        if(transaction.tag == null) {
            byte[] tagBytes = Converter.allocateBytesForTrits(TAG_TRINARY_SIZE);
            Converter.bytes(trits(), TAG_TRINARY_OFFSET, tagBytes, 0, TAG_TRINARY_SIZE);
            transaction.tag = HashFactory.TAG.create(tagBytes, 0, TAG_SIZE_IN_BYTES);
        }
        return transaction.tag;
    }

    /**
     * Getter for the <tt>Attachment Timestamp</tt> of a transaction.
     *
     * The attachme
     *
     * @return The <tt>Attachment Timestamp</tt>.
     */
    public long getAttachmentTimestamp() { return transaction.attachmentTimestamp; }

    /**
     * Getter for the <tt>Attachment Timestamp Lower Bound</tt> of a transaction.
     *
     * @return The <tt>Attachment Timestamp Lower Bound</tt>.
     */

    public long getAttachmentTimestampLowerBound() {
        return transaction.attachmentTimestampLowerBound;
    }

    /**
     * Getter for the <tt>Attachment Timestamp Upper Bound</tt> of a transaction.
     *
     * @return The <tt>Attachment Timestamp Upper Bound</tt>.
     */
    public long getAttachmentTimestampUpperBound() {
        return transaction.attachmentTimestampUpperBound;
    }


    public long value() {
        return transaction.value;
    }

    public void setValidity(Tangle tangle, int validity) throws Exception {
        if(transaction.validity != validity) {
            transaction.validity = validity;
            update(tangle, "validity");
        }
    }

    public int getValidity() {
        return transaction.validity;
    }

    public long getCurrentIndex() {
        return transaction.currentIndex;
    }

    public byte[] getSignature() {
        return Arrays.copyOfRange(trits(), SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
    }

    public long getTimestamp() {
        return transaction.timestamp;
    }

    public byte[] getNonce() {
        byte[] nonce = Converter.allocateBytesForTrits(NONCE_TRINARY_SIZE);
        Converter.bytes(trits(), NONCE_TRINARY_OFFSET, nonce, 0, trits().length);
        return nonce;
    }

    public long lastIndex() {
        return transaction.lastIndex;
    }

    public void setAttachmentData() {
        getTagValue();
        transaction.attachmentTimestamp = Converter.longValue(trits(), ATTACHMENT_TIMESTAMP_TRINARY_OFFSET, ATTACHMENT_TIMESTAMP_TRINARY_SIZE);
        transaction.attachmentTimestampLowerBound = Converter.longValue(trits(), ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET, ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE);
        transaction.attachmentTimestampUpperBound = Converter.longValue(trits(), ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET, ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE);

    }
    public void setMetadata() {
        transaction.value = Converter.longValue(trits(), VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE);
        transaction.timestamp = Converter.longValue(trits(), TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_SIZE);
        //if (transaction.timestamp > 1262304000000L ) transaction.timestamp /= 1000L;  // if > 01.01.2010 in milliseconds
        transaction.currentIndex = Converter.longValue(trits(), CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
        transaction.lastIndex = Converter.longValue(trits(), LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE);
        transaction.type = transaction.bytes == null ? TransactionViewModel.PREFILLED_SLOT : TransactionViewModel.FILLED_SLOT;
    }

    public static boolean exists(Tangle tangle, Hash hash) throws Exception {
        return tangle.exists(Transaction.class, hash);
    }

    public static Set<Indexable> getMissingTransactions(Tangle tangle) throws Exception {
        return tangle.keysWithMissingReferences(Approvee.class, Transaction.class);
    }

    public static void updateSolidTransactions(Tangle tangle, final Set<Hash> analyzedHashes) throws Exception {
        Iterator<Hash> hashIterator = analyzedHashes.iterator();
        TransactionViewModel transactionViewModel;
        while(hashIterator.hasNext()) {
            transactionViewModel = TransactionViewModel.fromHash(tangle, hashIterator.next());

            transactionViewModel.updateHeights(tangle);

            if(!transactionViewModel.isSolid()) {
                transactionViewModel.updateSolid(true);
                transactionViewModel.update(tangle, "solid|height");
            }
        }
    }

    public boolean updateSolid(boolean solid) throws Exception {
        if(solid != transaction.solid) {
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

    public void setSnapshot(Tangle tangle, final int index) throws Exception {
        if ( index != transaction.snapshot ) {
            transaction.snapshot = index;
            update(tangle, "snapshot");
        }
    }

    /**
     * This method is the setter for the milestone flag of a transaction.
     *
     * It gets automatically called by the "Latest Milestone Tracker" and marks transactions that represent a milestone
     * accordingly. It first checks if the value has actually changed and then issues a database update.
     *
     * @param tangle Tangle instance which acts as a database interface
     * @param isMilestone true if the transaction is a milestone and false otherwise
     * @throws Exception if something goes wrong while saving the changes to the database
     */
    public void isMilestone(Tangle tangle, final boolean isMilestone) throws Exception {
        if (isMilestone != transaction.milestone) {
            transaction.milestone = isMilestone;
            update(tangle, "milestone");
        }
    }

    /**
     * This method is the getter for the milestone flag of a transaction.
     *
     * The milestone flag indicates if the transaction is a coordinator issued milestone. It allows us to differentiate
     * the two types of transactions (normal transactions / milestones) very fast and efficiently without issuing
     * further database queries or even full verifications of the signature. If it is set to true one can for example
     * use the snapshotIndex() method to retrieve the corresponding MilestoneViewModel object.
     *
     * @return true if the transaction is a milestone and false otherwise
     */
    public boolean isMilestone() {
        return transaction.milestone;
    }

    public long getHeight() {
        return transaction.height;
    }

    private void updateHeight(long height) throws Exception {
        transaction.height = height;
    }

    public void updateHeights(Tangle tangle) throws Exception {
        TransactionViewModel transactionVM = this, trunk = this.getTrunkTransaction(tangle);
        Stack<Hash> transactionViewModels = new Stack<>();
        transactionViewModels.push(transactionVM.getHash());
        while(trunk.getHeight() == 0 && trunk.getType() != PREFILLED_SLOT && !trunk.getHash().equals(Hash.NULL_HASH)) {
            transactionVM = trunk;
            trunk = transactionVM.getTrunkTransaction(tangle);
            transactionViewModels.push(transactionVM.getHash());
        }
        while(transactionViewModels.size() != 0) {
            transactionVM = TransactionViewModel.fromHash(tangle, transactionViewModels.pop());
            long currentHeight = transactionVM.getHeight();
            if(Hash.NULL_HASH.equals(trunk.getHash()) && trunk.getHeight() == 0
                    && !Hash.NULL_HASH.equals(transactionVM.getHash())) {
                if(currentHeight != 1L ){
                    transactionVM.updateHeight(1L);
                    transactionVM.update(tangle, "height");
                }
            } else if ( trunk.getType() != PREFILLED_SLOT && transactionVM.getHeight() == 0){
                long newHeight = 1L + trunk.getHeight();
                if(currentHeight != newHeight) {
                    transactionVM.updateHeight(newHeight);
                    transactionVM.update(tangle, "height");
                }
            } else {
                break;
            }
            trunk = transactionVM;
        }
    }

    public void updateSender(String sender) throws Exception {
        transaction.sender = sender;
    }
    public String getSender() {
        return transaction.sender;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TransactionViewModel other = (TransactionViewModel) o;
        return Objects.equals(getHash(), other.getHash());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHash());
    }

    /**
     * This method creates a human readable string representation of the transaction.
     *
     * It can be used to directly append the transaction in error and debug messages.
     *
     * @return human readable string representation of the transaction
     */
    @Override
    public String toString() {
        return "transaction " + hash.toString();
    }
}
