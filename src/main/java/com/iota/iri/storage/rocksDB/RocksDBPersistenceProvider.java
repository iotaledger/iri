package com.iota.iri.storage.rocksDB;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SystemUtils;
import org.rocksdb.BackupEngine;
import org.rocksdb.BackupableDBOptions;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.Cache;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.LRUCache;
import org.rocksdb.MergeOperator;
import org.rocksdb.Priority;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksEnv;
import org.rocksdb.RocksIterator;
import org.rocksdb.StringAppendOperator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.model.HashFactory;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.utils.IotaIOUtils;
import com.iota.iri.utils.Pair;

public class RocksDBPersistenceProvider implements PersistenceProvider {

    private static final Logger log = LoggerFactory.getLogger(RocksDBPersistenceProvider.class);
    private static final int BLOOM_FILTER_BITS_PER_KEY = 10;

    private static final Pair<Indexable, Persistable> PAIR_OF_NULLS = new Pair<>(null, null);

    private final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
    private final SecureRandom seed = new SecureRandom();

    private final String dbPath;
    private final String logPath;
    private String configPath;
    
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
    
    private Cache cache, compressedCache;
    private ColumnFamilyOptions columnFamilyOptions;
    
    /**
     * Creates a new RocksDB provider without reading from a configuration file
     * 
     * @param dbPath The location where the database will be stored
     * @param logPath The location where the log files will be stored
     * @param cacheSize the size of the cache used by the database implementation
     * @param columnFamilies A map of the names related to their Persistable class
     * @param metadataColumnFamily Map of metadata used by the Persistable class, can be <code>null</code>
     */
    public RocksDBPersistenceProvider(String dbPath, String logPath, int cacheSize,
            Map<String, Class<? extends Persistable>> columnFamilies,
            Map.Entry<String, Class<? extends Persistable>> metadataColumnFamily) {
        this(dbPath, logPath, null, cacheSize, columnFamilies, metadataColumnFamily);
    }
    
    /**
     * Creates a new RocksDB provider by reading the configuration to be used in this instance from a file
     * 
     * @param dbPath The location where the database will be stored
     * @param logPath The location where the log files will be stored
     * @param configPath The location where the RocksDB config is read from
     * @param cacheSize the size of the cache used by the database implementation
     * @param columnFamilies A map of the names related to their Persistable class
     * @param metadataColumnFamily Map of metadata used by the Persistable class, can be <code>null</code>
     */
    public RocksDBPersistenceProvider(String dbPath, String logPath, String configPath, int cacheSize,
                                      Map<String, Class<? extends Persistable>> columnFamilies,
                                      Map.Entry<String, Class<? extends Persistable>> metadataColumnFamily) {
        this.dbPath = dbPath;
        this.logPath = logPath;
        this.cacheSize = cacheSize;
        this.columnFamilies = columnFamilies;
        this.metadataColumnFamily = metadataColumnFamily;
        this.configPath = configPath;

    }

