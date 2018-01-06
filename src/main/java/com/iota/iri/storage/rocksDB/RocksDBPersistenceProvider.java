package com.iota.iri.storage.rocksDB;

import com.iota.iri.model.*;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.utils.IotaUtils;
import com.iota.iri.utils.Pair;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.LoggerFactory;

import javax.naming.OperationNotSupportedException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class RocksDBPersistenceProvider implements PersistenceProvider {

    /**
     * Split values that are above this byte length
     */
    //TODO current value is arbitrary, make configurable. Change only the multiplier so no unit test will fail.
    //size of Hash + 1 byte because of the delimiter
            //TODO test what happens when you persist an object larger than this
    public static final int BYTE_LENGTH_SPLIT = (Hash.SIZE_IN_BYTES + 1)*27;
    /**
     * The maximal byte size a value in a key-value pair will not be above
     * {@code BYTE_LENGTH_SPLIT} * ({@code MAX_BYTE_RATIO} + 1)
     */
    //TODO current value is arbitrary. please change or make configurable
    public static final double MAX_BYTE_RATIO = 0.75;
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(RocksDBPersistenceProvider.class);
    private static final int BLOOM_FILTER_BITS_PER_KEY = 10;
    private final List<String> columnFamilyNames = Arrays.asList(
            new String(RocksDB.DEFAULT_COLUMN_FAMILY),
            "transaction",
            "transaction-metadata",
            "milestone",
            "stateDiff",
            "address",
            "approvee",
            "bundle",
            "tag"
    );
    private final String dbPath;
    private final String logPath;
    private final int cacheSize;
    private final AtomicReference<Map<Class<?>, ColumnFamilyHandle>> classTreeMap = new AtomicReference<>();
    private final AtomicReference<Map<Class<?>, ColumnFamilyHandle>> metadataReference = new AtomicReference<>();
    private final SecureRandom seed = new SecureRandom();
    private ColumnFamilyHandle transactionHandle;
    private ColumnFamilyHandle transactionMetadataHandle;
    private ColumnFamilyHandle milestoneHandle;
    private ColumnFamilyHandle stateDiffHandle;
    private ColumnFamilyHandle addressHandle;
    private ColumnFamilyHandle approveeHandle;
    private ColumnFamilyHandle bundleHandle;
    private ColumnFamilyHandle tagHandle;
    private List<ColumnFamilyHandle> transactionGetList;
    private RocksDB db;
    private DBOptions options;
    private BloomFilter bloomFilter;
    private boolean available;

    public RocksDBPersistenceProvider(String dbPath, String logPath, int cacheSize) {
        this.dbPath = dbPath;
        this.logPath = logPath;
        this.cacheSize = cacheSize;

    }

    @Override
    public void init() throws Exception {
        log.info("Initializing Database Backend... ");
        initDB(dbPath, logPath);
        initClassTreeMap();
        available = true;
        log.info("RocksDB persistence provider initialized.");
    }

    @Override
    public boolean isAvailable() {
        return this.available;
    }


    private void initClassTreeMap() throws RocksDBException {
        Map<Class<?>, ColumnFamilyHandle> classMap = new HashMap<>();
        classMap.put(Transaction.class, transactionHandle);
        classMap.put(Milestone.class, milestoneHandle);
        classMap.put(StateDiff.class, stateDiffHandle);
        classMap.put(Address.class, addressHandle);
        classMap.put(Approvee.class, approveeHandle);
        classMap.put(Bundle.class, bundleHandle);
        classMap.put(Tag.class, tagHandle);
        classTreeMap.set(classMap);

        Map<Class<?>, ColumnFamilyHandle> metadataHashMap = new HashMap<>();
        metadataHashMap.put(Transaction.class, transactionMetadataHandle);
        metadataReference.set(metadataHashMap);

        /*
        counts.put(Transaction.class, getCountEstimate(Transaction.class));
        counts.put(Milestone.class, getCountEstimate(Milestone.class));
        counts.put(StateDiff.class, getCountEstimate(StateDiff.class));
        counts.put(Hashes.class, getCountEstimate(Hashes.class));
        */
    }

    @Override
    public void shutdown() {
        if (db != null) db.close();
        options.close();
        bloomFilter.close();
    }

    @Override
    public boolean save(Persistable model, Indexable index) throws OperationNotSupportedException, RocksDBException {
        ColumnFamilyHandle handle = classTreeMap.get().get(model.getClass());
        /*
        if( !db.keyMayExist(handle, index.bytes(), new StringBuffer()) ) {
            counts.put(thing.getClass(), counts.get(thing.getClass()) + 1);
        }
        */

        if (model.isSplittable()) {
            try (WriteBatch writeBatch = new WriteBatch();
                 WriteOptions writeOptions = new WriteOptions()) {
                saveSplittable(model, index, handle, writeBatch);
                db.write(writeOptions, writeBatch);
            }
        } else {
            db.put(handle, index.bytes(), model.bytes());
        }
        //TODO should metadata be only on the first index in case of splitBytes?
        ColumnFamilyHandle referenceHandle = metadataReference.get().get(model.getClass());
        if (referenceHandle != null) {
            db.put(referenceHandle, index.bytes(), model.metadata());
        }
        return true;
    }

    private void saveSplittable(Persistable model, Indexable index, ColumnFamilyHandle handle, WriteBatch writeBatch) throws OperationNotSupportedException {
        byte[] startIndexBytes = createSplitIndexBytes(index, 0);
        List<Pair<byte[], byte[]>> splitModelAndIndexPairs = createSplitModelAndIndexPairs(model, startIndexBytes);

        Set<byte[]> newIndexes = splitModelAndIndexPairs.stream()
                .map(pair -> pair.hi)
                .collect(Collectors.toSet());
        List<byte[]> oldIndexes = listKeyBytesThatHoldSplitValue(model.getClass(), index);
        Set<byte[]> oldBytes = oldIndexes.stream()
                .filter(oldIndex -> !IotaUtils.containsArray(newIndexes, oldIndex) )
                .collect(Collectors.toSet());

        for (byte[] oldByte : oldBytes) {
            writeBatch.remove(handle, oldByte);
        }
        for (Pair<byte[], byte[]> pair : splitModelAndIndexPairs) {
            writeBatch.put(handle, pair.hi, pair.low);
        }
    }

    private List<Pair<byte[], byte[]>> createSplitModelAndIndexPairs(Persistable model, byte[] startKey) throws OperationNotSupportedException {
        int keyOffsetIndex = startKey.length - 4;
        List<byte[]> splitModelBytes = model.splitBytes(BYTE_LENGTH_SPLIT);
        int size = splitModelBytes.size();
        ByteBuffer byteBuffer = ByteBuffer.wrap(startKey);
        int offset = byteBuffer.getInt(keyOffsetIndex);
        List<Pair<byte[], byte[]>> modelAndIndex = new ArrayList<>(size);
        for (byte[] modelBytes : splitModelBytes) {
            startKey = ArrayUtils.clone(startKey);
            ByteBuffer.wrap(startKey)
                    .putInt(keyOffsetIndex, offset++);
            modelAndIndex.add(new Pair<>(modelBytes, startKey));
        }
        return modelAndIndex;
    }

    private byte[] createSplitIndexBytes(Indexable index, int indexOffset) {
        byte[] bytes = index.bytes();
        return ArrayUtils.addAll(bytes,
                ByteBuffer.wrap(new byte[4])
                        .putInt(indexOffset)
                        .array());
    }

    @Override
    public void delete(Class<?> model, Indexable index) throws Exception {
        /*
        if( db.keyMayExist(classTreeMap.get().get(model), index.bytes(), new StringBuffer()) ) {
            counts.put(model, counts.get(model) + 1);
        }
        */
        delete((Class<? extends Persistable>) model, index, 0);
    }

    public void delete(Class<? extends Persistable> model, Indexable index, int offset) throws RocksDBException,
            IllegalAccessException, InstantiationException {
        ColumnFamilyHandle columnFamilyHandle = classTreeMap.get().get(model);
        if (model.newInstance().isSplittable()) {
            try (WriteBatch writeBatch = new WriteBatch();
                 WriteOptions writeOptions = new WriteOptions()) {
                List<byte[]> keyBytes = listKeyBytesThatHoldSplitValue(model, index);
                for (int i = offset; i < keyBytes.size(); ++i) {
                    byte[] indexBytes = keyBytes.get(i);
                    writeBatch.remove(columnFamilyHandle, indexBytes);
                }
                db.write(writeOptions, writeBatch);
                //Only good if there is a big range of deleted keys... Consider removing
                //See https://github.com/facebook/rocksdb/wiki/Delete-A-Range-Of-Keys
                db.compactRange(columnFamilyHandle, keyBytes.get(offset), keyBytes.get(keyBytes.size() - 1));
            }
        } else {
            db.delete(classTreeMap.get().get(model), index.bytes());
        }
    }

    @Override
    public boolean exists(Class<?> model, Indexable key) throws Exception {
        ColumnFamilyHandle handle = classTreeMap.get().get(model);
        return handle != null && db.get(handle, key.bytes()) != null;
    }

    @Override
    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception {
        final Indexable index;
        final Persistable object;
        RocksIterator iterator = db.newIterator(classTreeMap.get().get(model));
        iterator.seekToLast();
        if (iterator.isValid()) {
            object = (Persistable) model.newInstance();
            index = (Indexable) indexModel.newInstance();
            index.read(iterator.key());
            object.read(iterator.value());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
            if (referenceHandle != null) {
                object.readMetadata(db.get(referenceHandle, iterator.key()));
            }
        } else {
            object = null;
            index = null;
        }
        iterator.close();
        return new Pair<>(index, object);
    }

    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> model, Class<?> other) throws Exception {
        ColumnFamilyHandle handle = classTreeMap.get().get(model);
        ColumnFamilyHandle otherHandle = classTreeMap.get().get(other);
        RocksIterator iterator = db.newIterator(handle);
        Set<Indexable> indexables = new HashSet<>();
        for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            if (db.get(otherHandle, iterator.key()) == null) {
                indexables.add(new Hash(iterator.key()));
            }
        }
        iterator.close();
        return indexables;
    }

    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        return get((Class<? extends Persistable>) model, index, 0, Integer.MAX_VALUE);
    }

    /**
     * fetch from db an object splitted over several consecutive keys. The inclusive
     * {@code splitStart} and exclusive {@code splitEnd} will define the window from which you
     * fetch the values
     *
     * @param model      the type of the returned object
     * @param index      the start index. This is the only index the user is aware of.
     * @param splitStart the inclusive offset from the index to start fetching from
     * @param splitEnd   the exclusive offset from the
     * @return a {@link Persistable} object from the db
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws RocksDBException
     */
    public Persistable get(Class<? extends Persistable> model, Indexable index, int splitStart, int splitEnd) throws IllegalAccessException,
            InstantiationException, RocksDBException {
        if (splitStart >= splitEnd) {
            // You guys usually use "assert". However in java it is considered best practice to throw an exact exception
            // so it can be handled correctly. Especially when it comes to checking user inputs.
            // Besides the above, "assert" statement can be mistakenly turned off in Runtime by VM args.
            throw new IllegalArgumentException(String.format("When fetching an object that is splitBytes over several keys," +
                    "the start offset %d should be less than the end offset %d", splitStart, splitEnd));
        }
        if (model == null) {
            log.error("Must specify what kind of object you are trying to read.");
            return null;
        }

        if (index == null) {
            log.error("Trying to read from a null index.");
            return null;
        }

        Persistable object = model.newInstance();
        ColumnFamilyHandle columnFamilyHandle = classTreeMap.get().get(model);
        if (object.isSplittable()) {
            object = readSplitObject(object, index);
        }
        else {
            object.read(db.get(columnFamilyHandle, index == null ? new byte[0] : index.bytes()));
        }
        ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
        if (referenceHandle != null) {
            object.readMetadata(db.get(referenceHandle, index == null ? new byte[0] : index.bytes()));
        }
        return object;
    }


    @Override
    public boolean mayExist(Class<?> model, Indexable index) throws Exception {
        ColumnFamilyHandle handle = classTreeMap.get().get(model);
        return db.keyMayExist(handle, index.bytes(), new StringBuilder());
    }

    @Override
    public long count(Class<?> model) throws Exception {
        return getCountEstimate(model);
        //return counts.get(model);
    }

    private long getCountEstimate(Class<?> model) throws RocksDBException {
        ColumnFamilyHandle handle = classTreeMap.get().get(model);
        return db.getLongProperty(handle, "rocksdb.estimate-num-keys");
    }

    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        RocksIterator iterator;
        ColumnFamilyHandle handle = classTreeMap.get().get(modelClass);
        Set<Indexable> keys = new LinkedHashSet<>();
        if(handle != null) {
            iterator = db.newIterator(handle);
            try {
                iterator.seek(new Hash(value, 0, value.length).bytes());
                for(;
                    iterator.isValid() && Arrays.equals(Arrays.copyOf(iterator.key(), value.length), value);
                    iterator.next()) {
                    keys.add(new Hash(iterator.key()));
                }
            } finally {
                iterator.close();
            }
        }
        return keys;
    }

    private List<byte[]> listKeyBytesThatHoldSplitValue(Class<? extends Persistable> modelClass, Indexable index) {
        List<byte[]> keyBytes = new ArrayList<>();
        ColumnFamilyHandle columnFamilyHandle = classTreeMap.get().get(modelClass);
        if (columnFamilyHandle != null) {
            byte[] indexBytes = index.bytes();
            try (RocksIterator iterator = db.newIterator(columnFamilyHandle)) {
                iterator.seek(indexBytes);
                while (iterator.isValid()
                        && Arrays.equals(Arrays.copyOf(iterator.key(), indexBytes.length), indexBytes)) {
                    byte[] key = iterator.key();
                    keyBytes.add(key);
                    iterator.next();
                }
            }
        }
        return keyBytes;
    }

    private byte[] findLastKeyByteThatMayHoldSplitValue(ColumnFamilyHandle columnFamilyHandle, Indexable index) throws IotaUtils.OverFlowException {
        byte[] bytes = createSplitIndexBytes(index, 0);
        byte[] lastSplitKey = Arrays.copyOf(bytes, bytes.length);

        boolean keyMayExist = db.keyMayExist(columnFamilyHandle, bytes, new StringBuilder());
        while (keyMayExist) {
            bytes = IotaUtils.incrementArrayIntegerSuffix(bytes);
            StringBuilder stringBuilder = new StringBuilder();
            keyMayExist = db.keyMayExist(columnFamilyHandle, bytes, stringBuilder);
            if (keyMayExist) {
                lastSplitKey = Arrays.copyOf(bytes, bytes.length);
            }
        }

        return lastSplitKey;
    }

    private <T extends Persistable> T readSplitObject(T object, Indexable index) {
        List<byte[]> keyBytes = new ArrayList<>();
        ColumnFamilyHandle columnFamilyHandle = classTreeMap.get().get(object.getClass());
        if (columnFamilyHandle != null) {
            byte[] indexBytes = index.bytes();
            try (RocksIterator iterator = db.newIterator(columnFamilyHandle)) {
                iterator.seek(indexBytes);
                while (iterator.isValid()
                        && Arrays.equals(Arrays.copyOf(iterator.key(), indexBytes.length), indexBytes)) {
                    byte[] key = iterator.key();
                    keyBytes.add(key);
                    byte[] value = iterator.value();
                    object.read(value);
                    iterator.next();
                }
            }
        }
        return object;
    }

    @Override
    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        Set<Indexable> hashes = keysStartingWith(model, key);
        Indexable out;
        if (hashes.size() == 1) {
            out = (Indexable) hashes.toArray()[0];
        } else if (hashes.size() > 1) {
            //Is random behavior correct?
            out = (Indexable) hashes.toArray()[seed.nextInt(hashes.size())];
        } else {
            out = null;
        }
        return get(model, out);
    }

    @Override
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        RocksIterator iterator = db.newIterator(classTreeMap.get().get(model));
        final Persistable object;
        final Indexable indexable;
        iterator.seek(index.bytes());
        iterator.next();
        if (iterator.isValid()) {
            object = (Persistable) model.newInstance();
            indexable = index.getClass().newInstance();
            indexable.read(iterator.key());
            object.read(iterator.value());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
            if (referenceHandle != null) {
                object.readMetadata(db.get(referenceHandle, iterator.key()));
            }
        } else {
            object = null;
            indexable = null;
        }
        iterator.close();
        return new Pair<>(indexable, object);
    }

    @Override
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        RocksIterator iterator = db.newIterator(classTreeMap.get().get(model));
        final Persistable object;
        final Indexable indexable;
        iterator.seek(index.bytes());
        iterator.prev();
        if (iterator.isValid()) {
            object = (Persistable) model.newInstance();
            object.read(iterator.value());
            indexable = (Indexable) index.getClass().newInstance();
            indexable.read(iterator.key());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
            if (referenceHandle != null) {
                object.readMetadata(db.get(referenceHandle, iterator.key()));
            }
        } else {
            object = null;
            indexable = null;
        }
        iterator.close();
        return new Pair<>(indexable, object);
    }

    @Override
    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> index) throws Exception {
        RocksIterator iterator = db.newIterator(classTreeMap.get().get(model));
        final Persistable object;
        final Indexable indexable;
        iterator.seekToFirst();
        if (iterator.isValid()) {
            object = (Persistable) model.newInstance();
            object.read(iterator.value());
            indexable = (Indexable) index.newInstance();
            indexable.read(iterator.key());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
            if (referenceHandle != null) {
                object.readMetadata(db.get(referenceHandle, iterator.key()));
            }
        } else {
            object = null;
            indexable = null;
        }
        iterator.close();
        return new Pair<>(indexable, object);
    }

    /**
     * Append an an entry at index. If entry is splittable add 4 bytes to the index
     * and add with incrementing offsets.
     *
     * @param model model to be persisted
     * @param index index to persist the object
     * @return true if the data was merged with an existing entry at {@code index}
     * @throws Exception
     */
    public boolean merge(Persistable model, Indexable index) throws Exception {
        boolean exists;
        ColumnFamilyHandle handle = classTreeMap.get().get(model.getClass());
        if (model.isSplittable()) {
            try (WriteBatch writeBatch = new WriteBatch();
                 WriteOptions writeOptions = new WriteOptions()) {
                exists = mergeSplittable(model, index, handle, writeBatch);
                db.write(writeOptions, writeBatch);
            }
        } else {
            exists = mayExist(model.getClass(), index);
            db.merge(handle, index.bytes(), model.bytes());
        }
        return exists;
    }

    private boolean mergeSplittable(Persistable model, Indexable index, ColumnFamilyHandle handle, WriteBatch writeBatch) throws RocksDBException, OperationNotSupportedException,
            IotaUtils.OverFlowException {
        byte[] lastSplitKey = findLastKeyByteThatMayHoldSplitValue(handle, index);

        byte[] dbEntry = db.get(handle, lastSplitKey);
        byte[] newKey = determineMergeStartPoint(dbEntry, lastSplitKey, writeBatch);
        if (newKey == null) {
            log.error("Merge was not performed");
            return false;
        }
        List<Pair<byte[], byte[]>> splitModelAndIndexPairs = createSplitModelAndIndexPairs(model, newKey);
        for (Pair<byte[], byte[]> pair : splitModelAndIndexPairs) {
            writeBatch.merge(handle, pair.hi, pair.low);
        }
        return dbEntry!=null && Arrays.equals(newKey, lastSplitKey);
    }

    private byte[] determineMergeStartPoint(byte[] dbEntry, byte[] lastSplitKey, WriteBatch writeBatch) throws RocksDBException {
        byte[] newKey = ArrayUtils.clone(lastSplitKey);
        if (dbEntry != null && dbEntry.length >= MAX_BYTE_RATIO * BYTE_LENGTH_SPLIT) {
            try {
                newKey = IotaUtils.incrementArrayIntegerSuffix(newKey);
            } catch (Exception e) {
                log.error("Can't find merge start point, e");
                return null;
            }
        }

        return newKey;
    }

    @Override
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        try (WriteBatch writeBatch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions()) {
            for (Pair<Indexable, Persistable> entry : models) {
                Indexable key = entry.low;
                Persistable value = entry.hi;
                ColumnFamilyHandle handle = classTreeMap.get().get(value.getClass());
                ColumnFamilyHandle referenceHandle = metadataReference.get().get(value.getClass());
                if (value.merge()) {
                    if (value.isSplittable()) {
                        mergeSplittable(value, key, handle, writeBatch);
                    }
                    else {
                        writeBatch.merge(handle, key.bytes(), value.bytes());
                    }
                }
                else {
                    if (value.isSplittable()) {
                        saveSplittable(value, key, handle, writeBatch);
                    }
                    else {
                        writeBatch.put(handle, key.bytes(), value.bytes());
                    }
                }
                if (referenceHandle != null) {
                    writeBatch.put(referenceHandle, key.bytes(), value.metadata());
                }
            }
            db.write(writeOptions, writeBatch);
        }
        return true;
    }

    @Override
    public void clear(Class<?> column) throws Exception {
        flushHandle(classTreeMap.get().get(column));
    }

    @Override
    public void clearMetadata(Class<?> column) throws Exception {
        flushHandle(metadataReference.get().get(column));
    }

    private void flushHandle(ColumnFamilyHandle handle) throws RocksDBException {
        List<byte[]> itemsToDelete = new ArrayList<>();
        RocksIterator iterator = db.newIterator(handle);
        for (iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
            itemsToDelete.add(iterator.key());
        }
        iterator.close();
        if (itemsToDelete.size() > 0) {
            log.info("Flushing flags. Amount to delete: " + itemsToDelete.size());
        }
        for (byte[] itemToDelete : itemsToDelete) {
            db.delete(handle, itemToDelete);
        }
    }


    @Override
    public boolean update(Persistable thing, Indexable index, String item) throws Exception {
        ColumnFamilyHandle referenceHandle = metadataReference.get().get(thing.getClass());
        if (referenceHandle != null) {
            db.put(referenceHandle, index.bytes(), thing.metadata());
        }
        return false;
    }

    public void createBackup(String path) throws RocksDBException {
        Env env;
        BackupableDBOptions backupableDBOptions;
        BackupEngine backupEngine;
        env = Env.getDefault();
        backupableDBOptions = new BackupableDBOptions(path);
        try {
            backupEngine = BackupEngine.open(env, backupableDBOptions);
            backupEngine.createNewBackup(db, true);
            backupEngine.close();
        } finally {
            env.close();
            backupableDBOptions.close();
        }
    }

    public void restoreBackup(String path, String logPath) throws Exception {
        Env env;
        BackupableDBOptions backupableDBOptions;
        BackupEngine backupEngine;
        env = Env.getDefault();
        backupableDBOptions = new BackupableDBOptions(path);
        backupEngine = BackupEngine.open(env, backupableDBOptions);
        shutdown();
        try (final RestoreOptions restoreOptions = new RestoreOptions(false)) {
            backupEngine.restoreDbFromLatestBackup(path, logPath, restoreOptions);
        } finally {
            backupEngine.close();
        }
        backupableDBOptions.close();
        env.close();
        initDB(path, logPath);
    }

    private void initDB(String path, String logPath) throws Exception {
        try {
            RocksDB.loadLibrary();
        } catch (Exception e) {
            if (SystemUtils.IS_OS_WINDOWS) {
                log.error("Error loading RocksDB library. " +
                        "Please ensure that " +
                        "Microsoft Visual C++ 2015 Redistributable Update 3 " +
                        "is installed and updated");
            }
            throw e;
        }
        Thread.yield();

        File pathToLogDir = Paths.get(logPath).toFile();
        if (!pathToLogDir.exists() || !pathToLogDir.isDirectory()) {
            pathToLogDir.mkdir();
        }

        RocksEnv.getDefault()
                .setBackgroundThreads(Runtime.getRuntime().availableProcessors() / 2, RocksEnv.FLUSH_POOL)
                .setBackgroundThreads(Runtime.getRuntime().availableProcessors() / 2, RocksEnv.COMPACTION_POOL)
        /*
                .setBackgroundThreads(Runtime.getRuntime().availableProcessors())
        */
        ;

        options = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setDbLogDir(logPath)
                .setMaxLogFileSize(SizeUnit.MB)
                .setMaxManifestFileSize(SizeUnit.MB)
                .setMaxOpenFiles(10000)
                .setMaxBackgroundCompactions(1)

                /*
                .setBytesPerSync(4 * SizeUnit.MB)
                .setMaxTotalWalSize(16 * SizeUnit.MB)
                */
        ;
        options.setMaxSubcompactions(Runtime.getRuntime().availableProcessors());

        bloomFilter = new BloomFilter(BLOOM_FILTER_BITS_PER_KEY);
        PlainTableConfig plainTableConfig = new PlainTableConfig();
        BlockBasedTableConfig blockBasedTableConfig = new BlockBasedTableConfig().setFilter(bloomFilter);
        blockBasedTableConfig
                .setFilter(bloomFilter)
                .setCacheNumShardBits(2)
                .setBlockSizeDeviation(10)
                .setBlockRestartInterval(16)
                .setBlockCacheSize(cacheSize * SizeUnit.KB)
                .setBlockCacheCompressedNumShardBits(10)
                .setBlockCacheCompressedSize(32 * SizeUnit.KB)
                /*
                .setHashIndexAllowCollision(true)
                .setCacheIndexAndFilterBlocks(true)
                */
        ;
        options.setAllowConcurrentMemtableWrite(true);

        MemTableConfig hashSkipListMemTableConfig = new HashSkipListMemTableConfig()
                .setHeight(9)
                .setBranchingFactor(9)
                .setBucketCount(2 * SizeUnit.MB);
        MemTableConfig hashLinkedListMemTableConfig = new HashLinkedListMemTableConfig().setBucketCount(100000);
        MemTableConfig vectorTableConfig = new VectorMemTableConfig().setReservedSize(10000);
        MemTableConfig skipListMemTableConfig = new SkipListMemTableConfig();


        MergeOperator mergeOperator = new StringAppendOperator();
        ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions()
                .setMergeOperator(mergeOperator)
                .setTableFormatConfig(blockBasedTableConfig)
                .setMaxWriteBufferNumber(2)
                .setWriteBufferSize(2 * SizeUnit.MB)
                /*
                .setCompactionStyle(CompactionStyle.UNIVERSAL)
                .setCompressionType(CompressionType.SNAPPY_COMPRESSION)
                */;
        //columnFamilyOptions.setMemTableConfig(hashSkipListMemTableConfig);

        List<ColumnFamilyHandle> familyHandles = new ArrayList<>();
        //List<ColumnFamilyDescriptor> familyDescriptors = columnFamilyNames.stream().map(name -> new ColumnFamilyDescriptor(name.getBytes(), columnFamilyOptions)).collect(Collectors.toList());
        //familyDescriptors.add(0, new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));

        List<ColumnFamilyDescriptor> columnFamilyDescriptors = columnFamilyNames.stream().map(name -> new ColumnFamilyDescriptor(name.getBytes(), columnFamilyOptions)).collect(Collectors.toList());
        //fillMissingColumns(columnFamilyDescriptors, familyHandles, path);
        db = RocksDB.open(options, path, columnFamilyDescriptors, familyHandles);

        fillmodelColumnHandles(familyHandles);
    }


    private void fillmodelColumnHandles(List<ColumnFamilyHandle> familyHandles) throws Exception {
        int i = 0;
        transactionHandle = familyHandles.get(++i);
        transactionMetadataHandle = familyHandles.get(++i);
        milestoneHandle = familyHandles.get(++i);
        stateDiffHandle = familyHandles.get(++i);
        addressHandle = familyHandles.get(++i);
        approveeHandle = familyHandles.get(++i);
        bundleHandle = familyHandles.get(++i);
        tagHandle = familyHandles.get(++i);
        //hashesHandle = familyHandles.get(++i);

        for (; ++i < familyHandles.size(); ) {
            db.dropColumnFamily(familyHandles.get(i));
        }

        transactionGetList = new ArrayList<>();
        for (i = 1; i < 5; i++) {
            transactionGetList.add(familyHandles.get(i));
        }
    }

    private void fillMissingColumns(List<ColumnFamilyDescriptor> familyDescriptors, List<ColumnFamilyHandle> familyHandles, String path) throws Exception {
        List<ColumnFamilyDescriptor> columnFamilies = RocksDB.listColumnFamilies(new Options().setCreateIfMissing(true), path)
                .stream()
                .map(b -> new ColumnFamilyDescriptor(b, new ColumnFamilyOptions()))
                .collect(Collectors.toList());
        columnFamilies.add(0, familyDescriptors.get(0));
        List<ColumnFamilyDescriptor> missingFromDatabase = familyDescriptors.stream().filter(d -> columnFamilies.stream().filter(desc -> new String(desc.columnFamilyName()).equals(new String(d.columnFamilyName()))).toArray().length == 0).collect(Collectors.toList());
        List<ColumnFamilyDescriptor> missingFromDescription = columnFamilies.stream().filter(d -> familyDescriptors.stream().filter(desc -> new String(desc.columnFamilyName()).equals(new String(d.columnFamilyName()))).toArray().length == 0).collect(Collectors.toList());
        if (missingFromDatabase.size() != 0) {
            missingFromDatabase.remove(familyDescriptors.get(0));
            db = RocksDB.open(options, path, columnFamilies, familyHandles);
            for (ColumnFamilyDescriptor description : missingFromDatabase) {
                addColumnFamily(description.columnFamilyName(), db);
            }
            db.close();
        }
        if (missingFromDescription.size() != 0) {
            missingFromDescription.forEach(familyDescriptors::add);
        }
    }

    private void addColumnFamily(byte[] familyName, RocksDB db) throws RocksDBException {
        final ColumnFamilyHandle columnFamilyHandle = db.createColumnFamily(
                new ColumnFamilyDescriptor(familyName,
                        new ColumnFamilyOptions()));
        assert (columnFamilyHandle != null);
    }

    @FunctionalInterface
    private interface MyFunction<T, R> {
        R apply(T t) throws Exception;
    }

    @FunctionalInterface
    private interface IndexFunction<T> {
        void apply(T t) throws Exception;
    }

    @FunctionalInterface
    private interface DoubleFunction<T, I> {
        void apply(T t, I i) throws Exception;
    }

    @FunctionalInterface
    private interface MyRunnable<R> {
        R run() throws Exception;

    }
}
