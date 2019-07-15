package com.iota.iri.utils.datastructure.impl;

import com.iota.iri.utils.datastructure.CuckooFilter;
import org.junit.*;
import org.junit.runners.MethodSorters;

/**
 * This is the Unit Test for the {@link CuckooFilterImpl}, that tests the individual methods as well as the overall
 * performance of the filter in regards to the expected false positive rate.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CuckooFilterImplTest {
    /**
     * Holds the amount of elements we want to store in the filter.
     *
     * Note: 1955 items allows for a ~0.955 load factor at an effective capacity of 2048
     */
    private static final int ELEMENTS_TO_STORE = 1955;

    /**
     * Holds a reference to the filter that is shared throughout the tests (for the String methods).
     */
    private static CuckooFilter stringCuckooFilter;

    /**
     * Holds a reference to the filter that is shared throughout the tests (for the byte[] methods).
     */
    private static CuckooFilter byteArrayCuckooFilter;

    /**
     * Initializes our test by creating an empty {@link CuckooFilterImpl}.
     */
    @BeforeClass
    public static void setup() {
        stringCuckooFilter = new CuckooFilterImpl(ELEMENTS_TO_STORE);
        byteArrayCuckooFilter = new CuckooFilterImpl(ELEMENTS_TO_STORE);
    }

    /**
     * Frees the resources again, so the unused filter can be cleaned up by the GarbageCollector.
     */
    @AfterClass
    public static void teardown() {
        stringCuckooFilter = null;
        byteArrayCuckooFilter = null;
    }

    /**
     * This method tests the function of the add method (for the String parameter) by:
     *
     *   1. inserting the defined amount of elements
     *   2. checking if the size is within the expected range
     */
    @Test
    public void testAaddString() {
        int insertedItems;
        for (insertedItems = 0; insertedItems < ELEMENTS_TO_STORE; insertedItems++) {
            stringCuckooFilter.add("INSERTED_ITEM" + Integer.toString(insertedItems));
        }

        int sizeDiff = ELEMENTS_TO_STORE - stringCuckooFilter.size();

        Assert.assertTrue("the filter should have less elements than we added (due to collisions)", sizeDiff >= 0);
        Assert.assertTrue("the difference in size should be less than 3%", sizeDiff <= ELEMENTS_TO_STORE * 0.03d);
    }

    /**
     * This method tests the function of the add method (for the byte[] parameter) by:
     *
     *   1. inserting the defined amount of elements
     *   2. checking if the size is within the expected range
     */
    @Test
    public void testAaddByteArray() {
        int insertedItems;
        for (insertedItems = 0; insertedItems < ELEMENTS_TO_STORE; insertedItems++) {
            byteArrayCuckooFilter.add(("INSERTED_ITEM" + Integer.toString(insertedItems)).getBytes());
        }

        int sizeDiff = ELEMENTS_TO_STORE - byteArrayCuckooFilter.size();

        Assert.assertTrue("the filter should have less elements than we added (due to collisions)", sizeDiff >= 0);
        Assert.assertTrue("the difference in size should be less than 3%", sizeDiff <= ELEMENTS_TO_STORE * 0.03d);
    }

    /**
     * This method tests the function of the contains method (for the String parameter) by checking if all previously
     * added elements are found.
     */
    @Test
    public void testBcontainsString() {
        int insertedItems;
        for (insertedItems = 0; insertedItems < ELEMENTS_TO_STORE; insertedItems++) {
            Assert.assertTrue("the filter should contain all previously added elements",
                    stringCuckooFilter.contains("INSERTED_ITEM" + Integer.toString(insertedItems)));
        }
    }

    /**
     * This method tests the function of the contains method (for the byte[] parameter) by checking if all previously
     * added elements are found.
     */
    @Test
    public void testBcontainsByteArray() {
        int insertedItems;
        for (insertedItems = 0; insertedItems < ELEMENTS_TO_STORE; insertedItems++) {
            Assert.assertTrue("the filter should contain all previously added elements",
                    byteArrayCuckooFilter.contains(("INSERTED_ITEM" + Integer.toString(insertedItems)).getBytes()));
        }
    }

    /**
     * This method tests the function of the delete method (for the String parameter) by:
     *
     *   1. removing all previously added elements
     *   2. checking if the filter is empty afterwards
     */
    @Test
    public void testCdeleteString() {
        int insertedItems;
        for (insertedItems = 0; insertedItems < ELEMENTS_TO_STORE; insertedItems++) {
            stringCuckooFilter.delete("INSERTED_ITEM" + Integer.toString(insertedItems));
        }

        Assert.assertEquals("the filter should be empty", 0, stringCuckooFilter.size());


    }

    /**
     * This method tests the function of the delete method (for the byte[] parameter) by:
     *
     *   1. removing all previously added elements
     *   2. checking if the filter is empty afterwards
     */
    @Test
    public void testCdeleteByteArray() {
        int insertedItems;
        for (insertedItems = 0; insertedItems < ELEMENTS_TO_STORE; insertedItems++) {
            stringCuckooFilter.delete(("INSERTED_ITEM" + Integer.toString(insertedItems)).getBytes());
        }

        Assert.assertEquals("the filter should be empty", 0, stringCuckooFilter.size());


    }

    /**
     * This method tests the performance of the filter (using the String parameter) in regards to false positives by:
     *
     *   1. inserting the defined amount of elements
     *   2. querying for non-existing elements
     *   3. calculating the false-positive hits
     *   4. comparing the value against the expected result
     */
    @Test
    public void testDfalsePositiveRateString() {
        int insertedItems;
        for (insertedItems = 0; insertedItems < ELEMENTS_TO_STORE; insertedItems++) {
            stringCuckooFilter.add("INSERTED_ITEM" + Integer.toString(insertedItems));
        }

        // a big enough sample size to get a reasonable result
        int elementsToQuery = 100000;

        int falsePositives = 0;
        int queriedItems;
        for (queriedItems = 0; queriedItems < elementsToQuery; queriedItems++) {
            if (stringCuckooFilter.contains("QUERIED_ITEMS" + Integer.toString(queriedItems))) {
                falsePositives++;
            }
        }

        double falsePositiveRate = (double) falsePositives / (double) elementsToQuery;

        Assert.assertTrue("expecting the false positive rate to be lower than 3%", falsePositiveRate < 0.03d);
    }

    /**
     * This method tests the performance of the filter (using the byte[] parameter) in regards to false positives by:
     *
     *   1. inserting the defined amount of elements
     *   2. querying for non-existing elements
     *   3. calculating the false-positive hits
     *   4. comparing the value against the expected result
     */
    @Test
    public void testDfalsePositiveRateByteArray() {
        int insertedItems;
        for (insertedItems = 0; insertedItems < ELEMENTS_TO_STORE; insertedItems++) {
            byteArrayCuckooFilter.add(("INSERTED_ITEM" + Integer.toString(insertedItems)).getBytes());
        }

        // a big enough sample size to get a reasonable result
        int elementsToQuery = 100000;

        int falsePositives = 0;
        int queriedItems;
        for (queriedItems = 0; queriedItems < elementsToQuery; queriedItems++) {
            if (byteArrayCuckooFilter.contains(("QUERIED_ITEMS" + Integer.toString(queriedItems)).getBytes())) {
                falsePositives++;
            }
        }

        double falsePositiveRate = (double) falsePositives / (double) elementsToQuery;

        Assert.assertTrue("expecting the false positive rate to be lower than 3%", falsePositiveRate < 0.03d);
    }

    /**
     * This method tests the function of the getCapacity method by:
     *
     *   1. creating filters of various sizes
     *   2. comparing the created capacity against the expected range
     *
     * Note: Since the capacity has to be a power of two and tries to achieve a load factor of 0.955, the capacity will
     *       at max be 2.1 times the intended size.
     *
     *       capacity <= 2 * (1 / 0.955) * filterSize
     */
    @Test
    public void testEcapacity() {
        int[] filterSizes = {10, 500, 25_000, 125_000, 10_000_000};

        CuckooFilter emptyCuckooFilter;
        for (int filterSize : filterSizes) {
            emptyCuckooFilter = new CuckooFilterImpl(filterSize);

            Assert.assertTrue("the capacity should be bigger than the intended filter size",
                    emptyCuckooFilter.getCapacity() > filterSize);

            Assert.assertTrue("the capacity should be smaller than 2.094 times the filter size",
                    emptyCuckooFilter.getCapacity() < filterSize * 2.094d);
        }
    }
}