    @Override
    public void init() throws Exception {
        log.info("Initializing Database on " + dbPath);
        initDB(dbPath, logPath, configPath, columnFamilies);
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
        IotaIOUtils.closeQuietly(db, options, bloomFilter, cache, compressedCache, columnFamilyOptions);
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
        if (mayExist(model, key)) {
            //ensure existence
            ColumnFamilyHandle handle = classTreeMap.get(model);
            return handle != null && db.get(handle, key.bytes()) != null;
        }
        return false;
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

                if (value.canMerge()) {
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
            try (WriteBatch writeBatch = new WriteBatch(); 
                    WriteOptions writeOptions = new WriteOptions()
                            //We are explicit about what happens if the node reboots before a flush to the db
                            .setDisableWAL(false)
                            //We want to make sure deleted data was indeed deleted
                            .setSync(true)) {
                
                for (Pair<Indexable, ? extends Class<? extends Persistable>> entry : models) {
                    Indexable indexable = entry.low;
                    byte[] keyBytes = indexable.bytes();
                    ColumnFamilyHandle handle = classTreeMap.get(entry.hi);
                    writeBatch.delete(handle, keyBytes);
                    ColumnFamilyHandle metadataHandle = metadataReference.get(entry.hi);
                    if (metadataHandle != null) {
                        writeBatch.delete(metadataHandle, keyBytes);
                    }
                }

                db.write(writeOptions, writeBatch);
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

    @Override
    public List<byte[]> loadAllKeysFromTable(Class<? extends Persistable> column) {
        List<byte[]> keyBytes = new ArrayList<>();

        ColumnFamilyHandle columnFamilyHandle = classTreeMap.get(column);
        try (RocksIterator iterator = db.newIterator(columnFamilyHandle)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                keyBytes.add(iterator.key());
            }
        }
        return keyBytes;
    }

    private void flushHandle(ColumnFamilyHandle handle) throws RocksDBException {
        List<byte[]> itemsToDelete = new ArrayList<>();
        try (RocksIterator iterator = db.newIterator(handle)) {

            for (iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
                itemsToDelete.add(iterator.key());
            }
        }
        if (!itemsToDelete.isEmpty()) {
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
        initDB(path, logPath, configPath, columnFamilies);
    }

    // options is closed in shutdown
    @SuppressWarnings("resource")
    private void initDB(String path, String logPath, String configFile, Map<String, Class<? extends Persistable>> columnFamilies) throws Exception {
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

            options = createOptions(logPath, configFile);

            bloomFilter = new BloomFilter(BLOOM_FILTER_BITS_PER_KEY);
            cache = new LRUCache(cacheSize * SizeUnit.KB, 2);
            compressedCache = new LRUCache(32 * SizeUnit.KB, 10);
            
            BlockBasedTableConfig blockBasedTableConfig = new BlockBasedTableConfig().setFilterPolicy(bloomFilter);
            blockBasedTableConfig
                .setFilterPolicy(bloomFilter)
                .setBlockSizeDeviation(10)
                .setBlockRestartInterval(16)
                .setBlockCache(cache)
                .setBlockCacheCompressed(compressedCache);

            MergeOperator mergeOperator = new StringAppendOperator();
            List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();
            
            columnFamilyOptions = new ColumnFamilyOptions()
                .setMergeOperator(mergeOperator)
                .setTableFormatConfig(blockBasedTableConfig)
                .setMaxWriteBufferNumber(2)
                .setWriteBufferSize(2 * SizeUnit.MB);
            
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
            IotaIOUtils.closeQuietly(db, options, bloomFilter, columnFamilyOptions, cache, compressedCache);
            throw e;
        }
    }

    private void initClassTreeMap(List<ColumnFamilyDescriptor> columnFamilyDescriptors) throws Exception {
        Map<Class<?>, ColumnFamilyHandle> classMap = new LinkedHashMap<>();
        String mcfName = metadataColumnFamily == null ? "" : metadataColumnFamily.getKey();
        //skip default column
        int i = 1;
        for (; i < columnFamilyDescriptors.size(); i++) {

            String name = new String(columnFamilyDescriptors.get(i).getName());
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

    private DBOptions createOptions(String logPath, String configFile) throws IOException {
        DBOptions options = null;
        File pathToLogDir = Paths.get(logPath).toFile();
        if (!pathToLogDir.exists() || !pathToLogDir.isDirectory()) {
            boolean success = pathToLogDir.mkdir();
            if (!success) {
                log.warn("Unable to make directory: {}", pathToLogDir);
            }
        }

        int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        RocksEnv.getDefault()
            .setBackgroundThreads(numThreads, Priority.HIGH)
            .setBackgroundThreads(numThreads, Priority.LOW);

        if (configFile != null) {
            File config = Paths.get(configFile).toFile();
            if (config.exists() && config.isFile() && config.canRead()) {
                Properties configProperties = new Properties();
                
                try (InputStream stream = new FileInputStream(config)){
                    configProperties.load(stream);
                    options = DBOptions.getDBOptionsFromProps(configProperties);
                } catch (IllegalArgumentException e) {
                    log.warn("RocksDB configuration file is empty, falling back to default values");
                }
            }
        }
        if (options == null) {
            options = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setMaxLogFileSize(SizeUnit.MB)
                .setMaxManifestFileSize(SizeUnit.MB)
                .setMaxOpenFiles(10000)
                .setMaxBackgroundCompactions(1)
                .setAllowConcurrentMemtableWrite(true)
                .setMaxSubcompactions(Runtime.getRuntime().availableProcessors());
        }
        
        if (!BaseIotaConfig.Defaults.DB_LOG_PATH.equals(logPath) && logPath != null) {
            if (!options.dbLogDir().equals("")) {
                log.warn("Defined a db log path in config and commandline; Using the command line setting."); 
            }
            
            options.setDbLogDir(logPath);
        }

        return options;
    }
}
