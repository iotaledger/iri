package com.iota.iri.utils.datastructure.impl;

import com.iota.iri.utils.BitSetUtils;
import com.iota.iri.utils.datastructure.CuckooFilter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;

/**
 * This class implements the basic contract of the {@link CuckooFilter}.
 */
public class CuckooFilterImpl implements CuckooFilter {
    /**
     * The amount of times we try to kick elements when inserting before we consider the index to be too full.
     */
    private static final int MAX_NUM_KICKS = 500;

    /**
     * A reference to the last element that didn't fit into the filter (used for "soft failure" on first attempt).
     */
    private CuckooFilterItem lastVictim;

    /**
     * The hash function that is used to generate finger prints and indexes (defaults to SHA1).
     */
    private MessageDigest hashFunction;

    /**
     * the amount of buckets in our table (get's calculated from the itemCount that we want to store)
     */
    private int tableSize = 1;

    /**
     * The amount of items that can be stored in each bucket.
     */
    private int bucketSize;

    /**
     * The amount of bits per fingerprint for each entry (the optimum is around 7 bits with a load of ~0.955)
     */
    private int fingerPrintSize;

    /**
     * Holds the amount if items that are stored in the filter.
     */
    private int storedItems = 0;

    /**
     * Holds the capacity of the filter.
     */
    private int capacity = 1;

    /**
     * The actual underlying data structure holding the elements.
     */
    private CuckooFilterTable cuckooFilterTable;

    /**
     * Simplified constructor that automatically chooses the values with best space complexity and false positive rate.
     *
     * The optimal values are a bucket size of 4 and a fingerprint size of 7.2 bits (we round up to 8 bits). For more
     * info regarding those values see
     * <a href="https://www.cs.cmu.edu/~dga/papers/cuckoo-conext2014.pdf">cuckoo-conext2014.pdf</a> and
     * <a href="https://brilliant.org/wiki/cuckoo-filter/#cuckoo-hashing">Cuckoo Hashing</a>.
     *
     * NOTE: The actual size will be slightly bigger since the size has to be a power of 2 and take the optimal load
     *       factor of 0.955 into account.
     *
     * @param itemCount the minimum amount of items that should fit into the filter
     */
    public CuckooFilterImpl(int itemCount) {
        this(itemCount, 4, 8);
    }

    /**
     * Advanced constructor that allows for fine tuning of the desired filter.
     *
     * It first saves a reference to the hash function and then checks the parameters - the finger print size cannot
     * be bigger than 128 bits because SHA1 generates 160 bits and we use 128 of that for the fingerprint and the rest
     * for the index.
     *
     * After verifying that the passed in parameters are reasonable, we calculate the required size of the
     * {@link CuckooFilterTable} by increasing the table size exponentially until we can fit the desired item count with
     * a load factor of <= 0.955. Finally we create the {@link CuckooFilterTable} that will hold our data.
     *
     * NOTE: The actual size will be slightly bigger since the size has to be a power of 2 and take the optimal load
     *       factor of 0.955 into account.
     *
     * @param itemCount the minimum amount of items that should fit into the filter
     * @param bucketSize the amount of items that can be stored in each bucket
     * @param fingerPrintSize the amount of bits per fingerprint (it has to be bigger than 0 and smaller than 128)
     * @throws IllegalArgumentException if the finger print size is too small or too big
     * @throws InternalError if the SHA1 hashing function can not be found with this java version [should never happen]
     */
    public CuckooFilterImpl(int itemCount, int bucketSize, int fingerPrintSize) throws IllegalArgumentException,
            InternalError {

        try {
            hashFunction = MessageDigest.getInstance("SHA1");
        } catch(NoSuchAlgorithmException e) {
            throw new InternalError("missing SHA1 support - please check your JAVA installation");
        }

        if(fingerPrintSize <= 0 || fingerPrintSize > 128) {
            throw new IllegalArgumentException("invalid finger print size \"" + fingerPrintSize +
                    "\" [expected value between 0 and 129]");
        }

        while((tableSize * bucketSize) < itemCount || itemCount * 1.0 / (tableSize * bucketSize) > 0.955) {
            tableSize <<= 1;
        }

        this.bucketSize = bucketSize;
        this.fingerPrintSize = fingerPrintSize;
        this.capacity = tableSize * bucketSize + 1;

        cuckooFilterTable = new CuckooFilterTable(tableSize, bucketSize, fingerPrintSize);
    }

    /**
     * {@inheritDoc}
     *
     * It retrieves the necessary details by passing it into the Item class and then executes the internal add logic.
     */
    @Override
    public boolean add(String item) throws IndexOutOfBoundsException {
        return add(new CuckooFilterItem(item));
    }

