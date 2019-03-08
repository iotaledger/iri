package com.iota.iri.storage.rocksDB;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.utils.IotaIOUtils;
import com.iota.iri.utils.Pair;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SystemUtils;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class RocksDBPersistenceProvider implements PersistenceProvider {

    private static final Logger log = LoggerFactory.getLogger(RocksDBPersistenceProvider.class);
    private static final int BLOOM_FILTER_BITS_PER_KEY = 10;

    private static final Pair<Indexable, Persistable> PAIR_OF_NULLS = new Pair<>(null, null);

    private final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
    private final SecureRandom seed = new SecureRandom();

    private final String dbPath;
    private final String logPath;
    private final int cacheSize;
    private final Map<String, Class<? extends Persistable>> columnFamilies;
    private final Map.Entry<String, Class<? extends Persistable>> metadataColumnFamily;

    private Map<Class<?>, ColumnFamilyHandle> classTreeMap;
    private Map<Class<?>, ColumnFamilyHandle> metadataReference = Collections.emptyMap();

    private RocksDB db;
    // DBOptions is only used in initDB(). However, it is closeable - so we keep a reference for shutdown.
    private DBOptions options;
    private BloomFilter bloomFilter;
    private boolean available;

    private static final String txnCountFileName = "TXN_COUNT";
    private AtomicLong txnCount = new AtomicLong(0);
    private File txnCountFile;

    public RocksDBPersistenceProvider(String dbPath, String logPath, int cacheSize,
                                      Map<String, Class<? extends Persistable>> columnFamilies,
                                      Map.Entry<String, Class<? extends Persistable>> metadataColumnFamily) {
        this.dbPath = dbPath;
        this.logPath = logPath;
        this.cacheSize = cacheSize;
        this.columnFamilies = columnFamilies;
        this.metadataColumnFamily = metadataColumnFamily;

    }

    @Override
    public void init() throws Exception {
        log.info("Initializing Database on " + dbPath);
        initDB(dbPath, logPath, columnFamilies);
        initTxnsCount(dbPath);
        available = true;
        log.info("RocksDB persistence provider initialized.");
    }

    @Override
    public boolean isAvailable() {
        return this.available;
    }


    @Override
    public void shutdown() {
        for (final ColumnFamilyHandle columnFamilyHandle : columnFamilyHandles) {
            IotaIOUtils.closeQuietly(columnFamilyHandle);
        }
        IotaIOUtils.closeQuietly(db, options, bloomFilter);
    }

    @Override
    public boolean save(Persistable thing, Indexable index) throws Exception {
        ColumnFamilyHandle handle = classTreeMap.get(thing.getClass());
        db.put(handle, index.bytes(), thing.bytes());

        ColumnFamilyHandle referenceHandle = metadataReference.get(thing.getClass());
        if (referenceHandle != null) {
            db.put(referenceHandle, index.bytes(), thing.metadata());
        }
        return true;
    }

    @Override
    public void delete(Class<?> model, Indexable index) throws Exception {
        db.delete(classTreeMap.get(model), index.bytes());
    }

    @Override
    public boolean exists(Class<?> model, Indexable key) throws Exception {
        ColumnFamilyHandle handle = classTreeMap.get(model);
        return handle != null && db.get(handle, key.bytes()) != null;
    }

    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> model, Class<?> other) throws Exception {
        ColumnFamilyHandle handle = classTreeMap.get(model);
        ColumnFamilyHandle otherHandle = classTreeMap.get(other);

        try (RocksIterator iterator = db.newIterator(handle)) {
            Set<Indexable> indexables = null;

            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                if (db.get(otherHandle, iterator.key()) == null) {
                    indexables = indexables == null ? new HashSet<>() : indexables;
                    indexables.add(HashFactory.GENERIC.create(model, iterator.key()));
                }
            }
            return indexables == null ? Collections.emptySet() : Collections.unmodifiableSet(indexables);
        }
    }

    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        Persistable object = (Persistable) model.newInstance();
        object.read(db.get(classTreeMap.get(model), index == null ? new byte[0] : index.bytes()));

        ColumnFamilyHandle referenceHandle = metadataReference.get(model);
        if (referenceHandle != null) {
            object.readMetadata(db.get(referenceHandle, index == null ? new byte[0] : index.bytes()));
        }
        return object;
    }

    @Override
    public boolean mayExist(Class<?> model, Indexable index) {
        ColumnFamilyHandle handle = classTreeMap.get(model);
        return db.keyMayExist(handle, index.bytes(), new StringBuilder());
    }

    @Override
    public long count(Class<?> model) throws Exception {
        return getCountEstimate(model);
    }

    private long getCountEstimate(Class<?> model) throws RocksDBException {
        ColumnFamilyHandle handle = classTreeMap.get(model);
        return db.getLongProperty(handle, "rocksdb.estimate-num-keys");
    }

    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        Objects.requireNonNull(value, "value byte[] cannot be null");
        ColumnFamilyHandle handle = classTreeMap.get(modelClass);
        Set<Indexable> keys = null;
        if (handle != null) {
            try (RocksIterator iterator = db.newIterator(handle)) {
                iterator.seek(HashFactory.GENERIC.create(modelClass, value, 0, value.length).bytes());

                byte[] found;
                while (iterator.isValid() && keyStartsWithValue(value, found = iterator.key())) {
                    keys = keys == null ? new HashSet<>() : keys;
                    keys.add(HashFactory.GENERIC.create(modelClass, found));
                    iterator.next();
                }
            }
        }
        return keys == null ? Collections.emptySet() : Collections.unmodifiableSet(keys);
    }

    /**
     * @param value What we are looking for.
     * @param key   The bytes we are searching in.
     * @return true If the {@code key} starts with the {@code value}.
     */
    private static boolean keyStartsWithValue(byte[] value, byte[] key) {
        if (key == null || key.length < value.length) {
            return false;
        }
        for (int n = 0; n < value.length; n++) {
            if (value[n] != key[n]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        Set<Indexable> hashes = keysStartingWith(model, key);
        if (hashes.isEmpty()) {
            return get(model, null);
        }
        if (hashes.size() == 1) {
            return get(model, (Indexable) hashes.toArray()[0]);
        }
        return get(model, (Indexable) hashes.toArray()[seed.nextInt(hashes.size())]);
    }

    private Pair<Indexable, Persistable> modelAndIndex(Class<?> model, Class<? extends Indexable> index, RocksIterator iterator)
        throws InstantiationException, IllegalAccessException, RocksDBException {

        if (!iterator.isValid()) {
            return PAIR_OF_NULLS;
        }

        Indexable indexable = index.newInstance();
        indexable.read(iterator.key());

        Persistable object = (Persistable) model.newInstance();
        object.read(iterator.value());

        ColumnFamilyHandle referenceHandle = metadataReference.get(model);
        if (referenceHandle != null) {
            object.readMetadata(db.get(referenceHandle, iterator.key()));
        }
        return new Pair<>(indexable, object);
    }

    @Override
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        try (RocksIterator iterator = db.newIterator(classTreeMap.get(model))) {
            iterator.seek(index.bytes());
            iterator.next();
            return modelAndIndex(model, index.getClass(), iterator);
        }
    }

    @Override
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        try (RocksIterator iterator = db.newIterator(classTreeMap.get(model))) {
            iterator.seek(index.bytes());
            iterator.prev();
            return modelAndIndex(model, index.getClass(), iterator);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception {
        try (RocksIterator iterator = db.newIterator(classTreeMap.get(model))) {
            iterator.seekToLast();
            return modelAndIndex(model, (Class<Indexable>) indexModel, iterator);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> index) throws Exception {
        try (RocksIterator iterator = db.newIterator(classTreeMap.get(model))) {
            iterator.seekToFirst();
            return modelAndIndex(model, (Class<Indexable>) index, iterator);
        }
    }

    // 2018 March 28 - Unused code
    public boolean merge(Persistable model, Indexable index) throws Exception {
        boolean exists = mayExist(model.getClass(), index);
        db.merge(classTreeMap.get(model.getClass()), index.bytes(), model.bytes());
        return exists;
    }

    @Override
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        try (WriteBatch writeBatch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions()) {

            for (Pair<Indexable, Persistable> entry : models) {

                Indexable key = entry.low;
                Persistable value = entry.hi;

                ColumnFamilyHandle handle = classTreeMap.get(value.getClass());
                ColumnFamilyHandle referenceHandle = metadataReference.get(value.getClass());

                if (value.merge()) {
                    writeBatch.merge(handle, key.bytes(), value.bytes());
                } else {
                    writeBatch.put(handle, key.bytes(), value.bytes());
                }
                if (referenceHandle != null) {
                    writeBatch.put(referenceHandle, key.bytes(), value.metadata());
                }
            }

            db.write(writeOptions, writeBatch);
            return true;
        }
    }

    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models)
            throws Exception {
        if (CollectionUtils.isNotEmpty(models)) {
            try (WriteBatch writeBatch = new WriteBatch()) {
                models.forEach(entry -> {
                    Indexable indexable = entry.low;
                    byte[] keyBytes = indexable.bytes();
                    ColumnFamilyHandle handle = classTreeMap.get(entry.hi);
                    writeBatch.remove(handle, keyBytes);
                    ColumnFamilyHandle metadataHandle = metadataReference.get(entry.hi);
                    if (metadataHandle != null) {
                        writeBatch.remove(metadataHandle, keyBytes);
                    }
                });

                try(WriteOptions writeOptions = new WriteOptions()
                        //We are explicit about what happens if the node reboots before a flush to the db
                        .setDisableWAL(false)
                        //We want to make sure deleted data was indeed deleted
                        .setSync(true)) {
                    db.write(writeOptions, writeBatch);
                }
            }
        }
    }

    @Override
    public void clear(Class<?> column) throws Exception {
        log.info("Deleting: {} entries", column.getSimpleName());
        flushHandle(classTreeMap.get(column));
    }

    @Override
    public void clearMetadata(Class<?> column) throws Exception {
        log.info("Deleting: {} metadata", column.getSimpleName());
        flushHandle(metadataReference.get(column));
    }

    private void flushHandle(ColumnFamilyHandle handle) throws RocksDBException {
        List<byte[]> itemsToDelete = new ArrayList<>();
        try (RocksIterator iterator = db.newIterator(handle)) {

            for (iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
                itemsToDelete.add(iterator.key());
            }
        }
        if (itemsToDelete.size() > 0) {
            log.info("Amount to delete: " + itemsToDelete.size());
        }
        int counter = 0;
        for (byte[] itemToDelete : itemsToDelete) {
            if (++counter % 10000 == 0) {
                log.info("Deleted: {}", counter);
            }
            db.delete(handle, itemToDelete);
        }
    }

    @Override
    public boolean update(Persistable thing, Indexable index, String item) throws Exception {
        ColumnFamilyHandle referenceHandle = metadataReference.get(thing.getClass());
        if (referenceHandle != null) {
            db.put(referenceHandle, index.bytes(), thing.metadata());
        }
        return false;
    }

    // 2018 March 28 - Unused Code
    public void createBackup(String path) throws RocksDBException {
        try (Env env = Env.getDefault();
             BackupableDBOptions backupableDBOptions = new BackupableDBOptions(path);
             BackupEngine backupEngine = BackupEngine.open(env, backupableDBOptions)) {

            backupEngine.createNewBackup(db, true);
        }
    }

    // 2018 March 28 - Unused Code
    public void restoreBackup(String path, String logPath) throws Exception {
        try (Env env = Env.getDefault();
             BackupableDBOptions backupableDBOptions = new BackupableDBOptions(path)) {

            // shutdown after successful creation of env and backupable ...
            shutdown();

            try (BackupEngine backupEngine = BackupEngine.open(env, backupableDBOptions);
                 RestoreOptions restoreOptions = new RestoreOptions(false)) {

                backupEngine.restoreDbFromLatestBackup(path, logPath, restoreOptions);
            }
        }
        initDB(path, logPath, columnFamilies);
    }

    private void initDB(String path, String logPath, Map<String, Class<? extends Persistable>> columnFamilies) {
        try {
            try {
                RocksDB.loadLibrary();
            } catch (Exception e) {
                if (SystemUtils.IS_OS_WINDOWS) {
                    log.error("Error loading RocksDB library. Please ensure that " +
                        "Microsoft Visual C++ 2015 Redistributable Update 3 " +
                        "is installed and updated");
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
            RocksEnv.getDefault()
                .setBackgroundThreads(numThreads, RocksEnv.FLUSH_POOL)
                .setBackgroundThreads(numThreads, RocksEnv.COMPACTION_POOL);

            options = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setDbLogDir(logPath)
                .setMaxLogFileSize(SizeUnit.MB)
                .setMaxManifestFileSize(SizeUnit.MB)
                .setMaxOpenFiles(10000)
                .setMaxBackgroundCompactions(1);

            options.setMaxSubcompactions(Runtime.getRuntime().availableProcessors());

            bloomFilter = new BloomFilter(BLOOM_FILTER_BITS_PER_KEY);

            BlockBasedTableConfig blockBasedTableConfig = new BlockBasedTableConfig().setFilter(bloomFilter);
            blockBasedTableConfig
                .setFilter(bloomFilter)
                .setCacheNumShardBits(2)
                .setBlockSizeDeviation(10)
                .setBlockRestartInterval(16)
                .setBlockCacheSize(cacheSize * SizeUnit.KB)
                .setBlockCacheCompressedNumShardBits(10)
                .setBlockCacheCompressedSize(32 * SizeUnit.KB);

            options.setAllowConcurrentMemtableWrite(true);

            MergeOperator mergeOperator = new StringAppendOperator();
            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions()
                .setMergeOperator(mergeOperator)
                .setTableFormatConfig(blockBasedTableConfig)
                .setMaxWriteBufferNumber(2)
                .setWriteBufferSize(2 * SizeUnit.MB);

            List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
            //Add default column family. Main motivation is to not change legacy code
            columnFamilyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
            for (String name : columnFamilies.keySet()) {
                columnFamilyDescriptors.add(new ColumnFamilyDescriptor(name.getBytes(), columnFamilyOptions));
            }
            // metadata descriptor is always last
            if (metadataColumnFamily != null) {
                columnFamilyDescriptors.add(
                        new ColumnFamilyDescriptor(metadataColumnFamily.getKey().getBytes(), columnFamilyOptions));
                metadataReference = new HashMap<>();
            }


            db = RocksDB.open(options, path, columnFamilyDescriptors, columnFamilyHandles);
            db.enableFileDeletions(true);

            initClassTreeMap(columnFamilyDescriptors);

        } catch (Exception e) {
            log.error("Error while initializing RocksDb", e);
            IotaIOUtils.closeQuietly(db);
        } finally {
            options.close();
//            mergeOperator.close();
//            columnFamilyDescriptors.close();
        }
    }

    private void initClassTreeMap(List<ColumnFamilyDescriptor> columnFamilyDescriptors) throws Exception {
        Map<Class<?>, ColumnFamilyHandle> classMap = new LinkedHashMap<>();
        String mcfName = metadataColumnFamily == null ? "" : metadataColumnFamily.getKey();
        //skip default column
        int i = 1;
        for (; i < columnFamilyDescriptors.size(); i++) {

            String name = new String(columnFamilyDescriptors.get(i).columnFamilyName());
            if (name.equals(mcfName)) {
                Map<Class<?>, ColumnFamilyHandle> metadataRef = new HashMap<>();
                metadataRef.put(metadataColumnFamily.getValue(), columnFamilyHandles.get(i));
                metadataReference = MapUtils.unmodifiableMap(metadataRef);
            }
            else {
                classMap.put(columnFamilies.get(name), columnFamilyHandles.get(i));
            }
        }

        for (; ++i < columnFamilyHandles.size(); ) {
            db.dropColumnFamily(columnFamilyHandles.get(i));
        }

        classTreeMap = MapUtils.unmodifiableMap(classMap);
    }

    // 2018 March 28 - Unused Code
    private void fillMissingColumns(List<ColumnFamilyDescriptor> familyDescriptors, String path) throws Exception {

        List<ColumnFamilyDescriptor> columnFamilies = RocksDB.listColumnFamilies(new Options().setCreateIfMissing(true), path)
            .stream()
            .map(b -> new ColumnFamilyDescriptor(b, new ColumnFamilyOptions()))
            .collect(Collectors.toList());

        columnFamilies.add(0, familyDescriptors.get(0));

        List<ColumnFamilyDescriptor> missingFromDatabase = familyDescriptors.stream().filter(d -> columnFamilies.stream().filter(desc -> new String(desc.columnFamilyName()).equals(new String(d.columnFamilyName()))).toArray().length == 0).collect(Collectors.toList());
        List<ColumnFamilyDescriptor> missingFromDescription = columnFamilies.stream().filter(d -> familyDescriptors.stream().filter(desc -> new String(desc.columnFamilyName()).equals(new String(d.columnFamilyName()))).toArray().length == 0).collect(Collectors.toList());

        if (missingFromDatabase.size() != 0) {
            missingFromDatabase.remove(familyDescriptors.get(0));

            try (RocksDB rocksDB = db = RocksDB.open(options, path, columnFamilies, columnFamilyHandles)) {
                for (ColumnFamilyDescriptor description : missingFromDatabase) {
                    addColumnFamily(description.columnFamilyName(), rocksDB);
                }
            }
        }
        if (missingFromDescription.size() != 0) {
            familyDescriptors.addAll(missingFromDescription);
        }
    }

    // 2018 March 28 - Unused Code
    private void addColumnFamily(byte[] familyName, RocksDB db) throws RocksDBException {
        try(final ColumnFamilyHandle columnFamilyHandle = db.createColumnFamily(
            new ColumnFamilyDescriptor(familyName, new ColumnFamilyOptions()))) {

            assert (columnFamilyHandle != null);
        }
    }

    private void initTxnsCount(String path) throws IOException {
        String filePath = path + "/" + txnCountFileName;
        txnCountFile = new File(filePath);

        if (!txnCountFile.exists()) {
            boolean b = txnCountFile.createNewFile();
            if (!b) {
                log.error("Creating TXN_COUNT file failed.");
            }
        }

        FileInputStream fis = new FileInputStream(txnCountFile);
        long count;
        if (fis.available() == 0) {
            count = 0;
        } else {
            // TODO: judge the count whether or not right.
            byte[] bytes = new byte[Long.BYTES];
            int c = fis.read(bytes);

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.put(bytes);
            buffer.flip(); //need flip
            count =  buffer.getLong();
        }
        txnCount.set(count);
        fis.close();
    }

    @Override
    public synchronized void addTxnCount(long count) {
        try {
            FileOutputStream fos = new FileOutputStream(txnCountFile);
            fos.write(ByteBuffer.allocate(Long.BYTES).putLong(txnCount.addAndGet(count)).array());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Writing to TX_COUNT filed");
        }
    }

    @Override
    public long getTotalTxns() {
        return txnCount.get();
    }

    @Override
    public List<Hash> getSiblings(Hash block) {
        return Collections.emptyList();
    }

    @Override
    public void buildGraph() {

    }

    @Override
    public void computeScore() {

    }

    @Override
    public Hash getPivotalHash(int depth) {
        return null;
    }

    @Override
    public List<Hash> getChain(HashMap<Integer, Set<Hash>> topOrder) {
        return null;
    }

    @Override
    public Set<Hash> getChild(Hash block) {
        return null;
    }

    public int getNumOfTips() {
        // TODO
        return -1;
    }
}
