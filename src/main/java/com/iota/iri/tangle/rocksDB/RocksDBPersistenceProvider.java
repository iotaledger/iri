package com.iota.iri.tangle.rocksDB;

import com.iota.iri.conf.Configuration;
import com.iota.iri.tangle.IPersistenceProvider;
import com.iota.iri.tangle.ModelFieldInfo;
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
    private Map<Class<?>, Map<String, RocksField>> modelColumns;

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
        for(Map.Entry<String, RocksField> set: modelColumns.get(modelClass).entrySet()) {
            String fieldName = set.getKey();
            RocksField rocksField = set.getValue();
            Object thingToSerialize;
            thingToSerialize = modelClass.getDeclaredField(fieldName).get(thing);
            if(rocksField.info.belongsTo != null && thingToSerialize != null) {
                thingToSerialize = modelPrimaryKey.get(rocksField.info.belongsTo).get(thingToSerialize);
            }
            byte[] fieldValue = serialize(thingToSerialize);
            if(fieldValue != null) {
                if(rocksField.handle != null) {
                    batch.put(rocksField.handle, primaryKey, fieldValue);
                }
                if(rocksField.ownerHandle != null) {
                    byte[] current = db.get(rocksField.ownerHandle, fieldValue);
                    if(current == null) current = new byte[0];
                    if(current.length < primaryKey.length || !Arrays.asList(current).containsAll(Arrays.asList(primaryKey))) {
                        current = ArrayUtils.addAll(current, primaryKey);
                    }
                    batch.put(rocksField.ownerHandle, fieldValue, current);
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
        for(Map.Entry<String, RocksField> set: modelColumns.get(modelClass).entrySet()) {
            RocksField rocksField = set.getValue();
            if(rocksField.handle != null) {
                Field field = modelClass.getDeclaredField(set.getKey());
                byte[] result = db.get(rocksField.handle, primaryKey);
                if(result != null) {
                    Field subField;
                    if((subField = modelPrimaryKey.get(field.getType())) != null) {
                        field.set(thing, field.getType().newInstance());
                        subField.set(field.get(thing), deserialize(result, subField.getType()));
                    } else {
                        field.set(thing, deserialize(result, field.getType()));
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void setColumns(Map<Class<?>, Field> modelPrimaryKey, Map<Class<?>, Map<String, ModelFieldInfo>> modelItems) {
        this.modelPrimaryKey = modelPrimaryKey;
        this.modelColumns = modelItems
                .entrySet()
                .stream()
                .map(set -> new HashMap.SimpleEntry<Class<?>, Map<String, RocksField>>(
                        set.getKey(),
                        set.getValue()
                                .entrySet()
                                .stream()
                                .map(field -> new HashMap.SimpleEntry<String, RocksField>(
                                        field.getKey(),
                                        new RocksField(field.getValue())))
                                .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue))))
                .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
    }

    @Override
    public Object query(Class<?> modelClass, String index, Object value, int keyLength) throws Exception{
        return queryMany(modelClass, index, value, keyLength);
        /*
        Class modelClass = thing.getClass();
        RocksField rocksField = modelColumns.get(modelClass).get(index);
        if(rocksField != null && rocksField.ownerHandle != null) {
            byte[] secondaryKey = serialize(modelPrimaryKey.get(value.getClass()).get(value));
            byte[] key = db.get(rocksField.ownerHandle, secondaryKey);
            if(key != null) {
                return get(thing, deserialize(key, modelPrimaryKey.get(modelClass).getType()));
            }
        }
        */
    }
    @Override
    public Object[] queryMany(Class<?> modelClass, String index, Object value, int keyLength) throws Exception {
        RocksField rocksField = modelColumns.get(modelClass).get(index);
        if(rocksField != null && rocksField.ownerHandle != null) {
            int numberOfKeys;
            Field field = modelPrimaryKey.get(rocksField.info.belongsTo);
            byte[] secondaryKey = serialize(field.get(value));
            byte[] primaryKeys = db.get(rocksField.ownerHandle, secondaryKey);
            if(primaryKeys != null && (numberOfKeys = primaryKeys.length / keyLength) != 0) {
                Object[] output = new Object[numberOfKeys];
                for(int i = 0; i < numberOfKeys; i++) {
                    byte[] key = Arrays.copyOfRange(primaryKeys, i*keyLength, (i+1)*keyLength);
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

    public Object[] queryMany2(Class<?> modelClass, String index, Object value, int keyLength) throws Exception {
        Map<String, RocksField> map = modelColumns.get(modelClass);
        RocksField rocksField = map.get(index);
        if(rocksField != null && rocksField.handle != null) {
            int numberOfKeys;
            byte[] secondaryKey = serialize(value);
            byte[] primaryKeys = db.get(rocksField.handle, secondaryKey);
            if(primaryKeys != null && (numberOfKeys = primaryKeys.length / keyLength) != 0) {
                Object[] output = new Object[numberOfKeys];
                for(int i = 0; i < numberOfKeys; i++) {
                    byte[] key = Arrays.copyOfRange(primaryKeys, i*keyLength, (i+1)*keyLength);
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
        RocksField rocksField = modelColumns.get(modelClass).get(item);
        if(rocksField != null && rocksField.handle != null) {
            Field primaryKeyField = modelPrimaryKey.get(modelClass);
            byte[] primaryKey = serialize(primaryKeyField.get(thing));
            byte[] newValue = serialize(value);
            db.put(rocksField.handle, primaryKey, newValue);
        }
        return false;
    }


    void initDB(String path) throws Exception {
        RocksDB.loadLibrary();
        options = new DBOptions().setCreateIfMissing(true);

        List<ColumnFamilyHandle> familyHandles = new ArrayList<>();
        List<ColumnFamilyDescriptor> familyDescriptors = modelColumns
                .values()
                .stream()
                .map(s -> s.values()
                        .stream()
                        .map(e ->
                                new ColumnFamilyDescriptor(
                                        e.info.name.getBytes(),
                                        new ColumnFamilyOptions()))
                        .collect(Collectors.toList()))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        familyDescriptors.add(0, new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));

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
        for(ColumnFamilyDescriptor d: familyDescriptors) {
            String name = new String(d.columnFamilyName());
            RocksField f = modelColumns.values()
                    .stream()
                    .map(s -> s.values()
                            .stream()
                            //.map(e -> e.getValue())
                            .filter(rocksField -> {
                                boolean test = name.equals(rocksField.info.name);
                                return test;
                            })
                            .findAny()
                            .orElse(null)
                    )
                    .filter(n -> n != null)
                    .findAny()
                    .orElse(null);
            if(f != null && f.info != null) {
                if(name.equals(f.info.name)) {
                    f.handle = familyHandles.get(familyDescriptors.indexOf(d));
                }
            }
        }
        modelColumns.values()
                .stream()
                .forEach( s -> s.values()
                        .stream()
                        .filter(f -> f.info.belongsTo != null)
                        .forEach(f -> {
                            f.ownerHandle = modelColumns.get(f.info.belongsTo)
                                    .values()
                                    .stream()
                                    .filter(v -> v.info.owns == f.info.memberOf)
                                    .map(v ->
                                            v.handle)
                                    .findAny()
                                    .orElse(null);
                        })
        );
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

    class RocksField {
        public ModelFieldInfo info;
        ColumnFamilyHandle handle;
        ColumnFamilyHandle ownerHandle;

        public RocksField() {}
        public RocksField(ModelFieldInfo info) {
            this.info = info;
        }
    }

    /*
    public void setColumns(Map<Class<?>, Field> modelPrimaryKey, Map<Class<?>, Set<Field>> modelIndices, Map<Class<?>, Set<Field>> modelStoredItems) {
        this.modelPrimaryKey = modelPrimaryKey;

        Map<Class<?>, Map<String, RocksField>> modelColumns = modelIndices.entrySet().parallelStream()
                .map(entrySet ->
                        new HashMap.SimpleEntry<Class<?>, Map<String, RocksField>>(entrySet.getKey(),
                                entrySet.getValue().stream()
                                        .map(entry -> {
                                            RocksField rocksField = new RocksField();
                                            rocksField.indexColumnName = SECONDARY_INDEX_PRE + entrySet.getKey().getSimpleName() + COLUMN_DELIMETER + entry.getName();
                                            if(modelStoredItems.get(entrySet.getKey()).contains(entry)) {
                                                rocksField.itemColumnName = entrySet.getKey().getSimpleName() + COLUMN_DELIMETER + entry.getName();
                                            }
                                            return new HashMap.SimpleEntry<>(entry.getName(), rocksField);
                                        })
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        modelStoredItems.entrySet().stream().forEach(entrySet -> {
            entrySet.getValue().parallelStream().forEach(entry -> {
                RocksField rocksField = modelColumns.get(entrySet.getKey()).get(entry.getName());
                if(rocksField == null) {
                    rocksField = new RocksField();
                    rocksField.itemColumnName = entrySet.getKey().getSimpleName() + COLUMN_DELIMETER + entry.getName();
                    modelColumns.get(entrySet.getKey()).put(entry.getName(), rocksField);
                }
            });
        });
    }
    */
}


