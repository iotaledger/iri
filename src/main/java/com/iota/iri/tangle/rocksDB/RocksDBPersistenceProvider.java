package com.iota.iri.tangle.rocksDB;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Transaction;
import com.iota.iri.tangle.IPersistenceProvider;
import org.apache.commons.lang3.ArrayUtils;
import org.rocksdb.*;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class RocksDBPersistenceProvider implements IPersistenceProvider {

    private Map<Class<?>, Field> modelPrimaryKey;
    private Map<Class<?>, Map<String, FieldInfo>> modelColumns;

    private static final String SECONDARY_INDEX_PRE = "SecondaryIndex.";
    private static final char COLUMN_DELIMETER = '.';

    RocksDB db;
    DBOptions options;
    WriteOptions writeOptions;

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
    public boolean save(Object thing) throws Exception {
        WriteBatch batch = new WriteBatch();
        Class modelClass = thing.getClass();
        Field primaryKeyField = modelPrimaryKey.get(modelClass);
        byte[] primaryKey = serialize(primaryKeyField.get(thing));
        for(Map.Entry<String, FieldInfo> set: modelColumns.get(modelClass).entrySet()) {
            String fieldName = set.getKey();
            FieldInfo fieldInfo = set.getValue();
            byte[] fieldValue = serialize(modelClass.getDeclaredField(fieldName).get(thing));
            if(fieldValue != null) {
                if(fieldInfo.itemHandle != null) {
                    batch.put(fieldInfo.itemHandle, primaryKey, fieldValue);
                }
                if(fieldInfo.indexHandle != null) {
                    byte[] current = db.get(fieldInfo.indexHandle, fieldValue);
                    if(current == null) current = new byte[0];
                    if(current.length < primaryKey.length || !Arrays.asList(current).containsAll(Arrays.asList(primaryKey))) {
                        current = ArrayUtils.addAll(current, primaryKey);
                    }
                    batch.put(fieldInfo.indexHandle, fieldValue, current);
                }
            }
        }
        db.write(new WriteOptions(), batch);
        return false;
    }

    @Override
    public boolean get(Object thing, Object key) throws Exception{
        Class modelClass = thing.getClass();
        modelPrimaryKey.get(modelClass).set(thing, key);
        byte[] primaryKey = serialize(key);
        for(Map.Entry<String, FieldInfo> set: modelColumns.get(modelClass).entrySet()) {
            FieldInfo fieldInfo = set.getValue();
            if(fieldInfo.itemHandle != null) {
                Field field = modelClass.getDeclaredField(set.getKey());
                byte[] result = db.get(fieldInfo.itemHandle, primaryKey);
                if(result != null) {
                    field.set(thing, deserialize(result, field.getType()));
                }
            }
        }
        return true;
    }

    @Override
    public boolean query(Object thing, String index, Object value) throws Exception{
        Class modelClass = thing.getClass();
        FieldInfo fieldInfo = modelColumns.get(modelClass).get(index);
        if(fieldInfo != null && fieldInfo.indexHandle != null) {
            byte[] secondaryKey = serialize(value);
            byte[] key = db.get(fieldInfo.indexHandle, secondaryKey);
            if(key != null) {
                return get(thing, deserialize(key, modelPrimaryKey.get(modelClass).getType()));
            }
        }
        return false;
    }

    @Override
    public Object[] queryMany(Class<?> modelClass, String index, Object value, int keyLength) throws Exception {
        Map<String, FieldInfo> map = modelColumns.get(modelClass);
        FieldInfo fieldInfo = map.get(index);
        if(fieldInfo != null && fieldInfo.indexHandle != null) {
            int numberOfKeys;
            byte[] secondaryKey = serialize(value);
            byte[] primaryKeys = db.get(fieldInfo.indexHandle, secondaryKey);
            if(primaryKeys != null && (numberOfKeys = primaryKeys.length / keyLength) != 0) {
                Object[] output = new Object[numberOfKeys];
                for(int i = 0; i < numberOfKeys; i++) {
                    byte[] key = Arrays.copyOfRange(primaryKeys, i*numberOfKeys, (i+1)*numberOfKeys);
                    output[i] = modelClass.newInstance();
                    if(!get(output[i], key)) {
                        return null;
                    }
                }
                return output;
            }
        }
        return null;
    }

    @Override
    public boolean update(Object thing, String item, Object value) throws Exception {
        Class modelClass = thing.getClass();
        FieldInfo fieldInfo = modelColumns.get(modelClass).get(item);
        if(fieldInfo != null && fieldInfo.itemHandle != null) {
            Field primaryKeyField = modelPrimaryKey.get(modelClass);
            byte[] primaryKey = serialize(primaryKeyField.get(thing));
            byte[] newValue = serialize(value);
            db.put(fieldInfo.itemHandle, primaryKey, newValue);
        }
        return false;
    }

    @Override
    public void setColumns(Map<Class<?>, Field> modelPrimaryKey, Map<Class<?>, Set<Field>> modelIndices, Map<Class<?>, Set<Field>> modelStoredItems) {
        this.modelPrimaryKey = modelPrimaryKey;

        modelColumns = modelIndices.entrySet().parallelStream()
                .map(entrySet ->
                        new HashMap.SimpleEntry<Class<?>, Map<String, FieldInfo>>(entrySet.getKey(),
                                entrySet.getValue().stream()
                                        .map(entry -> {
                                            FieldInfo fieldInfo = new FieldInfo();
                                            fieldInfo.indexColumnName = SECONDARY_INDEX_PRE + entrySet.getKey().getSimpleName() + COLUMN_DELIMETER + entry.getName();
                                            if(modelStoredItems.get(entrySet.getKey()).contains(entry)) {
                                                fieldInfo.itemColumnName = entrySet.getKey().getSimpleName() + COLUMN_DELIMETER + entry.getName();
                                            }
                                            return new HashMap.SimpleEntry<>(entry.getName(), fieldInfo);
                                        })
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        modelStoredItems.entrySet().stream().forEach(entrySet -> {
            entrySet.getValue().parallelStream().forEach(entry -> {
                FieldInfo fieldInfo = modelColumns.get(entrySet.getKey()).get(entry.getName());
                if(fieldInfo == null) {
                    fieldInfo = new FieldInfo();
                    fieldInfo.itemColumnName = entrySet.getKey().getSimpleName() + COLUMN_DELIMETER + entry.getName();
                    modelColumns.get(entrySet.getKey()).put(entry.getName(), fieldInfo);
                }
            });
        });
    }

    void initDB(String path) throws Exception {
        RocksDB.loadLibrary();
        options = new DBOptions().setCreateIfMissing(true);

        List<ColumnFamilyHandle> familyHandles = new ArrayList<>();
        List<ColumnFamilyDescriptor> familyDescriptors = new ArrayList<>();
        familyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));

        modelColumns.entrySet().stream().forEach(s -> {
            s.getValue().entrySet().parallelStream().forEach(e -> {
                if(e.getValue().indexColumnName != null)
                    familyDescriptors.add(new ColumnFamilyDescriptor(e.getValue().indexColumnName.getBytes(), new ColumnFamilyOptions()));
                if(e.getValue().itemColumnName != null)
                    familyDescriptors.add(new ColumnFamilyDescriptor(e.getValue().itemColumnName.getBytes(), new ColumnFamilyOptions()));
            });
        });

        fillMissingColumns(familyDescriptors, familyHandles, path);

        db = RocksDB.open(options,  path, familyDescriptors, familyHandles);

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
        if(missingFromDatabase.size() != 0)
        {
            missingFromDatabase.remove(familyDescriptors.get(0));
            db = RocksDB.open(options,  path, columnFamilies, familyHandles);
            for(ColumnFamilyDescriptor description : missingFromDatabase) {
                System.out.println(new String(description.columnFamilyName()));
                addColumnFamily(description.columnFamilyName(), db);
            }
        }
        if(missingFromDescription.size() != 0) {
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
        familyDescriptors.stream().forEach(d -> {
            String name = new String(d.columnFamilyName());
            FieldInfo f = modelColumns
                    .entrySet()
                    .stream()
                    .map(e -> e.getValue())
                    .map(s ->
                            s.entrySet().stream()
                                    .map(e -> e.getValue())
                                    .filter(fieldInfo -> name.equals(fieldInfo.indexColumnName) || name.equals(fieldInfo.itemColumnName))
                                    .findFirst()
                                    .orElse(new FieldInfo()))
                    .findFirst()
                    .get();
            if(f != null) {
                if(name.equals(f.indexColumnName)) {
                    f.indexHandle = familyHandles.get(familyDescriptors.indexOf(d));
                } else if(name.equals(f.itemColumnName)) {
                    f.itemHandle = familyHandles.get(familyDescriptors.indexOf(d));
                }
            }
        });
    }

    private static byte[] serialize(Object obj) throws IOException{
        if(obj instanceof byte[]) {
            return (byte[])obj;
        } else if (obj == null) {
            return null;
        }
        try(ByteArrayOutputStream b = new ByteArrayOutputStream()){
            try(ObjectOutputStream o = new ObjectOutputStream(b)){
                o.writeObject(obj);
            }
            return b.toByteArray();
        }
    }

    public static Object deserialize(byte[] bytes, Class target) throws IOException, ClassNotFoundException {
        if(target == byte[].class) {
            return bytes;
        }
        try(ByteArrayInputStream b = new ByteArrayInputStream(bytes)){
            try(ObjectInputStream o = new ObjectInputStream(b)){
                return o.readObject();
            }
        }
    }

    class FieldInfo {
        public String itemColumnName;
        public String indexColumnName;
        ColumnFamilyHandle itemHandle;
        ColumnFamilyHandle indexHandle;
    }

}

        /*
        this.modelIndicesColumnName = modelIndices
                .entrySet()
                .stream()
                .map(set -> new HashMap.SimpleEntry<>(set.getKey(),
                        set.getValue()
                                .parallelStream()
                                .map(entry -> (SECONDARY_INDEX_PRE + set.getKey().getSimpleName() + COLUMN_DELIMETER + entry)).
                                collect(Collectors.toSet())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        this.modelStoredItemsColumnName = modelStoredItems
                .entrySet()
                .parallelStream()
                .map(set -> new HashMap.SimpleEntry<>(set.getKey(),
                        set.getValue()
                                .stream()
                                .map(value -> (set.getKey().getSimpleName() + COLUMN_DELIMETER + value))
                                .collect(Collectors.toSet())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        columnFamilyNames = Arrays.asList(
                modelColumns.entrySet().stream()
                        .map(entrySet -> entrySet.getValue())
                        .map(m ->
                                m.entrySet().parallelStream()
                                        .map(f ->
                                                new String[]
                                                        {
                                                                f.getValue().indexColumnName,
                                                                f.getValue().itemColumnName
                                                        })
                                        .reduce((a,b) -> ArrayUtils.addAll(a,b))
                                        .get())
                        .reduce((a,b) -> ArrayUtils.addAll(a,b))
                        .get());
                */
            /*
            for(String familyName : columnFamilyNames) {
                try(final ColumnFamilyHandle columnFamilyHandle = db.createColumnFamily(
                        new ColumnFamilyDescriptor(familyName.getBytes(),
                                new ColumnFamilyOptions()))) {
                    assert (columnFamilyHandle != null);
                }
            }



    void createDBAndFamiliesIfMissing(String path) throws RocksDBException {
        try(final Options options = new Options().setCreateIfMissing(true);
            final RocksDB db = RocksDB.open(options, path)) {

            assert(db != null);

            modelColumns.entrySet().stream().forEach(s -> {
                s.getValue().entrySet().parallelStream().forEach(e -> {
                    try {
                        if(e.getValue().indexColumnName != null) {
                            addColumnFamily(e.getValue().indexColumnName.getBytes(), db);
                        }
                        if(e.getValue().itemColumnName != null) {
                            addColumnFamily(e.getValue().itemColumnName.getBytes(), db);
                        }
                    } catch (RocksDBException err) {
                        err.printStackTrace();
                    }
                });
            });
        }
    }
            */
        /*
        modelIndicesColumnName.get(model).parallelStream().forEach(secondaryIndex -> {
            batch.put(
                    familyHandles.get(columnFamilyNames.indexOf(secondaryIndex)),
                    new byte[0],
                    new byte[0]
            );
        });
        */
