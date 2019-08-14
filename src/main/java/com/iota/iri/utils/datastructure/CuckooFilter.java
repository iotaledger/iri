package com.iota.iri.utils.datastructure;

/**
 * The Cuckoo Filter is a probabilistic data structure that supports fast set membership testing.
 *
 * It is very similar to a bloom filter in that they both are very fast and space efficient. Both the bloom filter and
 * cuckoo filter also report false positives on set membership.
 *
 * Cuckoo filters are a relatively new data structure, described in a paper in 2014 by Fan, Andersen, Kaminsky, and
 * Mitzenmacher (https://www.cs.cmu.edu/~dga/papers/cuckoo-conext2014.pdf). They improve upon the design of the bloom
 * filter by offering deletion, limited counting, and a bounded false positive probability, while still maintaining a
 * similar space complexity.
 *
 * They use cuckoo hashing to resolve collisions and are essentially a compact cuckoo hash table.
 */
public interface CuckooFilter {
    /**
     * Adds a new elements to the filter that then can be queried with {@link #contains(String)}.
     *
     * @param item element that shall be stored in the filter
     * @return true if the insertion was successful (if the filter is too full this can return false)
     * @throws IndexOutOfBoundsException if we try to add an element to an already too full filter
     */
    boolean add(String item) throws IndexOutOfBoundsException;

    /**
     * Adds a new elements to the filter that then can be queried with {@link #contains(byte[])}.
     *
     * @param item element that shall be stored in the filter
     * @return true if the insertion was successful (if the filter is too full this can return false)
     * @throws IndexOutOfBoundsException if we try to add an element to an already too full filter
     */
    boolean add(byte[] item) throws IndexOutOfBoundsException;

    /**
     * Queries for the existence of an element in the filter.
     *
     * @param item element that shall be checked
     * @return true if it is "probably" in the filter (~3% false positives) or false if it is "definitely" not in there
     */
    boolean contains(String item);

    /**
     * Queries for the existence of an element in the filter.
     *
     * @param item element that shall be checked
     * @return true if it is "probably" in the filter (~3% false positives) or false if it is "definitely" not in there
     */
    boolean contains(byte[] item);

    /**
     * Deletes an element from the filter.
     *
     * @param item element that shall be deleted from filter
     * @return true if something was deleted matching the element or false otherwise
     */
    boolean delete(String item);

    /**
     * Deletes an element from the filter.
     *
     * @param item element that shall be deleted from filter
     * @return true if something was deleted matching the element or false otherwise
     */
    boolean delete(byte[] item);

    /**
     * This method returns the actual capacity of the filter.
     *
     * Since the capacity has to be a power of two and we want to reach a load factor of less than 0.955, the actual
     * capacity is bigger than the amount of items we passed into the constructor.
     *
     * @return the actual capacity of the filter
     */
    int getCapacity();

    /**
     * This method returns the amount of elements that are stored in the filter.
     *
     * Since a cuckoo filter can have collisions the size is not necessarily identical with the amount of items that we
     * added.
     *
     * @return the amount of stored items
     */
    int size();
}
