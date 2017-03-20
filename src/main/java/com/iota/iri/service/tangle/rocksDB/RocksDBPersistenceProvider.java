package com.iota.iri.service.tangle.rocksDB;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.*;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.tangle.IPersistenceProvider;
import com.iota.iri.service.tangle.Serializer;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.apache.commons.lang3.NotImplementedException;
import org.rocksdb.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class RocksDBPersistenceProvider implements IPersistenceProvider {

    private ExecutorService executor;
    /*
    private Map<Class<?>, Field> modelPrimaryKey;
    private Map<Class<?>, Map<String, RocksField>> modelColumns;
    private Map<Object, Map<String, RocksField>> transientColumns = new HashMap<>();
    */
    private static int BLOOM_FILTER_RANGE = 1<<1;
    private Map<Object, ColumnFamilyHandle> transientHandles = new HashMap<>();
    private Map<Object, Field> transientPrimaryKey = new HashMap<>();

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
    };

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

    List<ColumnFamilyHandle> transactionGetList;

    final byte[] zeroPosition = new byte[Hash.SIZE_IN_BYTES];
    byte[] scratchpadPosition = new byte[Hash.SIZE_IN_BYTES];

    RocksDB db;
    DBOptions options;
    private Random random;

    @Override
    public void init(String path) throws Exception{
        initDB(path);
    }

    @Override
    public void init() throws Exception {
        initDB(Configuration.string(Configuration.DefaultConfSettings.DB_PATH));
    }

    @Override
    public void shutdown() {
        if (db != null) db.close();
        options.close();
    }


    @Override
    public boolean saveTransaction(Transaction transaction) throws RocksDBException, IOException {
        WriteBatch batch = new WriteBatch();
        batch.put(transactionHandle, transaction.hash, transaction.bytes);
        batch.put(transactionValidityHandle, transaction.hash, Serializer.serialize(transaction.validity));
        batch.put(transactionTypeHandle, transaction.hash, Serializer.serialize(transaction.type));
        batch.put(transactionArrivalTimeHandle, transaction.hash, Serializer.serialize(transaction.arrivalTime));
        batch.merge(addressHandle, transaction.address.hash, transaction.hash);
        batch.merge(bundleHandle, transaction.bundle.hash, transaction.hash);
        batch.merge(approoveeHandle, transaction.trunk.hash, transaction.hash);
        batch.merge(approoveeHandle, transaction.branch.hash, transaction.hash);
        batch.merge(tagHandle, transaction.tag.bytes, transaction.hash);
        db.write(new WriteOptions(), batch);
        return true;
    }


    @Override
    public boolean save(Object thing) throws Exception {
        if(thing instanceof Transaction) {
            saveTransaction((Transaction) thing);
        } else if(thing instanceof Tip) {
            saveTip((Tip) thing);
        } else if(thing instanceof Scratchpad) {
            saveScratchpad((Scratchpad) thing);
        } else if(thing instanceof AnalyzedFlag) {
            return saveAnalyzedTransactionFlag(((AnalyzedFlag) thing));
        } else if(thing instanceof Flag) {
            saveFlag((Flag) thing);
        } else {
            return false;
        }
        return true;
    }

    public boolean saveAnalyzedTransactionFlag(AnalyzedFlag flag) throws RocksDBException {
        if(db.get(analyzedFlagHandle, flag.hash) == null) {
            db.put(analyzedFlagHandle, flag.hash, flag.status);
            return false;
        } else {
            return true;
        }
    }

    private void saveScratchpad(Scratchpad thing) throws RocksDBException {
        if(db.get(transactionHandle, thing.hash) == null && !db.keyMayExist(scratchpadHandle, ((Scratchpad) thing).hash, stringBuffer))
            db.put(scratchpadHandle, thing.hash, thing.status);
    }

    private void saveFlag(Flag thing) throws RocksDBException {
        if(db.get(flagHandle, thing.hash) == null)
            db.put(flagHandle, thing.hash, thing.status);
    }

    private void saveTip(Tip tip) throws RocksDBException {
        db.put(tipHandle, tip.hash, tip.status);
    }

    StringBuffer stringBuffer = new StringBuffer();
    @Override
    public void delete(Object thing) throws Exception {
        if(thing instanceof Tip) {
            db.delete(tipHandle, ((Tip) thing).hash);
        } else if(thing instanceof Scratchpad) {
            if(db.keyMayExist(scratchpadHandle, ((Scratchpad) thing).hash, stringBuffer))
                db.delete(scratchpadHandle, ((Scratchpad) thing).hash);
        } else if(thing instanceof Transaction) {
            deleteTransaction((Transaction) thing);
        } else if(thing instanceof AnalyzedFlag) {
            db.delete(analyzedFlagHandle, ((AnalyzedFlag) thing).hash);
        } else if(thing instanceof Flag) {
            db.delete(flagHandle, ((Flag) thing).hash);
        }
    }

    private void deleteTransaction(Transaction transaction) throws RocksDBException {
        db.delete(transactionHandle, transaction.hash);
        db.delete(transactionArrivalTimeHandle, transaction.hash);
        db.delete(transactionTypeHandle, transaction.hash);
        db.delete(transactionValidityHandle, transaction.hash);
    }



    private Hash[] byteToHash(byte[] bytes, int size) {
        if(bytes == null) {
            return new Hash[0];
        }
        int i;
        Set<ByteBuffer> hashes = new TreeSet<>();
        for(i = size; i <= bytes.length; i += size + 1) {
            hashes.add(ByteBuffer.wrap(Arrays.copyOfRange(bytes, i - size, i)));
        }
        return hashes.stream().map(ByteBuffer::array).map(Hash::new).toArray(Hash[]::new);
    }

    @Override
    public boolean setTransientHandle(Class<?> model, Object uuid) throws ExceptionInInitializerError, RocksDBException {
        final ColumnFamilyHandle columnFamilyHandle;
        columnFamilyHandle = db.createColumnFamily(new ColumnFamilyDescriptor(uuid.toString().getBytes()));
        transientHandles.put(uuid, columnFamilyHandle);
        return true;
    }

    @Override
    public void dropTransientHandle(Object uuid) throws Exception {
        ColumnFamilyHandle handle = transientHandles.get(uuid);
        if(handle != null) {
            db.flush(new FlushOptions().setWaitForFlush(true), handle);
            db.dropColumnFamily(handle);
        }
        transientHandles.remove(uuid);
    }

    @Override
    public boolean save(Object uuid, Object model) throws Exception {
        boolean exists = db.keyMayExist(transientHandles.get(uuid), Serializer.serialize(((Flag) model).hash), new StringBuffer());
        if(model instanceof Flag) {
            db.put(transientHandles.get(uuid), ((Flag) model).hash, Serializer.serialize(((Flag) model).status));
        } else if (model instanceof AnalyzedFlag) {
            db.put(transientHandles.get(uuid), ((AnalyzedFlag) model).hash, Serializer.serialize(((AnalyzedFlag) model).status));
        }
        return exists;
    }

    @Override
    public boolean mayExist(Object handle, Object key) throws Exception {
        return db.keyMayExist(transientHandles.get(handle), ((byte[]) key), new StringBuffer());
    }

    @Override
    public boolean exists(Class<?> model, Object key) throws Exception {
        if(model == Transaction.class) {
            return db.get(transactionHandle, (byte[])key) != null;
        } else if (model == AnalyzedFlag.class) {
            return db.get(analyzedFlagHandle, (byte[])key) != null;
        }
        throw new NotImplementedException("Mada mada exists shinai");
    }

    @Override
    public Object get(Object uuid, Class<?> model, Object key) throws Exception {
        Object out = null;
        byte[] result;
        if(model == Flag.class) {
            result = db.get(transientHandles.get(uuid), ((byte[]) key));
            if(result != null) {
                Flag flag = new Flag();
                flag.hash = ((byte[]) key);
            }
        } else if (model == AnalyzedFlag.class) {
            result = db.get(transientHandles.get(uuid), ((byte[]) key));
            if(result != null) {
                AnalyzedFlag flag = new AnalyzedFlag();
                flag.hash = ((byte[]) key);
            }
        }
        return out;
    }


    @Override
    public void deleteTransientObject(Object uuid, Object key) throws Exception {
        ColumnFamilyHandle handle = transientHandles.get(uuid);
        if(db.get(handle, ((byte[]) key)) != null) {
            db.delete(handle, ((byte[]) key));
        }
    }

    @Override
    public void copyTransientList(Object sourceId, Object destId) throws Exception {
        RocksIterator iterator;
        WriteBatch batch = new WriteBatch();
        ColumnFamilyHandle sourceHandle = transientHandles.get(sourceId);
        ColumnFamilyHandle destHandle = transientHandles.get(destId);
        iterator = db.newIterator(sourceHandle);
        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            batch.put(destHandle, iterator.key(), iterator.value());
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
                scratchpad.hash = iterator.key();
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
                tips.add(new Tip(iterator.key()));
            }
            out = tips.toArray();
        }
        return out;
    }

    @Override
    public boolean get(Transaction transaction) throws Exception {
        transaction.bytes = db.get(transactionHandle, transaction.hash);
        if(transaction.bytes != null) {
            transaction.validity = Serializer.getInteger(db.get(transactionValidityHandle, transaction.hash));
            transaction.type = Serializer.getInteger(db.get(transactionTypeHandle, transaction.hash));
            transaction.arrivalTime = Serializer.getLong(db.get(transactionArrivalTimeHandle, transaction.hash));
            return true;
        } else {
            transaction.type = AbstractStorage.PREFILLED_SLOT;
        }
        return false;
    }

    @Override
    public boolean get(Address address) throws Exception {
        byte[] result = db.get(addressHandle, address.hash);
        if(result != null) {
            address.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return  true;
        } else {
            address.transactions = new Hash[0];
            return false;
        }
    }

    @Override
    public boolean get(Tag tag) throws Exception {
        byte[] result = db.get(tagHandle, tag.bytes);
        if(result != null) {
            tag.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return  true;
        } else {
            tag.transactions = new Hash[0];
            return false;
        }
    }

    @Override
    public boolean get(Bundle bundle) throws Exception {
        byte[] result = db.get(bundleHandle, bundle.hash);
        if(result != null) {
            bundle.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return true;
        }
        return false;
    }

    @Override
    public boolean get(Approvee approvee) throws Exception {
        byte[] result = db.get(approoveeHandle, approvee.hash);
        if(result != null) {
            approvee.transactions = byteToHash(result, Hash.SIZE_IN_BYTES);
            return true;
        } else {
            approvee.transactions = new Hash[0];
        return false;
        }
    }

    @Override
    public boolean mayExist(Scratchpad scratchpad) throws Exception {
        return db.keyMayExist(scratchpadHandle, scratchpad.hash, new StringBuffer());
    }

    @Override
    public boolean mayExist(Transaction transaction) throws Exception {
        return db.keyMayExist(transactionHandle, transaction.hash, new StringBuffer());
    }

    @Override
    public boolean mayExist(Tip tip) throws Exception {
        return db.keyMayExist(tipHandle, tip.hash, new StringBuffer());
    }

    @Override
    public void updateType(Transaction transaction) throws Exception {
        db.put(transactionTypeHandle, transaction.hash, Serializer.serialize(transaction.type));
    }

    @Override
    public boolean transientObjectExists(Object uuid, byte[] hash) throws Exception {
        return db.get(transientHandles.get(uuid), hash) != null;
    }

    @Override
    public void flushAnalyzedFlags() throws Exception {
        db.flush(new FlushOptions().setWaitForFlush(true), analyzedFlagHandle);
        RocksIterator iterator = db.newIterator(analyzedFlagHandle);
        for(iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
            db.delete(analyzedFlagHandle, iterator.key());
        }
    }

    @Override
    public void flushScratchpad() throws Exception {
        db.flush(new FlushOptions().setWaitForFlush(true), scratchpadHandle);
        RocksIterator iterator = db.newIterator(scratchpadHandle);
        for(iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
            db.delete(scratchpadHandle, iterator.key());
        }
    }

    @Override
    public long getNumberOfTransactions() throws Exception {
        return db.getLongProperty(transactionHandle, "rocksdb.estimate-num-keys");
    }

    @Override
    public long getNumberOfRequestedTransactions() throws Exception {
        return db.getLongProperty(scratchpadHandle, "rocksdb.estimate-num-keys");
    }

    @Override
    public boolean transactionExists(byte[] hash) throws Exception {
        return db.get(transactionHandle, hash) != null;
    }

    @Override
    public boolean setTransientFlagHandle(Object uuid) throws RocksDBException {
        final ColumnFamilyHandle columnFamilyHandle;
        columnFamilyHandle = db.createColumnFamily(new ColumnFamilyDescriptor(uuid.toString().getBytes()));
        transientHandles.put(uuid, columnFamilyHandle);
        return true;
    }

    @Override
    public void flushTransientFlags(Object id) throws Exception {
        ColumnFamilyHandle handle = transientHandles.get(id);
        if(handle != null) {
            db.flush(new FlushOptions().setWaitForFlush(true), handle);
            RocksIterator iterator = db.newIterator(handle);
            for(iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
                db.delete(handle, iterator.key());
            }
        }
    }

    @Override
    public boolean update(Object thing, String item) throws Exception {
        if(thing instanceof Transaction) {
            Transaction transaction = (Transaction) thing;
            switch (item) {
                case "validity":
                    db.put(transactionValidityHandle, transaction.hash, Serializer.serialize(transaction.validity));
                    break;
                case "type":
                    db.put(transactionValidityHandle, transaction.hash, Serializer.serialize(transaction.type));
                    break;
                case "arrivalTime":
                    db.put(transactionValidityHandle, transaction.hash, Serializer.serialize(transaction.arrivalTime));
                    break;
                default:
                    throw new NotImplementedException("Mada Sono Update ga dekinai yo");
            }
        } else {
            throw new NotImplementedException("Mada Sono Update ga dekinai yo");
        }
        return true;
    }


    void initDB(String path) throws Exception {
        random = new Random();
        executor = Executors.newCachedThreadPool();
        StringAppendOperator stringAppendOperator = new StringAppendOperator();
        RocksDB.loadLibrary();
        Thread.yield();
        BloomFilter bloomFilter = new BloomFilter(BLOOM_FILTER_RANGE);
        BlockBasedTableConfig blockBasedTableConfig = new BlockBasedTableConfig().setFilter(bloomFilter);
        options = new DBOptions().setCreateIfMissing(true);

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

    }

    private void addColumnFamily(byte[] familyName, RocksDB db) throws RocksDBException {
        final ColumnFamilyHandle columnFamilyHandle = db.createColumnFamily(
                new ColumnFamilyDescriptor(familyName,
                        new ColumnFamilyOptions()));
        assert (columnFamilyHandle != null);
    }

    private void fillmodelColumnHandles(List<ColumnFamilyDescriptor> familyDescriptors, List<ColumnFamilyHandle> familyHandles) {
        int i = 1;
        transactionHandle = familyHandles.get(i++);
        transactionValidityHandle = familyHandles.get(i++);
        transactionTypeHandle = familyHandles.get(i++);
        transactionArrivalTimeHandle = familyHandles.get(i++);
        addressHandle = familyHandles.get(i++);
        bundleHandle = familyHandles.get(i++);
        approoveeHandle = familyHandles.get(i++);
        tagHandle = familyHandles.get(i++);
        flagHandle = familyHandles.get(i++);
        tipHandle = familyHandles.get(i++);
        scratchpadHandle = familyHandles.get(i++);
        analyzedFlagHandle = familyHandles.get(i++);

        transactionGetList = new ArrayList<>();
        for(i = 1; i < 5; i ++) {
            transactionGetList.add(familyHandles.get(i));
        }
    }
}
