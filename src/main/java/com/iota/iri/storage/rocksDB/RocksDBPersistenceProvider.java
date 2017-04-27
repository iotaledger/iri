package com.iota.iri.storage.rocksDB;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.*;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.utils.Serializer;
import com.iota.iri.controllers.TransactionViewModel;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.SystemUtils;
import org.rocksdb.*;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class RocksDBPersistenceProvider implements PersistenceProvider {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(RocksDBPersistenceProvider.class);
    private static final int BLOOM_FILTER_BITS_PER_KEY = 10;

    private final String[] columnFamilyNames = new String[]{
            "transaction",
            "transactionValidity",
            "transactionType",
            "transactionArrivalTime",
            "transactionSolid",
            "markedSnapshot",
            "address",
            "bundle",
            "approovee",
            "tag",
            "tip",
            "transactionRating",
            "milestone",
            "snapshot",
            "height",
            "sender",
    };

    private boolean running;
    private ColumnFamilyHandle transactionHandle;
    private ColumnFamilyHandle transactionValidityHandle;
    private ColumnFamilyHandle transactionTypeHandle;
    private ColumnFamilyHandle transactionArrivalTimeHandle;
    private ColumnFamilyHandle transactionSolidHandle;
    private ColumnFamilyHandle markedSnapshotHandle;
    private ColumnFamilyHandle addressHandle;
    private ColumnFamilyHandle bundleHandle;
    private ColumnFamilyHandle approoveeHandle;
    private ColumnFamilyHandle tagHandle;
    private ColumnFamilyHandle tipHandle;
    private ColumnFamilyHandle milestoneHandle;
    private ColumnFamilyHandle snapshotHandle;
    private ColumnFamilyHandle heightHandle;
    private ColumnFamilyHandle senderHandle;

    private List<ColumnFamilyHandle> transactionGetList;

    private final Map<Class<?>, ColumnFamilyHandle> classTreeMap = new HashMap<>();
    private final Map<Class<?>, MyFunction<Object, Boolean>> saveMap = new HashMap<>();
    private final Map<Class<?>, MyFunction<Object, Void>> deleteMap = new HashMap<>();
    private final Map<Class<?>, MyFunction<Object, Object>> setKeyMap = new HashMap<>();
    private final Map<Class<?>, MyFunction<Object, Boolean>> loadMap = new HashMap<>();
    private final Map<Class<?>, MyFunction<Object, Boolean>> mayExistMap = new HashMap<>();
    private final Map<Class<?>, MyFunction<Object, Object>> nextMap = new HashMap<>();
    private final Map<Class<?>, MyFunction<Object, Object>> prevMap = new HashMap<>();
    private final Map<Class<?>, MyRunnable<Object>> latestMap = new HashMap<>();
    private final Map<Class<?>, MyRunnable<Object>> firstMap = new HashMap<>();
    private final Map<Class<?>, ColumnFamilyHandle> countMap = new HashMap<>();

    private final SecureRandom seed = new SecureRandom();

    private RocksDB db;
    private DBOptions options;
    private BloomFilter bloomFilter;
    long compationWaitTime = 5 * 60 * 1000;
    private Thread restartThread;
    private boolean available;

    @Override
    public void init() throws Exception {
        log.info("Initializing Database Backend... ");
        initDB(
                Configuration.string(Configuration.DefaultConfSettings.DB_PATH),
                Configuration.string(Configuration.DefaultConfSettings.DB_LOG_PATH)
        );
        initClassTreeMap();
        initSaveMap();
        initLoadMap();
        initSetKeyMap();
        initMayExistMap();
        initDeleteMap();
        initCountMap();
        initIteratingMaps();
        available = true;
        log.info("RocksDB persistence provider initialized.");
    }

    @Override
    public boolean isAvailable() {
        return this.available;
    }

    private void initIteratingMaps() {
        firstMap.put(Milestone.class, firstMilestone);
        latestMap.put(Milestone.class, latestMilestone);
        nextMap.put(Milestone.class, nextMilestone);
        prevMap.put(Milestone.class, previousMilestone);
    }

    private void initSetKeyMap() {
        setKeyMap.put(Transaction.class, hashObject -> new Transaction(((Hash) hashObject)));
    }

    private void initCountMap() {
        countMap.put(Transaction.class, transactionHandle);
        countMap.put(Address.class, addressHandle);
        countMap.put(Bundle.class, bundleHandle);
        countMap.put(Approvee.class, approoveeHandle);
        countMap.put(Tag.class, tagHandle);
        countMap.put(Tip.class, tipHandle);
        /*
        countMap.put(AnalyzedFlag.class, analyzedFlagHandle);
        */
    }

    private void initDeleteMap() {
        deleteMap.put(Transaction.class, txObj -> {
            Transaction transaction = ((Transaction) txObj);
            byte[] key = transaction.hash.bytes();
            db.delete(transactionHandle, key);
            db.delete(transactionArrivalTimeHandle, key);
            db.delete(transactionTypeHandle, key);
            db.delete(transactionValidityHandle, key);
            db.delete(transactionSolidHandle, key);
            db.delete(markedSnapshotHandle, key);
            return null;
        });
        deleteMap.put(Tip.class, txObj -> {
            db.delete(tipHandle, ((Tip) txObj).hash.bytes());
            return null;
        });
        deleteMap.put(Milestone.class, msObj -> {
            db.delete(milestoneHandle, Serializer.serialize(((Milestone)msObj).index));
            db.delete(snapshotHandle, ((Milestone)msObj).hash.bytes());
            return null;
        });
    }

    private void initMayExistMap() {
        mayExistMap.put(Transaction.class, txObj -> db.keyMayExist(transactionHandle, ((Transaction) txObj).hash.bytes(), new StringBuffer()));
        mayExistMap.put(Tip.class, txObj -> db.keyMayExist(tipHandle, ((Tip) txObj).hash.bytes(), new StringBuffer()));
    }

    private void initLoadMap() {
        loadMap.put(Transaction.class, getTransaction);
        loadMap.put(Address.class, getAddress);
        loadMap.put(Tag.class, getTag);
        loadMap.put(Bundle.class, getBundle);
        loadMap.put(Approvee.class, getApprovee);
        loadMap.put(Milestone.class, getMilestone);
    }

    private void initSaveMap() {
        saveMap.put(Transaction.class, saveTransaction);
        saveMap.put(Tip.class, saveTip);
        saveMap.put(Milestone.class, saveMilestone);
    }

    private void initClassTreeMap() {
        classTreeMap.put(Address.class, addressHandle);
        classTreeMap.put(Tip.class, tipHandle);
        classTreeMap.put(Approvee.class, approoveeHandle);
        classTreeMap.put(Bundle.class, bundleHandle);
        classTreeMap.put(Tag.class, tagHandle);
        classTreeMap.put(Transaction.class, transactionHandle);
    }

    @Override
    public void shutdown() {
        running = false;
        if (db != null) db.close();
        options.close();
        bloomFilter.close();
    }

    private final MyFunction<Object, Boolean> saveTransaction = (txObject -> {
        Transaction transaction = (Transaction) txObject;
        WriteBatch batch = new WriteBatch();
        WriteOptions writeOptions = new WriteOptions();
        byte[] key = transaction.hash.bytes();
        batch.put(transactionHandle, key, transaction.bytes);
        batch.put(transactionValidityHandle, key, Serializer.serialize(transaction.validity));
        batch.put(transactionTypeHandle, key, Serializer.serialize(transaction.type));
        batch.put(transactionArrivalTimeHandle, key, Serializer.serialize(transaction.arrivalTime));
        batch.put(heightHandle, key, Serializer.serialize(transaction.height));
        batch.put(senderHandle, key, transaction.sender.getBytes());
        batch.put(transactionSolidHandle, key, Serializer.serialize(transaction.solid));
        batch.put(markedSnapshotHandle, key, transaction.snapshot ? new byte[]{1}: new byte[]{0});
        batch.merge(addressHandle, transaction.address.hash.bytes(), key);
        batch.merge(bundleHandle, transaction.bundle.hash.bytes(), key);
        batch.merge(approoveeHandle, transaction.trunk.hash.bytes(), key);
        batch.merge(approoveeHandle, transaction.branch.hash.bytes(), key);
        batch.merge(tagHandle, transaction.tag.value.bytes(), key);
        db.write(writeOptions, batch);
        batch.close();
        writeOptions.close();
        return true;
    });

    private final MyFunction<Object, Boolean> saveTip = tipObj -> {
        db.put(tipHandle, ((Tip) tipObj).hash.bytes(), ((Tip) tipObj).status);
        return true;
    };

    private final MyFunction<Object, Boolean> saveMilestone = msObj -> {
        WriteBatch writeBatch = new WriteBatch();
        WriteOptions writeOptions = new WriteOptions();
        Milestone milestone = ((Milestone) msObj);
        writeBatch.put(milestoneHandle, Serializer.serialize(milestone.index), milestone.hash.bytes());
        if(milestone.snapshot != null) {
            writeBatch.put(snapshotHandle, milestone.hash.bytes(), objectBytes(milestone.snapshot));
        }
        db.write(writeOptions, writeBatch);
        writeBatch.close();
        writeOptions.close();
        return true;
    };

    private byte[] objectBytes(Object o) throws IOException {
        byte[] output;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        oos.close();
        output = bos.toByteArray();
        bos.close();
        return output;
    }

    private Object objectFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        Object out;
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis);
        out = ois.readObject();
        ois.close();
        bis.close();
        return out;
    }

    @Override
    public boolean save(Object thing) throws Exception {
        return saveMap.get(thing.getClass()).apply(thing);
    }

    @Override
    public void delete(Object thing) throws Exception {
        deleteMap.get(thing.getClass()).apply(thing);
    }

    private Hash[] byteToHash(byte[] bytes, int size) {
        if(bytes == null) {
            return new Hash[0];
        }
        int i;
        Set<Hash> hashes = new HashSet<>();
        for(i = size; i <= bytes.length; i += size + 1) {
            hashes.add(new Hash(Arrays.copyOfRange(bytes, i - size, i)));
        }
        return hashes.stream().toArray(Hash[]::new);
    }

    @Override
    public boolean exists(Class<?> model, Hash key) throws Exception {
        if(model == Transaction.class) {
            return db.get(transactionHandle, key.bytes()) != null;
        }
        throw new NotImplementedException("Mada mada exists shinai");
    }

    @Override
    public Object latest(Class<?> model) throws Exception {
        MyRunnable<Object> locator = latestMap.get(model);
        if(locator != null) {
            return locator.run();
        }
        return null;
    }

    @Override
    public Object[] keysWithMissingReferences(Class<?> modelClass) throws Exception {
        if(modelClass == Transaction.class) {
            return getMissingTransactions();
        }
        if(modelClass == Tip.class) {
            List<byte[]> tips = new ArrayList<>();
            RocksIterator iterator = db.newIterator(transactionHandle);
            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                if(db.get(approoveeHandle, iterator.key()) == null) {
                    tips.add(iterator.key());
                }
            }
            iterator.close();
            return tips.stream().map(Hash::new).toArray();
        }
        throw new NotImplementedException("Not implemented");
    }

    private Object[] getMissingTransactions() throws RocksDBException {
        List<byte[]> txToRequest = new ArrayList<>();
        RocksIterator iterator = db.newIterator(approoveeHandle);
        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            if(db.get(transactionHandle, iterator.key()) == null) {
                txToRequest.add(iterator.key());
            }
        }
        iterator.close();
        return txToRequest.stream().map(Hash::new).toArray();
    }

    @Override
    public boolean get(Object model) throws Exception {
        return loadMap.get(model.getClass()).apply(model);
    }

    private final MyFunction<Object, Boolean> getTransaction = (txObject) -> {
        if(txObject == null) {
            return false;
        }
        Transaction transaction = ((Transaction) txObject);
        byte[] key = transaction.hash.bytes();
        transaction.bytes = db.get(transactionHandle, key);
        if(transaction.bytes == null) {
            transaction.type = TransactionViewModel.PREFILLED_SLOT;
            return false;
        } else if (transaction.bytes.length != TransactionViewModel.SIZE) {
            delete(transaction);
            transaction.type = TransactionViewModel.PREFILLED_SLOT;
            return false;
        }
        transaction.validity = Serializer.getInteger(db.get(transactionValidityHandle, key));
        transaction.type = Serializer.getInteger(db.get(transactionTypeHandle, key));
        transaction.arrivalTime = Serializer.getLong(db.get(transactionArrivalTimeHandle, key));
        transaction.solid =  db.get(transactionSolidHandle, key);
        transaction.snapshot = byteToBoolean(db.get(markedSnapshotHandle, key));
        transaction.height = Serializer.getLong(db.get(heightHandle, key));
        transaction.sender = new String(db.get(senderHandle, key));
        return true;
    };

    private boolean byteToBoolean(byte[] bytes) {
        return !(bytes == null || bytes.length != 1) && bytes[0] != 0;
    }

    private final MyFunction<Object, Boolean> getAddress = (addrObject) -> {
        Address address = ((Address) addrObject);
        byte[] result = db.get(addressHandle, address.hash.bytes());
        if(result != null) {
            address.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return  true;
        } else {
            address.transactions = new Hash[0];
            return false;
        }
    };

    private final MyFunction<Object, Boolean> getTag = tagObj -> {
        Tag tag = ((Tag) tagObj);
        byte[] result = db.get(tagHandle, tag.value.bytes());
        if(result != null) {
            tag.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return  true;
        } else {
            tag.transactions = new Hash[0];
            return false;
        }
    };

    private final MyFunction<Object, Boolean> getMilestone = msObj -> {
        if(msObj instanceof Milestone) {
            Milestone milestone = ((Milestone) msObj);
            byte[] hash = db.get(milestoneHandle, Serializer.serialize(milestone.index));
            if(hash != null) {
                milestone.hash = new Hash(hash);
                byte[] snapshot = db.get(snapshotHandle, hash);
                if(snapshot != null) {
                    Object snapshotObject = objectFromBytes(snapshot);
                    if(snapshotObject instanceof Map) {
                        milestone.snapshot = (Map<Hash, Long>) snapshotObject;
                    }
                }
                return true;
            }
        }
        return false;
    };

    private MyRunnable<Object> firstMilestone = () -> {
        Milestone milestone = new Milestone();
        RocksIterator iterator = db.newIterator(milestoneHandle);
        iterator.seekToFirst();
        if(iterator.isValid()) {
            milestone.index = Serializer.getInteger(iterator.key());
            getMilestone.apply(milestone);
        }
        if(milestone.index == null) {
            return null;
        }
        return milestone;
    };

    private MyRunnable<Object> latestMilestone = () -> {
        Milestone milestone = new Milestone();
        RocksIterator iterator = db.newIterator(milestoneHandle);
        iterator.seekToLast();
        if(iterator.isValid()) {
            milestone.index = Serializer.getInteger(iterator.key());
            getMilestone.apply(milestone);
        }
        if(milestone.index == null) {
            return null;
        }
        return milestone;
    };

    private MyFunction<Object, Object> nextMilestone = (start) -> {
        Milestone milestone = new Milestone();
        RocksIterator iterator = db.newIterator(milestoneHandle);
        iterator.seek(Serializer.serialize((int)start));
        iterator.next();
        if(iterator.isValid()) {
            milestone.index = Serializer.getInteger(iterator.key());
            getMilestone.apply(milestone);
        }
        if(milestone.index == null) {
            return null;
        }
        return milestone;
    };

    private MyFunction<Object, Object> previousMilestone = (start) -> {
        Milestone milestone = new Milestone();
        RocksIterator iterator = db.newIterator(milestoneHandle);
        iterator.seek(Serializer.serialize((int)start));
        iterator.prev();
        if(iterator.isValid()) {
            milestone.index = Serializer.getInteger(iterator.key());
            getMilestone.apply(milestone);
        }
        if(milestone.index == null) {
            return null;
        }
        return milestone;
    };

    private final MyFunction<Object, Boolean> getBundle = bundleObj -> {
        Bundle bundle = ((Bundle) bundleObj);
        byte[] result = db.get(bundleHandle, bundle.hash.bytes());
        if(result != null) {
            bundle.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return true;
        }
        return false;
    };

    private final MyFunction<Object, Boolean> getApprovee = approveeObj -> {
        Approvee approvee = ((Approvee) approveeObj);
        byte[] result = db.get(approoveeHandle, approvee.hash.bytes());
        if(result != null) {
            approvee.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return true;
        } else {
            approvee.transactions = new Hash[0];
            return false;
        }
    };

    @Override
    public boolean mayExist(Object model) throws Exception {
        return mayExistMap.get(model.getClass()).apply(model);
    }

    @Override
    public long count(Class<?> model) throws Exception {
        ColumnFamilyHandle handle = countMap.get(model);
        return db.getLongProperty(handle, "rocksdb.estimate-num-keys");
    }

    @Override
    public Hash[] keysStartingWith(Class<?> modelClass, byte[] value) {
        RocksIterator iterator;
        ColumnFamilyHandle handle = classTreeMap.get(modelClass);
        List<byte[]> keys = new LinkedList<>();
        if(handle != null) {
            iterator = db.newIterator(handle);
            try {
                iterator.seek(new Hash(value, 0, value.length).bytes());
                for(;
                    iterator.isValid() && Arrays.equals(Arrays.copyOf(iterator.key(), value.length), value);
                    iterator.next()) {
                    keys.add(iterator.key());
                }
            } finally {
                iterator.close();
            }
        }
        return keys.stream().map(Hash::new).toArray(Hash[]::new);
    }

    @Override
    public Object seek(Class<?> model, byte[] key) throws Exception {
        Hash[] hashes = keysStartingWith(model, key);
        Object out;
        if(hashes.length == 1) {
            out = setKeyMap.get(model).apply(hashes[0]);
        } else if (hashes.length > 1) {
            out = setKeyMap.get(model).apply(hashes[seed.nextInt(hashes.length)]);
        } else {
            out = null;
        }
        loadMap.get(model).apply(out);
        return out;
    }

    @Override
    public Object next(Class<?> model, int index) throws Exception {
        MyFunction<Object, Object> locator = nextMap.get(model);
        if(locator != null) {
            return locator.apply(index);
        }
        return null;
    }

    @Override
    public Object previous(Class<?> model, int index) throws Exception {
        MyFunction<Object, Object> locator = prevMap.get(model);
        if(locator != null) {
            return locator.apply(index);
        }
        return null;
    }

    @Override
    public Object first(Class<?> model) throws Exception {
        MyRunnable<Object> locator = firstMap.get(model);
        if(locator != null) {
            return locator.run();
        }
        return null;
    }

    private void flushHandle(ColumnFamilyHandle handle) throws RocksDBException {
        List<byte[]> itemsToDelete = new ArrayList<>();
        RocksIterator iterator = db.newIterator(handle);
        for(iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
            itemsToDelete.add(iterator.key());
        }
        iterator.close();
        if(itemsToDelete.size() > 0) {
            log.info("Flushing flags. Amount to delete: " + itemsToDelete.size());
        }
        for(byte[] itemToDelete: itemsToDelete) {
            db.delete(handle, itemToDelete);
        }
    }


    private MyFunction<Object, Boolean> updateTransaction(String item) {
        return txObject -> {
            Transaction transaction = (Transaction) txObject;
            byte[] key = transaction.hash.bytes();
            switch (item) {
                case "validity":
                    db.put(transactionValidityHandle, key, Serializer.serialize(transaction.validity));
                    break;
                case "type":
                    db.put(transactionValidityHandle, key, Serializer.serialize(transaction.type));
                    break;
                case "arrivalTime":
                    db.put(transactionValidityHandle, key, Serializer.serialize(transaction.arrivalTime));
                    break;
                case "solid":
                    db.put(transactionSolidHandle, key, Serializer.serialize(transaction.solid));
                    break;
                case "markedSnapshot":
                    db.put(markedSnapshotHandle, key, transaction.snapshot ? new byte[]{1} : new byte[]{0});
                    break;
                case "height":
                    db.put(heightHandle, key, Serializer.serialize(transaction.height));
                    break;
                case "sender":
                    db.put(senderHandle, key, transaction.sender.getBytes());
                    break;
                default: {
                    log.error("That's not available yet.");
                    return false;
                }
            }
            return true;
        };
    }

    private MyFunction<Object, Boolean> updateMilestone(String item) {
        return msObj -> {
            Milestone milestone = ((Milestone) msObj);
            byte[] key = milestone.hash.bytes();
            switch (item) {
                case "snapshot":
                    if(!db.keyMayExist(snapshotHandle, key, new StringBuffer())) {
                        db.put(snapshotHandle, key, objectBytes(milestone.snapshot));
                    }
                    break;
                default:
                    return false;
            }
            return true;
        };
    }

    @Override
    public boolean update(Object thing, String item) throws Exception {
        if(thing instanceof Transaction) {
            return updateTransaction(item).apply(thing);
        } else if (thing instanceof Milestone){
            return updateMilestone(item).apply(thing);
        }
        throw new NotImplementedException("Mada Sono Update ga dekinai yo");
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
        try(final RestoreOptions restoreOptions = new RestoreOptions(false)){
            backupEngine.restoreDbFromLatestBackup(path, logPath, restoreOptions);
        } finally {
            backupEngine.close();
        }
        backupableDBOptions.close();
        env.close();
        initDB(path, logPath);
    }

    private void initDB(String path, String logPath) throws Exception {
        StringAppendOperator stringAppendOperator = new StringAppendOperator();
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
        bloomFilter = new BloomFilter(BLOOM_FILTER_BITS_PER_KEY);
        BlockBasedTableConfig blockBasedTableConfig = new BlockBasedTableConfig().setFilter(bloomFilter);
        options = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setDbLogDir(logPath);

        List<ColumnFamilyHandle> familyHandles = new ArrayList<>();
        List<ColumnFamilyDescriptor> familyDescriptors = Arrays.stream(columnFamilyNames)
                .map(name -> new ColumnFamilyDescriptor(name.getBytes(),
                        new ColumnFamilyOptions()
                                .setMergeOperator(stringAppendOperator).setTableFormatConfig(blockBasedTableConfig))).collect(Collectors.toList());

        familyDescriptors.add(0, new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));

        //fillMissingColumns(familyDescriptors, familyHandles, path);

        db = RocksDB.open(options, path, familyDescriptors, familyHandles);
        db.enableFileDeletions(true);

        fillmodelColumnHandles(familyHandles);

        //log.info("Checking Tags... ");
        //updateTagDB();
        log.info("Scanning transactions... ");
        scanTxDeleteBaddies();
        //log.info("Clearing solidity markers... ");
        //clearSolidTransactionTags();
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
        running = true;
    }

    private void addColumnFamily(byte[] familyName, RocksDB db) throws RocksDBException {
        final ColumnFamilyHandle columnFamilyHandle = db.createColumnFamily(
                new ColumnFamilyDescriptor(familyName,
                        new ColumnFamilyOptions()));
        assert (columnFamilyHandle != null);
    }

    private void fillmodelColumnHandles(List<ColumnFamilyHandle> familyHandles) throws Exception {
        int i = 0;
        transactionHandle = familyHandles.get(++i);
        transactionValidityHandle = familyHandles.get(++i);
        transactionTypeHandle = familyHandles.get(++i);
        transactionArrivalTimeHandle = familyHandles.get(++i);
        transactionSolidHandle = familyHandles.get(++i);
        markedSnapshotHandle = familyHandles.get(++i);
        addressHandle = familyHandles.get(++i);
        bundleHandle = familyHandles.get(++i);
        approoveeHandle = familyHandles.get(++i);
        tagHandle = familyHandles.get(++i);
        tipHandle = familyHandles.get(++i);
        milestoneHandle = familyHandles.get(++i);
        snapshotHandle = familyHandles.get(++i);
        heightHandle = familyHandles.get(++i);
        senderHandle = familyHandles.get(++i);

        for(; ++i < familyHandles.size();) {
            db.dropColumnFamily(familyHandles.get(i));
        }

        transactionGetList = new ArrayList<>();
        for(i = 1; i < 5; i ++) {
            transactionGetList.add(familyHandles.get(i));
        }
    }

    private void clearSolidTransactionTags() throws RocksDBException {
        RocksIterator iterator = db.newIterator(transactionSolidHandle);
        WriteBatch writeBatch = new WriteBatch();
        WriteOptions writeOptions = new WriteOptions();
        byte[] zero = new byte[]{0};
        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            writeBatch.put(iterator.key(), zero);
        }
        db.write(writeOptions, writeBatch);
        writeBatch.close();
        writeOptions.close();
        iterator.close();
    }

    private void scanTxDeleteBaddies() throws Exception {
        RocksIterator iterator = db.newIterator(transactionHandle);
        List<byte[]> baddies = new ArrayList<>();
        WriteBatch batch = new WriteBatch();
        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            //if(iterator.value().length != TransactionViewModel.SIZE || Arrays.equals(iterator.value(), TransactionViewModel.NULL_TRANSACTION_BYTES)) {
            if(iterator.value().length != TransactionViewModel.SIZE ) {
                byte[] bytes = iterator.value();
                baddies.add(iterator.key());
            } else {
                //batch.put(markedSnapshotHandle, iterator.key(), new byte[]{0});
            }
        }
        iterator.close();
        if(baddies.size() > 0) {
            log.info("Flushing corrupted transactions. Amount to delete: " + baddies.size());
        }
        for(byte[] baddie : baddies) {
            db.delete(transactionHandle, baddie);
        }
        db.write(new WriteOptions(), batch);
        batch.close();
    }

    private void updateTagDB() throws RocksDBException {

        RocksIterator iterator = db.newIterator(tagHandle);
        byte[] key;
        List<byte[]> delList = new ArrayList<>();
        WriteBatch batch = new WriteBatch();
        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            if(iterator.key().length < Hash.SIZE_IN_BYTES) {
                key = ArrayUtils.addAll(iterator.key(), Arrays.copyOf(TransactionViewModel.NULL_TRANSACTION_HASH_BYTES, Hash.SIZE_IN_BYTES - iterator.key().length));
                batch.put(tagHandle, key, iterator.value());
                delList.add(iterator.key());
            }
        }
        iterator.close();
        WriteOptions writeOptions = new WriteOptions();
        db.write(writeOptions, batch);
        batch.close();
        writeOptions.close();
        if(delList.size() > 0) {
            log.info("Flushing corrupted tag handles. Amount to delete: " + delList.size());
        }
        for(byte[] bytes: delList) {
            db.delete(tagHandle, bytes);
        }
    }

    @FunctionalInterface
    private interface MyFunction<T, R> {
        R apply(T t) throws Exception;
    }

    @FunctionalInterface
    private interface MyRunnable<R> {
        R run() throws Exception;
    }
}
