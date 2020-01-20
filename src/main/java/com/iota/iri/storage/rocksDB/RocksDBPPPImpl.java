package com.iota.iri.storage.rocksDB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Bytes;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.model.persistables.Address;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.model.persistables.Hashes;
import com.iota.iri.storage.PermanentPersistenceProvider;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.utils.IotaIOUtils;
import com.iota.iri.utils.Pair;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;

import org.rocksdb.util.SizeUnit;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.Priority;
import org.rocksdb.DBOptions;
import org.rocksdb.BloomFilter;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.rocksdb.RocksEnv;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.StringAppendOperator;
import org.rocksdb.MergeOperator;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.ColumnFamilyDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/***
 * Implements the retrieval functions of a the normal persistence provider and the permanent persistence provider
 */
public class RocksDBPPPImpl implements PermanentPersistenceProvider, PersistenceProvider {

    private static final Logger log = LoggerFactory.getLogger(RocksDBPPPImpl.class);
    private static final int BLOOM_FILTER_BITS_PER_KEY = 10;
    /** A delimeter for separating hashes within a byte stream */
    private static final byte delimiter = ",".getBytes()[0];
    public static String TRANSACTION_COLUMN = "transaction";
    public static String ADDRESS_INDEX = "address-index";
    public static String BUNDLE_INDEX = "bundle-index";
    public static String TAG_INDEX = "tag-index";
    public static String APPROVEE_INDEX = "approvee-index";
    private final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
    private final String dbPath;
    private final String logPath;
    private final int cacheSize;
    @VisibleForTesting
    Map<String, ColumnFamilyHandle> columnMap = new HashMap<>();

    private RocksDB db;
    // DBOptions is only used in initDB(). However, it is closeable - so we keep a reference for shutdown.
    private DBOptions options;
    private BloomFilter bloomFilter;
    private boolean available;

    /**
     * Constructor
     * @param dbPath database path
     * @param logPath database log path
     * @param cacheSize cache size
     */
    public RocksDBPPPImpl(String dbPath, String logPath, int cacheSize) {
        this.dbPath = dbPath;
        this.logPath = logPath;
        this.cacheSize = cacheSize;
    }

    /**
     * @see PersistenceProvider#init()
     * @throws Exception
     */
    @Override
    public void init() throws Exception {
        log.info("Initializing Database on " + dbPath);
        initDB(dbPath, logPath);
        available = true;
        log.info("RocksDB permanent persistence provider initialized.");
    }

    /**
     * @see PersistenceProvider#isAvailable()
     * @return
     */
    @Override
    public boolean isAvailable() {
        return this.available;
    }

    /**
     * @see PersistenceProvider#shutdown()
     */
    @Override
    public void shutdown() {
        for (final ColumnFamilyHandle columnFamilyHandle : columnFamilyHandles) {
            IotaIOUtils.closeQuietly(columnFamilyHandle);
        }
        IotaIOUtils.closeQuietly(db, options, bloomFilter);
    }

    // ----------- PersistenceProvider only overrides ---------------
    /**
     * @see PersistenceProvider#save(Persistable, Indexable)
     */
    @Override
    public boolean save(Persistable model, Indexable index) throws Exception {
        return false;
    }

    /**
     * @see PersistenceProvider#delete(Class, Indexable)
     */
    @Override
    public void delete(Class<?> model, Indexable index) throws Exception {
        return;
    }

    /**
     * @see PersistenceProvider#update(Persistable, Indexable, String)
     */
    @Override
    public boolean update(Persistable model, Indexable index, String item) throws Exception {
        return false;
    }

    /**
     * @see PersistenceProvider#exists(Class, Indexable) ()
     */
    @Override
    public boolean exists(Class<?> model, Indexable key) throws Exception {
        return false;
    }

    /**
     * @see PersistenceProvider#latest(Class, Class)
     */
    @Override
    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception {
        return null;
    }