    /**
     * {@inheritDoc}
     *
     * It retrieves the necessary details by passing it into the Item class and then executes the internal add logic.
     */
    @Override
    public boolean add(byte[] item) throws IndexOutOfBoundsException {
        return add(new CuckooFilterItem(hashFunction.digest(item)));
    }

    /**
     * {@inheritDoc}
     *
     * It retrieves the necessary details by passing it into the Item class and then executes the internal contains
     * logic.
     */
    @Override
    public boolean contains(String item) {
        return contains(new CuckooFilterItem(item));
    }

    /**
     * {@inheritDoc}
     *
     * It retrieves the necessary details by passing it into the Item class and then executes the internal contains
     * logic.
     */
    @Override
    public boolean contains(byte[] item) {
        return contains(new CuckooFilterItem(hashFunction.digest(item)));
    }

    /**
     * {@inheritDoc}
     *
     * It retrieves the necessary details by passing it into the Item class and then executes the internal delete logic.
     */
    @Override
    public boolean delete(String item) {
        return delete(new CuckooFilterItem(item));
    }

    /**
     * {@inheritDoc}
     *
     * It retrieves the necessary details by passing it into the Item class and then executes the internal delete logic.
     */
    @Override
    public boolean delete(byte[] item) {
        return delete(new CuckooFilterItem(hashFunction.digest(item)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCapacity() {
        return capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return storedItems;
    }

    /**
     * Adds a new elements to the filter that then can be queried for existence.
     *
     * It first checks if the item is already a part of the filter and skips the insertion if that is the case. If the
     * element is not part of the filter, we check if the table is too full already by checking if we have a kicked out
     * victim.
     *
     * If the filter is not too full, we insert the element by trying to place it in its associated position (moving
     * existing elements if space is needed).
     *
     * @param item item to be stored in the filter
     * @return true if the insertion was successful (if the filter is too full this can return false)
     * @throws IndexOutOfBoundsException if we try to add an item to an already too full filter
     */
    private boolean add(CuckooFilterItem item) throws IndexOutOfBoundsException {
        if(contains(item)) {
            return true;
        }

        if(lastVictim != null) {
            throw new IndexOutOfBoundsException("the filter is too full");
        }

        // try to insert the item into the first free slot of its cuckooFilterTable (trivial)
        for(int i = 0; i < bucketSize; i++) {
            if(cuckooFilterTable.get(item.index, i) == null) {
                cuckooFilterTable.set(item.index, i, item.fingerPrint);

                storedItems++;

                return true;
            }

            if(cuckooFilterTable.get(item.altIndex, i) == null) {
                cuckooFilterTable.set(item.altIndex, i, item.fingerPrint);

                storedItems++;

                return true;
            }
        }

        // filter is full -> start moving MAX_NUM_KICKS times (randomly select which bucket to start with)
        int indexOfDestinationBucket = Math.random() < 0.5 ? item.index : item.altIndex;
        for(int i = 0; i < MAX_NUM_KICKS; i++) {
            // select a random item to kick
            int indexOfItemToKick = (int) (Math.random() * bucketSize);

            // swap the items
            BitSet kickedFingerPrint = cuckooFilterTable.get(indexOfDestinationBucket, indexOfItemToKick);
            cuckooFilterTable.set(indexOfDestinationBucket, indexOfItemToKick, item.fingerPrint);
            item = new CuckooFilterItem(kickedFingerPrint, indexOfDestinationBucket);
            indexOfDestinationBucket = item.altIndex;

            // try to insert the items into its alternate location
            for(int n = 0; n < bucketSize; n++) {
                if(cuckooFilterTable.get(indexOfDestinationBucket, n) == null) {
                    cuckooFilterTable.set(indexOfDestinationBucket, n, item.fingerPrint);

                    storedItems++;

                    return true;
                }
            }
        }

        // store the last item that didn't fit, so we can provide a soft failure option
        lastVictim = item;

        storedItems++;

        // return false to indicate that the addition failed
        return false;
    }

    /**
     * Queries for the existence of an element in the filter.
     *
     * It simply checks if the item exists in one of it's associated buckets or if it equals the lastVictim which is set
     * in case the filter ever gets too full.
     *
     * @param item element to be checked
     * @return true if it is "probably" in the filter (~3% false positives) or false if it is "definitely" not in there
     */
    private boolean contains(CuckooFilterItem item) {
        if(lastVictim != null && item.fingerPrint.equals(lastVictim.fingerPrint)) {
            return true;
        }

        // check existence of our finger print in the first index
        for(int i = 0; i < bucketSize; i++) {
            if(item.fingerPrint.equals(cuckooFilterTable.get(item.index, i))) {
                return true;
            }
        }

        // check existence of our finger print the alternate index
        for(int i = 0; i < bucketSize; i++) {
            if(item.fingerPrint.equals(cuckooFilterTable.get(item.altIndex, i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Deletes an element from the filter.
     *
     * It first tries to delete the item from the lastVictim slot if it matches and in case of failure cycles through
     * the corresponding buckets, to remove a copy of it's fingerprint if one is found.
     *
     * @param item element that shall be deleted from filter
     * @return true if something was deleted matching the item
     */
    public boolean delete(CuckooFilterItem item) {
        if(lastVictim != null && item.fingerPrint.equals(lastVictim.fingerPrint)) {
            lastVictim = null;

            storedItems--;

            return true;
        }

        // check existence of our finger print in the first index
        for(int i = 0; i < bucketSize; i++) {
            if(item.fingerPrint.equals(cuckooFilterTable.get(item.index, i))) {
                cuckooFilterTable.delete(item.index, i);

                storedItems--;

                return true;
            }
        }

        // check existence of our finger print the alternate index
        for(int i = 0; i < bucketSize; i++) {
            if(item.fingerPrint.equals(cuckooFilterTable.get(item.altIndex, i))) {
                cuckooFilterTable.delete(item.altIndex, i);

                storedItems--;

                return true;
            }
        }

        return false;
    }

    /**
     * This method derives the index of an element by the full hash of the item.
     *
     * It is primarily used to calculate the original position of the item, when the item is freshly inserted into the
     * filter. Since we only store the finger print of the item we have to use the 2nd version of this method to later
     * retrieve the alternate index through (partial-key cuckoo hashing).
     *
     * @param elementHash hash of the element
     * @return the primary index of the element
     */
    private int getIndex(byte[] elementHash) {
        // initialize the new address with an empty bit sequence
        long index = 0;

        // process all address bytes (first 4 bytes)
        for(int i = 0; i < 4; i++) {
            // copy the bits from the hash into the index
            index |= (elementHash[i] & 0xff);

            // shift the bits to make space for the next iteration
            if(i < 3) {
                index <<= 8;
            }
        }

        // extract the relevant last 8 bits
        index &= 0x00000000ffffffffL;

        // map the result to the domain of possible table addresses
        return (int) (index % (long) tableSize);
    }

    /**
     * This method allows us to retrieve the "alternate" index of an item based on it's current position in the table
     * and it's fingerprint.
     *
     * It is used to move items around that are already part of the table and where the original hash value is not known
     * anymore. The mechanism used to derive the new position is called partial-key cuckoo hashing and while being
     * relatively bad "in theory", it turns out to be better in practice than the math would suggest when it comes to
     * distributing the entries equally in the table.
     *
     * The operation is bi-directional, allowing us to also get the original position by passing the alternate index
     * into this function.
     *
     * @param fingerPrint finger print of the item
     * @param oldIndex the old position of the item in the cuckoo hash table
     * @return the alternate index of the element
     */
    private int getIndex(BitSet fingerPrint, long oldIndex) {
        // calculate the hash of the finger print (partial-key cuckoo hashing)
        byte[] fingerPrintHash = hashFunction.digest(BitSetUtils.convertBitSetToByteArray(fingerPrint));

        // initialize the new address with an empty bit sequence
        long index = 0;

        // process all address bytes (first 4 bytes)
        for (int i=0; i < 4; i++) {
            // shift the relevant oldIndex byte into position
            byte oldIndexByte = (byte) (((0xffL << (i*8)) & (long) oldIndex) >> (i * 8));

            // xor the finger print and the oldIndex bytes and insert the result into the new index
            index |= (((fingerPrintHash[i] ^ oldIndexByte) & 0xff) << (i * 8));
        }

        // extract the relevant last 8 bits
        index &= 0x00000000ffffffffL;

        // map the result to the domain of possible table addresses
        return (int) (index % (long) tableSize);
    }

    /**
     * This method allows us to calculate the finger print of an item based on it's hash value.
     *
     * It is used when inserting an item for the first time into the filter and to check the existence of items in the
     * filter.
     *
     * @param hash full hash of the item only known when inserting or checking if an item is contained in the filter
     * @return a BitSet representing the first n bits of the hash starting from index 4 up to the necessary length
     * @throws IllegalArgumentException if the hash value provided to the method is too short
     */
    private BitSet generateFingerPrint(byte[] hash) throws IllegalArgumentException {
        if(hash.length < 20) {
            throw new IllegalArgumentException("invalid hash [expected hash to contain at least 20 bytes]");
        }

        // do a simple conversion of the byte array to a BitSet of the desired length
        return BitSetUtils.convertByteArrayToBitSet(hash, 4, fingerPrintSize);
    }

    /**
     * Internal helper class to represent items that are stored in the filter.
     *
     * It bundles the logic for generating the correct indexes and eases the access to all the properties that are
     * related to managing those items while moving and inserting them. By having this little wrapper we only have to do
     * the expensive calculations (like generating the hashes) once and can then pass them around.
     */
    private class CuckooFilterItem {
        private BitSet fingerPrint;

        private int index;

        private int altIndex;

        public CuckooFilterItem(String item) {
            this(hashFunction.digest(item.getBytes()));
        }

        public CuckooFilterItem(byte[] hash) {
            fingerPrint = generateFingerPrint(hash);
            index = getIndex(hash);
            altIndex = getIndex(fingerPrint, index);
        }

        public CuckooFilterItem(BitSet fingerPrint, int index) {
            this.fingerPrint = fingerPrint;
            this.index = index;
            altIndex = getIndex(fingerPrint, index);
        }
    }

    /**
     * This class implements a 2 dimensional table holding BitSets, whereas the first dimension represents the bucket
     * index and the 2nd dimension represents the slot in the bucket.
     *
     * It maps this 2-dimensional data structure to a 1-dimensional BitSet holding the actual values so even for huge
     * first-level dimensions, we only call the constructor once - making it very fast.
     */
    private class CuckooFilterTable {
        /**
         * Holds the actual data in a "flattened" way to improve performance.
         */
        private BitSet data;

        /**
         * Holds the number of buckets (first dimension).
         */
        private int bucketAmount;

        /**
         * Holds the size of the buckets (second dimension).
         */
        private int bucketSize;

        /**
         * Holds the amount of bits stored in each slot of the bucket.
         */
        private int bitSetSize;

        /**
         * This method initializes our underlying data structure and saves all the relevant parameters.
         *
         * @param bucketAmount number of buckets
         * @param bucketSize size of the buckets
         * @param bitSetSize amount of bits stored in each slot of the bucket
         */
        public CuckooFilterTable(int bucketAmount, int bucketSize, int bitSetSize) {
            this.bucketAmount = bucketAmount;
            this.bucketSize = bucketSize;
            this.bitSetSize = bitSetSize;

            data = new BitSet(bucketAmount * bucketSize * (bitSetSize + 1));
        }

        /**
         * This method allows us to retrieve elements from the table.
         *
         * It creates a new BitSet with the value that is stored underneath. Every consequent call of this method
         * creates a new Object so we don't waste any memory with caching objects.
         *
         * Note: It is not possible to retrieve a bucket as a whole since it gets mapped to the 1-dimensional structure
         *       but this is also not necessary for the implementation of the filter.
         *
         * @param bucketIndex index of the bucket (1st dimension)
         * @param slotIndex slot in the bucket (2nd dimension)
         * @return stored BitSet or null if the slot is empty
         */
        public BitSet get(int bucketIndex, int slotIndex) {
            // calculates the mapped indexes
            int nullIndex = bucketIndex * bucketSize * (bitSetSize + 1) + slotIndex * (bitSetSize + 1);
            if(!data.get(nullIndex)) {
                return null;
            }

            // creates the result object
            BitSet result = new BitSet(bitSetSize);

            // copies the bits from our underlying data structure to the result
            for(int i = nullIndex + 1; i <= nullIndex + bitSetSize; i++) {
                int relativeIndex = i - (nullIndex + 1);

                result.set(relativeIndex, data.get(i));
            }

            // returns the final result object
            return result;
        }

        /**
         * This method allows us to store a new BitSet at the defined location.
         *
         * If we pass null as the object to store, the old items gets deleted and the flag representing "if the slot is
         * filled" get's set to false.
         *
         * @param bucketIndex index of the bucket (1st dimension)
         * @param slotIndex slot in the bucket (2nd dimension)
         * @param bitSet object to store
         * @return the table itself so we can chain calls
         */
        public CuckooFilterTable set(int bucketIndex, int slotIndex, BitSet bitSet) {
            // calculates the mapped indexes
            int nullIndex = bucketIndex * bucketSize * (bitSetSize + 1) + slotIndex * (bitSetSize + 1);

            // mark the location as set or unset
            data.set(nullIndex, bitSet != null);

            // copy the bits of the source BitSet to the mapped data structure
            if(bitSet != null) {
                for(int i = nullIndex + 1; i <= nullIndex + bitSetSize; i++) {
                    int relativeIndex = i - (nullIndex + 1);

                    data.set(i, bitSet.get(relativeIndex));
                }
            }

            return this;
        }

        /**
         * This method allows us to remove elements from the table.
         *
         * It internally calls the set method with null as the item to store.
         *
         * @param bucketIndex index of the bucket (1st dimension)
         * @param slotIndex slot in the bucket (2nd dimension)
         * @return the table itself so we can chain calls
         */
        public CuckooFilterTable delete(int bucketIndex, int slotIndex) {
            return set(bucketIndex, slotIndex, null);
        }
    }
}
