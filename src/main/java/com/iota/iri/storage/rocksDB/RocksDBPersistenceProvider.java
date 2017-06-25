package com.iota.iri.storage.rocksDB;

import com.iota.iri.model.*;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.utils.Pair;
import org.apache.commons.lang3.SystemUtils;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class RocksDBPersistenceProvider implements PersistenceProvider {

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

    private ColumnFamilyHandle transactionHandle;
    private ColumnFamilyHandle transactionMetadataHandle;
    private ColumnFamilyHandle milestoneHandle;
    private ColumnFamilyHandle stateDiffHandle;
    private ColumnFamilyHandle addressHandle;
    private ColumnFamilyHandle approveeHandle;
    private ColumnFamilyHandle bundleHandle;
    private ColumnFamilyHandle tagHandle;

    private List<ColumnFamilyHandle> transactionGetList;

    private final AtomicReference<Map<Class<?>, ColumnFamilyHandle>> classTreeMap = new AtomicReference<>();
    private final AtomicReference<Map<Class<?>, ColumnFamilyHandle>> metadataReference = new AtomicReference<>();

    private final SecureRandom seed = new SecureRandom();

    private RocksDB db;
    private DBOptions options;
    private BloomFilter bloomFilter;

    public RocksDBPersistenceProvider(String dbPath, String logPath) {
        this.dbPath = dbPath;
        this.logPath = logPath;
    }

    @Override
    public void init() throws Exception {
        log.info("Initializing Database Backend... ");
        initDB(dbPath, logPath);
        initClassTreeMap();
        log.info("RocksDB persistence provider initialized.");
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
    public boolean save(Persistable thing, Indexable index) throws Exception {
        ColumnFamilyHandle handle = classTreeMap.get().get(thing.getClass());
        /*
        if( !db.keyMayExist(handle, index.bytes(), new StringBuffer()) ) {
            counts.put(thing.getClass(), counts.get(thing.getClass()) + 1);
        }
        */
        db.put(handle, index.bytes(), thing.bytes());
        ColumnFamilyHandle referenceHandle = metadataReference.get().get(thing.getClass());
        if(referenceHandle != null) {
            db.put(referenceHandle, index.bytes(), thing.metadata());
        }
        return true;
    }

    @Override
    public void delete(Class<?> model, Indexable index) throws Exception {
        /*
        if( db.keyMayExist(classTreeMap.get().get(model), index.bytes(), new StringBuffer()) ) {
            counts.put(model, counts.get(model) + 1);
        }
        */
        db.delete(classTreeMap.get().get(model), index.bytes());
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
        if(iterator.isValid()) {
            object = (Persistable) model.newInstance();
            index = (Indexable) indexModel.newInstance();
            index.read(iterator.key());
            object.read(iterator.value());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
            if(referenceHandle != null) {
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
        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            if(db.get(otherHandle, iterator.key()) == null) {
                indexables.add(new Hash(iterator.key()));
            }
        }
        iterator.close();
        return indexables;
    }

    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        Persistable object = (Persistable) model.newInstance();
        object.read(db.get(classTreeMap.get().get(model), index == null? new byte[0]: index.bytes()));
        ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
        if(referenceHandle != null) {
            object.readMetadata(db.get(referenceHandle, index == null? new byte[0]: index.bytes()));
        }
        return object;
    }


    @Override
    public boolean mayExist(Class<?> model, Indexable index) throws Exception {
        /* for version 5.4.5
        ColumnFamilyHandle handle = classTreeMap.get().get(model);
        return db.keyMayExist(handle, index.bytes(), new StringBuilder());
        */
        return db.keyMayExist(classTreeMap.get().get(model), index.bytes(), new StringBuffer());
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
        Set<Indexable> keys = new HashSet<>();
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

    @Override
    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        Set<Indexable> hashes = keysStartingWith(model, key);
        Indexable out;
        if(hashes.size() == 1) {
            out = (Indexable) hashes.toArray()[0];
        } else if (hashes.size() > 1) {
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
        if(iterator.isValid()) {
            object = (Persistable) model.newInstance();
            indexable = index.getClass().newInstance();
            indexable.read(iterator.key());
            object.read(iterator.value());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
            if(referenceHandle != null) {
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
        if(iterator.isValid()) {
            object = (Persistable) model.newInstance();
            object.read(iterator.value());
            indexable = (Indexable) index.getClass().newInstance();
            indexable.read(iterator.key());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
            if(referenceHandle != null) {
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
        if(iterator.isValid()) {
            object = (Persistable) model.newInstance();
            object.read(iterator.value());
            indexable = (Indexable) index.newInstance();
            indexable.read(iterator.key());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(model);
            if(referenceHandle != null) {
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
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        WriteBatch writeBatch = new WriteBatch();
        WriteOptions writeOptions = new WriteOptions();
        for(Pair<Indexable, Persistable> entry: models) {
            Indexable key = entry.low;
            Persistable value = entry.hi;
            ColumnFamilyHandle handle = classTreeMap.get().get(value.getClass());
            ColumnFamilyHandle referenceHandle = metadataReference.get().get(value.getClass());
            if(value.merge()) {
                writeBatch.merge(handle, key.bytes(), value.bytes());
            } else {
                writeBatch.put(handle, key.bytes(), value.bytes());
            }
            if(referenceHandle != null) {
                writeBatch.put(referenceHandle, key.bytes(), value.metadata());
            }
        }
        db.write(writeOptions, writeBatch);
        writeBatch.close();
        writeOptions.close();
        return true;
    }

    @Override
    public boolean update(Persistable thing, Indexable index, String item) throws Exception {
        ColumnFamilyHandle referenceHandle = metadataReference.get().get(thing.getClass());
        if(referenceHandle != null) {
            db.put(referenceHandle, index.bytes(), thing.metadata());
        }
        return false;
    }

    private void initDB(String path, String logPath) throws Exception {
        try {
            RocksDB.loadLibrary();
        } catch(Exception e) {
            if(SystemUtils.IS_OS_WINDOWS) {
                log.error("Error loading RocksDB library. " +
                        "Please ensure that " +
                        "Microsoft Visual C++ 2015 Redistributable Update 3 " +
                        "is installed and updated");
            }
            throw e;
        }
        Thread.yield();

        File pathToLogDir = Paths.get(logPath).toFile();
        if(!pathToLogDir.exists() || !pathToLogDir.isDirectory()) {
            pathToLogDir.mkdir();
        }

        RocksEnv.getDefault()
                .setBackgroundThreads(Runtime.getRuntime().availableProcessors()/2, RocksEnv.FLUSH_POOL)
                .setBackgroundThreads(Runtime.getRuntime().availableProcessors()/2, RocksEnv.COMPACTION_POOL)
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
        BlockBasedTableConfig blockBasedTableConfig = new BlockBasedTableConfig().setFilter(bloomFilter);
        blockBasedTableConfig
                .setFilter(bloomFilter)
                .setCacheNumShardBits(2)
                .setBlockSizeDeviation(10)
                .setBlockRestartInterval(16)
                .setBlockCacheSize(200 * SizeUnit.KB)
                .setBlockCacheCompressedNumShardBits(10)
                .setBlockCacheCompressedSize(32 * SizeUnit.KB);
        options.setAllowConcurrentMemtableWrite(true);

        MergeOperator mergeOperator = new StringAppendOperator();
        ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions()
                .setMergeOperator(mergeOperator)
                .setTableFormatConfig(blockBasedTableConfig)
                .setMaxWriteBufferNumber(2)
                .setWriteBufferSize(2 * SizeUnit.MB);

        List<ColumnFamilyHandle> familyHandles = new ArrayList<>();

        List<ColumnFamilyDescriptor> columnFamilyDescriptors = columnFamilyNames.stream().map(name -> new ColumnFamilyDescriptor(name.getBytes(), columnFamilyOptions)).collect(Collectors.toList());
        db = RocksDB.open(options, path, columnFamilyDescriptors, familyHandles);
        db.enableFileDeletions(true);

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

        for(; ++i < familyHandles.size();) {
            db.dropColumnFamily(familyHandles.get(i));
        }

        transactionGetList = new ArrayList<>();
        for(i = 1; i < 5; i ++) {
            transactionGetList.add(familyHandles.get(i));
        }
    }

}