    /**
     * @see PersistenceProvider#keysWithMissingReferences(Class, Class)
     */
    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass) throws Exception {
        return null;
    }

    /**
     * @see PersistenceProvider#get(Class, Indexable)
     */
    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        Persistable object = (Persistable) model.newInstance();

        if (object instanceof Transaction) {

            TransactionViewModel tvm = getTransaction((Hash) index);
            if (tvm == null) {
                return object;
            }
            // These 'manually' fill the metadata from the transaction data.
            tvm.getAddressHash();
            tvm.getBundleHash();
            tvm.getBranchTransactionHash();
            tvm.getTrunkTransactionHash();
            tvm.getObsoleteTagValue();
            tvm.getObsoleteTagValue();
            tvm.getTagValue();
            tvm.setAttachmentData();
            tvm.setMetadata();

            return tvm.getTransaction();
        }

        if (object instanceof Bundle) {
            Bundle toReturn = new Bundle();
            toReturn.set = findBundle((Hash) index).set;
            return toReturn;
        }
        if (object instanceof Address) {
            Address toReturn = new Address();
            toReturn.set = findAddress((Hash) index).set;
            return toReturn;
        }
        if (object instanceof Tag) {
            Tag toReturn = new Tag();
            toReturn.set = findTag((Hash) index).set;
            return toReturn;
        }
        if (object instanceof Approvee) {
            Approvee toReturn = new Approvee();
            toReturn.set = findApprovee((Hash) index).set;
            return toReturn;
        }
        return object;
    }

    /**
     * @see PersistenceProvider#mayExist(Class, Indexable)
     */
    @Override
    public boolean mayExist(Class<?> model, Indexable index) throws Exception {
        return false;
    }

    /**
     * @see PersistenceProvider#count(Class)
     */
    @Override
    public long count(Class<?> model) throws Exception {
        return 0;
    }

    /**
     * @see PersistenceProvider#keysStartingWith(Class, byte[])
     */
    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        return null;
    }

    /**
     * @see PersistenceProvider#seek(Class, byte[])
     */
    @Override
    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        return null;
    }

    /**
     * @see PersistenceProvider#next(Class, Indexable)
     */
    @Override
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    /**
     * @see PersistenceProvider#previous(Class, Indexable)
     */
    @Override
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    /**
     * @see PersistenceProvider#first(Class, Class)
     */
    @Override
    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception {
        return null;
    }

    /**
     * @see PersistenceProvider#saveBatch(List)
     */
    @Override
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        return false;
    }

    /**
     * @see PersistenceProvider#deleteBatch(Collection)
     */
    @Override
    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models)
            throws Exception {
        return;
    }

    /**
     * @see PersistenceProvider#clear(Class)
     */
    @Override
    public void clear(Class<?> column) throws Exception {
        return;
    }

    /**
     * @see PersistenceProvider#clearMetadata(Class)
     */
    @Override
    public void clearMetadata(Class<?> column) throws Exception {
        return;
    }

    /**
     * @see PersistenceProvider#loadAllKeysFromTable(Class)
     */
    @Override
    public List<byte[]> loadAllKeysFromTable(Class<? extends Persistable> model) {
        return null;
    }
    // END----------- PersistenceProvider only overrides ---------------

    /**
     * @see PermanentPersistenceProvider#pinTransaction(TransactionViewModel, Hash)
     */
    @Override
    public boolean pinTransaction(TransactionViewModel model, Hash index) throws Exception {
        boolean exists = isPinned(Collections.singletonList(index))[0];
        if (!exists) {
            try (WriteBatch writeBatch = new WriteBatch(); WriteOptions writeOptions = new WriteOptions()) {
                byte[] txBytes = model.getBytes();

                writeBatch.put(columnMap.get(TRANSACTION_COLUMN), index.bytes(), txBytes);

                addToIndex(writeBatch, columnMap.get(ADDRESS_INDEX), model.getAddressHash(), index);
                addToIndex(writeBatch, columnMap.get(TAG_INDEX), model.getTagValue(), index);
                addToIndex(writeBatch, columnMap.get(BUNDLE_INDEX), model.getBundleHash(), index);
                addToIndex(writeBatch, columnMap.get(APPROVEE_INDEX), model.getTrunkTransactionHash(), index);
                addToIndex(writeBatch, columnMap.get(APPROVEE_INDEX), model.getBranchTransactionHash(), index);
                db.write(writeOptions, writeBatch);
                return true;
            }
        } else {
            return true; // Already pinned, is fine to return true
        }

    }

    /**
     * @see PermanentPersistenceProvider#unpinTransaction(Hash)
     */
    @Override
    public boolean unpinTransaction(Hash index) throws Exception {
        try (WriteBatch writeBatch = new WriteBatch(); WriteOptions writeOptions = new WriteOptions()) {
            safeDeleteTransaction(writeBatch, index);
            db.write(writeOptions, writeBatch);
            return true;
        }catch (Exception e) {
            log.error(e.getLocalizedMessage());
            return false;
        }
    }

    /**
     * @see PermanentPersistenceProvider#isPinned(List)
     */
    @Override
    public boolean[] isPinned(List<Hash> indexes) throws Exception {
        boolean[] result = new boolean[indexes.size()];
        ColumnFamilyHandle handle = columnMap.get(TRANSACTION_COLUMN);
        for (int i = 0; i < result.length; i++) {
            byte[] keyBytes =  indexes.get(i).bytes();
            if(db.keyMayExist(handle, keyBytes, new StringBuilder())){
                result[i] = db.get(handle, keyBytes) != null;
            }else{
                result[i] = false;
            }

        }
        return result;
    }

    /**
     * Atomic deletion of a transaction and their relevant indexes
     * 
     * @param writeBatch rocksDB atomic write batch
     * @param key transaction ID to delete
     * @throws Exception
     */
    @VisibleForTesting
    void safeDeleteTransaction(WriteBatch writeBatch, Hash key) throws Exception {
        byte[] keyBytes = key.bytes();
        TransactionViewModel tx = getTransaction(key);
        if(tx != null) {
            writeBatch.delete(columnMap.get(TRANSACTION_COLUMN), keyBytes);

            removeFromIndex(writeBatch, columnMap.get(ADDRESS_INDEX), tx.getAddressHash(), key);
            removeFromIndex(writeBatch, columnMap.get(TAG_INDEX), tx.getTagValue(), key);
            removeFromIndex(writeBatch, columnMap.get(BUNDLE_INDEX), tx.getBundleHash(), key);
            removeFromIndex(writeBatch, columnMap.get(APPROVEE_INDEX), tx.getTrunkTransactionHash(), key);
            removeFromIndex(writeBatch, columnMap.get(APPROVEE_INDEX), tx.getBranchTransactionHash(), key);
        }
    }

    /**
     * Removes an index from a set and deletes the index when the set is empty.
     * With an atomic write batch
     * @param writeBatch rocksDB atomic operation
     * @param column index column
     * @param key index key
     * @param indexValue index value
     * @throws Exception
     */
    @VisibleForTesting
    void removeFromIndex(WriteBatch writeBatch, ColumnFamilyHandle column, Indexable key, Indexable indexValue)
            throws Exception {
        byte[] indexBytes = indexValue.bytes();
        byte[] result = db.get(column, key.bytes());
        if (result != null) {
            if (result.length <= indexBytes.length) {
                // when it is the last one to delete just delete the entire index key space.
                writeBatch.delete(column, key.bytes());
            } else {

                int keyLoc = Bytes.indexOf(result, indexBytes);
                if (keyLoc >= 0) { // Does exists
                    ByteBuffer buffer = ByteBuffer.allocate(result.length - indexBytes.length - 1);
                    byte[] subarray1 = ArrayUtils.subarray(result, 0, keyLoc - 1);
                    buffer.put(subarray1);
                    byte[] subarray2 = ArrayUtils.subarray(result, keyLoc + indexBytes.length + 1, result.length);
                    if (subarray1.length > 0 && subarray2.length > 0) {
                        buffer.put(delimiter);
                        buffer.put(subarray2);
                    }
                    writeBatch.put(column, key.bytes(), buffer.array());
                }
            }
        }
    }

    /**
     * Adds a key to an index through an atomic write batch
     * @param writeBatch rocksDB atomic operation
     * @param column index column
     * @param key index key
     * @param indexValue index value
     * @throws Exception
     */
    @VisibleForTesting
    void addToIndex(WriteBatch writeBatch, ColumnFamilyHandle column, Indexable key, Indexable indexValue)
            throws Exception {
        byte[] indexBytes = indexValue.bytes();

        byte[] dbResult = db.get(column, key.bytes());

        if (dbResult != null && Bytes.indexOf(dbResult, indexBytes) != -1) {// not found

            ByteBuffer buffer = ByteBuffer
                    .allocate(dbResult.length + indexBytes.length + (dbResult.length > 0 ? 1 : 0));// +1 delimiter
                                                                                                   // length
            buffer.put(dbResult);
            if (dbResult.length > 0) {
                // Means we add on the end of the current stream.
                // To be compatible with Hashes.read(byte[])
                buffer.put(delimiter);
            }
            buffer.put(indexBytes);
            writeBatch.put(column, key.bytes(), buffer.array());
        }
    }

    /**
     * @see PermanentPersistenceProvider#getTransaction(Hash)
     */
    @Override
    public TransactionViewModel getTransaction(Hash index) throws Exception {
        if (index == null) {
            return null;
        }
        byte[] dbResult = db.get(columnMap.get(TRANSACTION_COLUMN), index.bytes());
        if (dbResult != null && dbResult.length > 0) {
            return new TransactionViewModel(TransactionViewModel.trits(dbResult), Hash.NULL_HASH);
        }
        return null;
    }
    /**
     * @see PermanentPersistenceProvider#findAddress(Hash)
     */
    @Override
    public Hashes findAddress(Hash index) throws Exception {
        return getIndex(columnMap.get(ADDRESS_INDEX), index);
    }
    /**
     * @see PermanentPersistenceProvider#findBundle(Hash)
     */
    @Override
    public Hashes findBundle(Hash index) throws Exception {
        return getIndex(columnMap.get(BUNDLE_INDEX), index);
    }
    /**
     * @see PermanentPersistenceProvider#findTag(Hash)
     */
    @Override
    public Hashes findTag(Hash index) throws Exception {
        return getIndex(columnMap.get(TAG_INDEX), index);
    }
    /**
     * @see PermanentPersistenceProvider#findApprovee(Hash)
     */
    @Override
    public Hashes findApprovee(Hash index) throws Exception {
        return getIndex(columnMap.get(APPROVEE_INDEX), index);
    }

    private Hashes getIndex(ColumnFamilyHandle column, Indexable index) throws Exception {
        if (index == null) {
            return new Hashes();
        }
        Hashes found = new Hashes();
        byte[] result = db.get(column, index.bytes());
        if (result != null) {
            found.read(result);
        }
        return found;
    }

    private void initDB(String path, String logPath) throws Exception {
        try {
            try {
                RocksDB.loadLibrary();
            } catch (Exception e) {
                if (SystemUtils.IS_OS_WINDOWS) {
                    log.error("Error loading RocksDB library. Please ensure that "
                            + "Microsoft Visual C++ 2015 Redistributable Update 3 " + "is installed and updated");
                }
                throw e;
            }

            File pathToLogDir = Paths.get(logPath).toFile();
            if (!pathToLogDir.exists() || !pathToLogDir.isDirectory()) {
                boolean success = pathToLogDir.mkdir();
                if (!success) {
                    log.warn("Unable to make directory: {}", pathToLogDir);
                }
            }

            int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
            RocksEnv.getDefault().setBackgroundThreads(numThreads, Priority.HIGH).setBackgroundThreads(numThreads,
                    Priority.LOW);

            options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true).setDbLogDir(logPath)
                    .setMaxLogFileSize(SizeUnit.MB).setMaxManifestFileSize(SizeUnit.MB).setMaxOpenFiles(10000)
                    .setMaxBackgroundCompactions(1);

            options.setMaxSubcompactions(Runtime.getRuntime().availableProcessors());

            bloomFilter = new BloomFilter(BLOOM_FILTER_BITS_PER_KEY);

            BlockBasedTableConfig blockBasedTableConfig = new BlockBasedTableConfig().setFilter(bloomFilter);
            blockBasedTableConfig.setFilter(bloomFilter).setCacheNumShardBits(2).setBlockSizeDeviation(10)
                    .setBlockRestartInterval(16).setBlockCacheSize(cacheSize * SizeUnit.KB)
                    .setBlockCacheCompressedNumShardBits(10).setBlockCacheCompressedSize(32 * SizeUnit.KB);

            options.setAllowConcurrentMemtableWrite(true);

            MergeOperator mergeOperator = new StringAppendOperator();
            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions().setMergeOperator(mergeOperator)
                    .setTableFormatConfig(blockBasedTableConfig).setMaxWriteBufferNumber(2)
                    .setWriteBufferSize(2 * SizeUnit.MB);

            List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
            // Add default column family. Main motivation is to not change legacy code
            columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));

            columnFamilyDescriptors
                    .add(new ColumnFamilyDescriptor(TRANSACTION_COLUMN.getBytes("UTF-8"), columnFamilyOptions));
            // columnFamilyDescriptors.add(new ColumnFamilyDescriptor(COUNTER_INDEX.getBytes("UTF-8"),
            // columnFamilyOptions));
            columnFamilyDescriptors
                    .add(new ColumnFamilyDescriptor(BUNDLE_INDEX.getBytes("UTF-8"), columnFamilyOptions));
            columnFamilyDescriptors.add(new ColumnFamilyDescriptor(TAG_INDEX.getBytes("UTF-8"), columnFamilyOptions));
            columnFamilyDescriptors
                    .add(new ColumnFamilyDescriptor(ADDRESS_INDEX.getBytes("UTF-8"), columnFamilyOptions));
            columnFamilyDescriptors
                    .add(new ColumnFamilyDescriptor(APPROVEE_INDEX.getBytes("UTF-8"), columnFamilyOptions));

            db = RocksDB.open(options, path, columnFamilyDescriptors, columnFamilyHandles);
            db.enableFileDeletions(true);

            for (ColumnFamilyHandle columnHandler : columnFamilyHandles) {
                columnMap.put(new String(columnHandler.getName(), "UTF-8"), columnHandler);
            }

        } catch (Exception e) {
            IotaIOUtils.closeQuietly(db);
            throw e;
        }
    }

}
