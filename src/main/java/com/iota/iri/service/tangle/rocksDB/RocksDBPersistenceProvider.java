package com.iota.iri.service.tangle.rocksDB;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.*;
import com.iota.iri.service.tangle.IPersistenceProvider;
import com.iota.iri.service.tangle.Serializer;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.bouncycastle.crypto.agreement.srp.SRP6Client;
import org.rocksdb.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class RocksDBPersistenceProvider implements IPersistenceProvider {

    private static int BLOOM_FILTER_RANGE = 1<<1;

    private String[] columnFamilyNames = new String[]{
            "transaction",
            "transactionValidity",
            "transactionType",
            "transactionArrivalTime",
            "address",
            "bundle",
            "approovee",
            "tag",
            "flag",
            "tip",
            "scratchpad",
            "analyzedFlag",
            "analyzedTipFlag",
    };

    boolean running;
    private ColumnFamilyHandle transactionHandle;
    private ColumnFamilyHandle transactionValidityHandle;
    private ColumnFamilyHandle transactionTypeHandle;
    private ColumnFamilyHandle transactionArrivalTimeHandle;
    private ColumnFamilyHandle addressHandle;
    private ColumnFamilyHandle bundleHandle;
    private ColumnFamilyHandle approoveeHandle;
    private ColumnFamilyHandle tagHandle;
    private ColumnFamilyHandle flagHandle;
    private ColumnFamilyHandle tipHandle;
    private ColumnFamilyHandle scratchpadHandle;
    private ColumnFamilyHandle analyzedFlagHandle;
    private ColumnFamilyHandle analyzedTipHandle;

    List<ColumnFamilyHandle> transactionGetList;

    private Map<Class<?>, ColumnFamilyHandle[]> classTreeMap = new HashMap<>();
    private Map<Class<?>, MyFunction<Object, Boolean>> saveMap = new HashMap<>();
    private Map<Class<?>, MyFunction<Object, Void>> deleteMap = new HashMap<>();
    private Map<Class<?>, MyFunction<Object, Boolean>> loadMap = new HashMap<>();
    private Map<Class<?>, MyFunction<Object, Boolean>> mayExistMap = new HashMap<>();
    private Map<Class<?>, ColumnFamilyHandle> countMap = new HashMap<>();

    RocksDB db;
    DBOptions options;
    private Random random;
    private Thread compactionThreadHandle;

    @Override
    public void init() throws Exception {
        initDB(
                Configuration.string(Configuration.DefaultConfSettings.DB_PATH),
                Configuration.string(Configuration.DefaultConfSettings.DB_LOG_PATH)
        );
        initClassTreeMap();
        initSaveMap();
        initLoadMap();
        initMayExistMap();
        initDeleteMap();
        initCountMap();
    }

    private void initCountMap() {
        countMap.put(Transaction.class, transactionHandle);
        countMap.put(Scratchpad.class, scratchpadHandle);
        countMap.put(Address.class, addressHandle);
        countMap.put(Bundle.class, bundleHandle);
        countMap.put(Approvee.class, approoveeHandle);
        countMap.put(Tag.class, tagHandle);
        countMap.put(Flag.class, flagHandle);
        countMap.put(Tip.class, tipHandle);
        countMap.put(AnalyzedFlag.class, analyzedFlagHandle);
    }

    private void initDeleteMap() {
        deleteMap.put(Tip.class, tip -> {
            db.delete(tipHandle, ((Tip) tip).hash.bytes());
            return null;
        });
        deleteMap.put(Scratchpad.class, scratchpad -> {
            byte[] key = ((Scratchpad) scratchpad).hash.bytes();
            if(db.keyMayExist(scratchpadHandle, key, stringBuffer))
                db.delete(scratchpadHandle, key);
            return null;
        });
        deleteMap.put(Transaction.class, txObj -> {
            Transaction transaction = ((Transaction) txObj);
            byte[] key = transaction.hash.bytes();
            db.delete(transactionHandle, key);
            db.delete(transactionArrivalTimeHandle, key);
            db.delete(transactionTypeHandle, key);
            db.delete(transactionValidityHandle, key);
            if(db.get(tipHandle, key) != null)
                db.delete(tipHandle, key);
            return null;
        });
        deleteMap.put(AnalyzedFlag.class, flag -> {
            db.delete(analyzedFlagHandle, ((AnalyzedFlag) flag).hash.bytes());
            return null;
        });
        deleteMap.put(Flag.class, flag -> {
            db.delete(flagHandle, ((AnalyzedFlag) flag).hash.bytes());
            return null;
        });
    }

    private void initMayExistMap() {
        mayExistMap.put(Scratchpad.class, scratchpadObj -> db.keyMayExist(scratchpadHandle, ((Scratchpad) scratchpadObj).hash.bytes(), new StringBuffer()));
        mayExistMap.put(Transaction.class, txObj -> db.keyMayExist(transactionHandle, ((Transaction) txObj).hash.bytes(), new StringBuffer()));
        mayExistMap.put(Tip.class, tipObj -> db.keyMayExist(tipHandle, ((Tip) tipObj).hash.bytes(), new StringBuffer()));
    }

    private void initLoadMap() {
        loadMap.put(Transaction.class, getTransaction);
        loadMap.put(Address.class, getAddress);
        loadMap.put(Tag.class, getTag);
        loadMap.put(Bundle.class, getBundle);
        loadMap.put(Approvee.class, getApprovee);
    }

    private void initSaveMap() {
        saveMap.put(Transaction.class, saveTransaction);
        saveMap.put(AnalyzedFlag.class, saveAnalyzedTransactionFlag);
        saveMap.put(Scratchpad.class, saveScratchpad);
        saveMap.put(Flag.class, saveFlag);
        saveMap.put(Tip.class, saveTip);
    }

    private void initClassTreeMap() {
        classTreeMap.put(Address.class, new ColumnFamilyHandle[]{addressHandle});
        classTreeMap.put(AnalyzedFlag.class, new ColumnFamilyHandle[]{analyzedFlagHandle});
        classTreeMap.put(Approvee.class, new ColumnFamilyHandle[]{approoveeHandle});
        classTreeMap.put(Bundle.class, new ColumnFamilyHandle[]{bundleHandle});
        classTreeMap.put(Flag.class, new ColumnFamilyHandle[]{flagHandle});
        classTreeMap.put(Scratchpad.class, new ColumnFamilyHandle[]{scratchpadHandle});
        classTreeMap.put(Tag.class, new ColumnFamilyHandle[]{tagHandle});
        classTreeMap.put(Tip.class, new ColumnFamilyHandle[]{tipHandle});
        classTreeMap.put(Transaction.class, new ColumnFamilyHandle[]{
                transactionHandle,
                transactionArrivalTimeHandle,
                transactionTypeHandle,
                transactionValidityHandle,
        });
    }

    @Override
    public void shutdown() {
        running = false;
        try {
            compactionThreadHandle.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (db != null) db.close();
        options.close();
    }

    private MyFunction<Object, Boolean> saveTransaction = (txObject -> {
        Transaction transaction = (Transaction) txObject;
        WriteBatch batch = new WriteBatch();
        byte[] key = transaction.hash.bytes();
        batch.put(transactionHandle, key, transaction.bytes);
        batch.put(transactionValidityHandle, key, Serializer.serialize(transaction.validity));
        batch.put(transactionTypeHandle, key, Serializer.serialize(transaction.type));
        batch.put(transactionArrivalTimeHandle, key, Serializer.serialize(transaction.arrivalTime));
        batch.merge(addressHandle, transaction.address.hash.bytes(), key);
        batch.merge(bundleHandle, transaction.bundle.hash.bytes(), key);
        batch.merge(approoveeHandle, transaction.trunk.hash.bytes(), key);
        batch.merge(approoveeHandle, transaction.branch.hash.bytes(), key);
        batch.merge(tagHandle, transaction.tag.value.bytes(), key);
        try {
            db.write(new WriteOptions(), batch);
        } catch (RocksDBException e) {
            e.printStackTrace();
        }
        return true;
    });

    public MyFunction<Object, Boolean> saveAnalyzedTransactionFlag = (flagObj) -> {
        AnalyzedFlag flag = (AnalyzedFlag) flagObj;
        byte[] key = flag.hash.bytes();
        if(db.get(analyzedFlagHandle, key) == null) {
            db.put(analyzedFlagHandle, key, flag.status);
            return false;
        } else {
            return true;
        }
    };

    private MyFunction<Object, Boolean> saveScratchpad = (scratchpadObj) -> {
        Scratchpad scratchpad = ((Scratchpad) scratchpadObj);
        byte[] key = scratchpad.hash.bytes();
        if(db.get(transactionHandle, key) == null && !db.keyMayExist(scratchpadHandle, key, new StringBuffer()))
            db.put(scratchpadHandle, key, scratchpad.status);
        return true;
    };

    private MyFunction<Object, Boolean> saveFlag = (flagObj) -> {
        Flag flag = ((Flag) flagObj);
        byte[] key = flag.hash.bytes();
        if(db.get(flagHandle, key) == null)
            db.put(flagHandle, key, flag.status);
        return true;
    };

    private MyFunction<Object, Boolean> saveTip = (tipObj) -> {
        Tip tip = ((Tip) tipObj);
        byte[] key = tip.hash.bytes();
        db.put(tipHandle, key, tip.status);
        return true;
    };

    @Override
    public boolean save(Object thing) throws Exception {
        return saveMap.get(thing.getClass()).apply(thing);
    }

    StringBuffer stringBuffer = new StringBuffer();

    @Override
    public void delete(Object thing) throws Exception {
        deleteMap.get(thing.getClass()).apply(thing);
    }



    private Hash[] byteToHash(byte[] bytes, int size) {
        if(bytes == null) {
            return new Hash[0];
        }
        int i;
        Set<Hash> hashes = new TreeSet<>();
        for(i = size; i <= bytes.length; i += size + 1) {
            hashes.add(new Hash(Arrays.copyOfRange(bytes, i - size, i)));
        }
        return hashes.stream().toArray(Hash[]::new);
    }

    @Override
    public boolean save(int uuid, Object model) throws Exception {
        byte[] key = getTransientKey(uuid, ((Flag) model).hash.bytes());
        boolean exists = db.keyMayExist(analyzedTipHandle, key, new StringBuffer());
        if (model instanceof AnalyzedFlag) {
            db.put(analyzedTipHandle, key, Serializer.serialize(((AnalyzedFlag) model).status));
        } else if(model instanceof Flag) {
            db.put(analyzedTipHandle, key, Serializer.serialize(((Flag) model).status));
        }
        return exists;
    }

    @Override
    public boolean mayExist(int handle, Hash key) throws Exception {
        byte[] transientKey = getTransientKey(handle, key.bytes());
        boolean mayExist = db.keyMayExist(analyzedTipHandle, transientKey, new StringBuffer());
        return mayExist;
    }

    @Override
    public boolean exists(Class<?> model, Hash key) throws Exception {
        if(model == Transaction.class) {
            return db.get(transactionHandle, key.bytes()) != null;
        } else if (model == AnalyzedFlag.class) {
            return db.get(analyzedFlagHandle, key.bytes()) != null;
        }
        throw new NotImplementedException("Mada mada exists shinai");
    }

    @Override
    public Object get(int uuid, Class<?> model, Hash key) throws Exception {
        Object out = null;
        byte[] result;
        Flag flag;
        if (model == AnalyzedFlag.class) {
            result = db.get(analyzedTipHandle, getTransientKey(uuid, key.bytes()));
            if(result != null) {
                flag = new AnalyzedFlag();
                flag.hash = (key);
                out = flag;
            }
        } else if(model == Flag.class) {
            result = db.get(analyzedTipHandle, getTransientKey(uuid, key.bytes()));
            if(result != null) {
                flag = new Flag();
                flag.hash = (key);
                out = flag;
            }
        }
        return out;
    }

    @Override
    public void copyTransientList(int sourceId, int destId) throws Exception {
        RocksIterator iterator;
        WriteBatch batch = new WriteBatch();
        iterator = db.newIterator(analyzedTipHandle);
        byte[] sourcePre = Serializer.serialize(sourceId);
        byte[] sourceStart = getTransientKey(sourceId, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES);
        byte[] destPre = Serializer.serialize(destId);
        byte[] destKey;
        iterator.seek(sourceStart);
        iterator.next();
        for(; iterator.isValid(); iterator.next()) {
            if(!Arrays.equals(sourcePre, Arrays.copyOfRange(iterator.key(), 0, sourcePre.length)))
                break;
            destKey = ArrayUtils.addAll(destPre, Arrays.copyOfRange(iterator.key(), sourcePre.length, iterator.key().length));
            batch.put(analyzedTipHandle, destKey, iterator.value());
        }
        db.write(new WriteOptions(), batch);
    }

    @Override
    public Object latest(Class<?> model) throws Exception{
        RocksIterator iterator;
        Object out = null;
        if(model == Scratchpad.class) {
            Scratchpad scratchpad = new Scratchpad();
            byte[] randomPosition = new byte[Hash.SIZE_IN_BYTES];
            random.nextBytes(randomPosition);
            iterator = db.newIterator(scratchpadHandle);
            iterator.seek(randomPosition);
            iterator.next();
            if(!iterator.isValid()) {
                iterator.seek(randomPosition);
                iterator.prev();
            }
            if(!iterator.isValid()) {
                iterator.seekToFirst();
            }
            if(iterator.isValid()) {
                scratchpad.hash = new Hash(iterator.key());
            }
            out = scratchpad;
            iterator.close();
        }
        return out;
    }

    @Override
    public Object[] getKeys(Class<?> modelClass) throws Exception {
        RocksIterator iterator;
        Object[] out = null;
        if(modelClass == Tip.class) {
            List<Tip> tips = new ArrayList<>();
            iterator = db.newIterator(tipHandle);
            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                tips.add(new Tip(new Hash(iterator.key())));
            }
            out = tips.toArray();
        }
        return out;
    }

    @Override
    public boolean get(Object model) throws Exception {
        return loadMap.get(model.getClass()).apply(model);
    }

    private MyFunction<Object, Boolean> getTransaction = (txObject) -> {
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
        return true;
    };

    private MyFunction<Object, Boolean> getAddress = (addrObject) -> {
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

    private MyFunction<Object, Boolean> getTag = tagObj -> {
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

    private MyFunction<Object, Boolean> getBundle = bundleObj -> {
        Bundle bundle = ((Bundle) bundleObj);
        byte[] result = db.get(bundleHandle, bundle.hash.bytes());
        if(result != null) {
            bundle.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return true;
        }
        return false;
    };

    private MyFunction<Object, Boolean> getApprovee = approveeObj -> {
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
    public void flush(Class<?> modelClass) throws Exception {
        ColumnFamilyHandle[] handles = classTreeMap.get(modelClass);
        for(ColumnFamilyHandle handle: handles) {
            flushHandle(handle);
        }
    }

    @Override
    public long count(Class<?> model) throws Exception {
        ColumnFamilyHandle handle = countMap.get(model);
        return db.getLongProperty(handle, "rocksdb.estimate-num-keys");
    }

    private void flushHandle(ColumnFamilyHandle handle) throws RocksDBException {
        db.flush(new FlushOptions().setWaitForFlush(true), handle);
        RocksIterator iterator = db.newIterator(handle);
        for(iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
            db.delete(handle, iterator.key());
        }
    }


    @Override
    public boolean setTransientFlagHandle(int uuid) throws RocksDBException {
        return true;
    }

    @Override
    public void flushTagRange(int id) throws Exception {
        byte[] idBytes, start, keyStart;
        RocksIterator iterator;
        idBytes = Serializer.serialize(id);
        start = getTransientKey(id, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES);
        iterator = db.newIterator(analyzedTipHandle);
        iterator.seek(start);
        iterator.next();
        for(; iterator.isValid(); iterator.next()) {
            keyStart = Arrays.copyOfRange(iterator.key(), 0, idBytes.length);
            if(!Arrays.equals(idBytes, keyStart)) break;
            db.delete(analyzedTipHandle, iterator.key());
        }
    }

    @Override
    public boolean update(Object thing, String item) throws Exception {
        if(thing instanceof Transaction) {
            Transaction transaction = (Transaction) thing;
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
                default:
                    throw new NotImplementedException("Mada Sono Update ga dekinai yo");
            }
        } else {
            throw new NotImplementedException("Mada Sono Update ga dekinai yo");
        }
        return true;
    }

    private byte[] getTransientKey(int handle, byte[] key) throws IOException {
        return ArrayUtils.addAll(Serializer.serialize(handle), key);
    }


    void initDB(String path, String logPath) throws Exception {
        random = new Random();
        StringAppendOperator stringAppendOperator = new StringAppendOperator();
        RocksDB.loadLibrary();
        Thread.yield();
        BloomFilter bloomFilter = new BloomFilter(BLOOM_FILTER_RANGE);
        BlockBasedTableConfig blockBasedTableConfig = new BlockBasedTableConfig().setFilter(bloomFilter);
        options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true).setDbLogDir(logPath);

        List<ColumnFamilyHandle> familyHandles = new ArrayList<>();
        List<ColumnFamilyDescriptor> familyDescriptors = Arrays.stream(columnFamilyNames)
                .map(name -> new ColumnFamilyDescriptor(name.getBytes(),
                        new ColumnFamilyOptions()
                                .setMergeOperator(stringAppendOperator).setTableFormatConfig(blockBasedTableConfig))).collect(Collectors.toList());

        familyDescriptors.add(0, new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));

        fillMissingColumns(familyDescriptors, familyHandles, path);

        db = RocksDB.open(options, path, familyDescriptors, familyHandles);

        fillmodelColumnHandles(familyDescriptors, familyHandles);
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
            missingFromDescription.stream().forEach(familyDescriptors::add);
        }
        running = true;
        this.compactionThreadHandle = new Thread(() -> {
            long compationWaitTime = 5 * 60 * 1000;
            while(running) {
                try {
                    for(ColumnFamilyHandle handle: familyHandles) {
                            db.compactRange(handle);
                    }
                    Thread.sleep(compationWaitTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (RocksDBException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void addColumnFamily(byte[] familyName, RocksDB db) throws RocksDBException {
        final ColumnFamilyHandle columnFamilyHandle = db.createColumnFamily(
                new ColumnFamilyDescriptor(familyName,
                        new ColumnFamilyOptions()));
        assert (columnFamilyHandle != null);
    }

    private void fillmodelColumnHandles(List<ColumnFamilyDescriptor> familyDescriptors, List<ColumnFamilyHandle> familyHandles) throws Exception {
        int i = 0;
        transactionHandle = familyHandles.get(++i);
        transactionValidityHandle = familyHandles.get(++i);
        transactionTypeHandle = familyHandles.get(++i);
        transactionArrivalTimeHandle = familyHandles.get(++i);
        addressHandle = familyHandles.get(++i);
        bundleHandle = familyHandles.get(++i);
        approoveeHandle = familyHandles.get(++i);
        tagHandle = familyHandles.get(++i);
        flagHandle = familyHandles.get(++i);
        tipHandle = familyHandles.get(++i);
        scratchpadHandle = familyHandles.get(++i);
        analyzedFlagHandle = familyHandles.get(++i);
        analyzedTipHandle = familyHandles.get(++i);

        flushHandle(analyzedTipHandle);
        flushHandle(analyzedFlagHandle);
        flushHandle(scratchpadHandle);
        updateTagDB();
        scanTxDeleteBaddies();

        db.compactRange();

        transactionGetList = new ArrayList<>();
        for(i = 1; i < 5; i ++) {
            transactionGetList.add(familyHandles.get(i));
        }
    }

    private void scanTxDeleteBaddies() throws Exception {
        RocksIterator iterator = db.newIterator(transactionHandle);
        List<byte[]> baddies = new ArrayList<>();
        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            if(iterator.value().length != TransactionViewModel.SIZE || Arrays.equals(iterator.value(), TransactionViewModel.NULL_TRANSACTION_BYTES)) {
                baddies.add(iterator.key());
            }
        }
        Transaction transaction;
        for(byte[] baddie : baddies) {
            transaction = new Transaction();
            transaction.hash = new Hash(baddie);
            delete(transaction);
        }
    }

    private void updateTagDB() throws RocksDBException {

        RocksIterator iterator = db.newIterator(tagHandle);
        byte[] res, key;
        WriteBatch batch = new WriteBatch();
        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            if(iterator.key().length < Hash.SIZE_IN_BYTES) {
                key = ArrayUtils.addAll(iterator.key(), Arrays.copyOf(TransactionViewModel.NULL_TRANSACTION_HASH_BYTES, Hash.SIZE_IN_BYTES - iterator.key().length));
                batch.put(key, iterator.value());
                db.delete(iterator.key());
            }
        }
        db.write(new WriteOptions(), batch);
    }

    @FunctionalInterface
    private interface MyFunction<T, R> {
       R apply(T t) throws Exception;
    }
}
