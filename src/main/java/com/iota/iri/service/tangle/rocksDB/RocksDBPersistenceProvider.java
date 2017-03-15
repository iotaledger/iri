package com.iota.iri.service.tangle.rocksDB;

import com.iota.iri.conf.Configuration;
import com.iota.iri.service.tangle.IPersistenceProvider;
import com.iota.iri.service.tangle.ModelFieldInfo;
import com.iota.iri.service.tangle.Serializer;
import com.iota.iri.service.tangle.annotations.*;
import org.apache.commons.lang3.ArrayUtils;
import org.rocksdb.*;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class RocksDBPersistenceProvider implements IPersistenceProvider {

    private Map<Class<?>, Field> modelPrimaryKey;
    private Map<Class<?>, Map<String, RocksField>> modelColumns;
    private Map<Object, Map<String, RocksField>> transientColumns = new HashMap<>();
    private Map<Object, ColumnFamilyHandle> transientHandles = new HashMap<>();
    private Map<Object, Field> transientPrimaryKey = new HashMap<>();

    RocksDB db;
    DBOptions options;
    WriteOptions writeOptions;
    private Random random;

    @Override
    public void init(String path) throws Exception{
        initDB(path);
        random = new Random();
    }

    @Override
    public void init() throws Exception {
        initDB(Configuration.string(Configuration.DefaultConfSettings.DB_PATH));
        random = new Random();
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
        byte[] primaryKey = Serializer.serialize(primaryKeyField.get(thing));
        boolean exists = mayExist(modelClass, primaryKey);
        for (Map.Entry<String, RocksField> set : modelColumns.get(modelClass).entrySet()) {
            Object thingToSerialize = modelClass.getDeclaredField(set.getKey()).get(thing);
            if(thingToSerialize != null) {
                RocksField rocksField = set.getValue();
                if (rocksField.info.belongsTo != null && thingToSerialize != null) {
                    Field indexField = modelPrimaryKey.get(rocksField.info.belongsTo);
                    thingToSerialize = indexField.get(thingToSerialize);
                }
                byte[] fieldValue = Serializer.serialize(thingToSerialize);
                if (fieldValue != null) {
                    if (rocksField.handle != null) {
                        batch.put(rocksField.handle, primaryKey, fieldValue);
                    }
                    if (rocksField.ownerHandle != null) {
                        byte[] current = db.get(rocksField.ownerHandle, fieldValue);
                        if (current == null) current = new byte[0];
                        //!Arrays.asList(current).containsAll(Arrays.asList(primaryKey))
                        if (current.length < primaryKey.length || new String(current).indexOf(new String(primaryKey)) == -1) {
                            current = ArrayUtils.addAll(current, primaryKey);
                            batch.put(rocksField.ownerHandle, fieldValue, current);
                        }
                    }
                }
            }
        }
        db.write(new WriteOptions(), batch);
        return exists;
    }

    @Override
    public void delete(Object thing) throws Exception {
        Class modelClass = thing.getClass();
        Field primaryKeyField = modelPrimaryKey.get(modelClass);
        byte[] primaryKey = Serializer.serialize(primaryKeyField.get(thing));
        for (Map.Entry<String, RocksField> set : modelColumns.get(modelClass).entrySet()) {
            RocksField rocksField = set.getValue();
            if(rocksField.handle != null) {
                db.delete(rocksField.handle, primaryKey);
            }
            if(rocksField.ownerHandle != null) {
                db.delete(rocksField.ownerHandle, primaryKey);
            }
        }
    }

    @Override
    public Object get(Class<?> modelClass, Object key) throws Exception {
        Object thing = modelClass.newInstance();
        modelPrimaryKey.get(modelClass).set(thing, key);
        byte[] primaryKey = Serializer.serialize(key), result;
        Field field, subField;
        for (Map.Entry<String, RocksField> set : modelColumns.get(modelClass).entrySet()) {
            RocksField rocksField = set.getValue();
            if (rocksField.handle != null) {
                result = db.get(rocksField.handle, primaryKey);
                if (result != null) {
                    field = modelClass.getDeclaredField(set.getKey());
                    subField = field.getType().isArray() ? modelPrimaryKey.get(field.getType().getComponentType()) : modelPrimaryKey.get(field.getType());
                    if (subField != null) {
                        SizedArray sizedArray = subField.getAnnotation(SizedArray.class);
                        if (sizedArray != null) {
                            int numberOfKeys;
                            if ((numberOfKeys = result.length / sizedArray.length()) != 0) {
                                field.set(thing, Array.newInstance(field.getType().getComponentType(), numberOfKeys));
                                Object[] subArray = (Object[]) field.get(thing);
                                for (int i = 0; i < numberOfKeys; i++) {
                                    subArray[i] = field.getType().getComponentType().newInstance();
                                    byte[] theKey = Arrays.copyOfRange(result, i * sizedArray.length(), (i + 1) * sizedArray.length());
                                    subField.set(subArray[i], Serializer.deserialize(theKey, subField.getType()));
                                    //Array.set(field.get(thing), i, deserialize(theKey, subField.getType()));
                                }
                            }
                        } else {
                            field.set(thing, field.getType().newInstance());
                            subField.set(field.get(thing), Serializer.deserialize(result, subField.getType()));
                        }
                    } else {
                        field.set(thing, Serializer.deserialize(result, field.getType()));
                    }
                }
            }
            if(rocksField.ownerHandle != null) {
                result = db.get(rocksField.ownerHandle, primaryKey);
                if(result != null) {
                    field = modelClass.getDeclaredField(set.getKey());
                    subField = modelPrimaryKey.get(rocksField.info.belongsTo);
                    subField.set(field.get(thing), Serializer.deserialize(result, subField.getType()));
                }
            }
        }
        return thing;
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
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Object query(Class<?> modelClass, String index, Object value, int keyLength) throws Exception {
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
        Object[] output = (Object[]) Array.newInstance(modelClass, 0);
        byte[] secondaryKey, primaryKeys;
        if (rocksField != null && rocksField.ownerHandle != null) {
            int numberOfKeys;
            Field field = modelPrimaryKey.get(rocksField.info.belongsTo);
            secondaryKey = Serializer.serialize(field.get(value));
            primaryKeys = db.get(rocksField.ownerHandle, secondaryKey);
            primaryKeys = db.get(rocksField.handle, secondaryKey);
            if (primaryKeys != null && (numberOfKeys = primaryKeys.length / keyLength) != 0) {
                output = (Object[]) Array.newInstance(modelClass, 0);
                for (int i = 0; i < numberOfKeys; i++) {
                    byte[] key = Arrays.copyOfRange(primaryKeys, i * keyLength, (i + 1) * keyLength);
                    output[i] = get(modelClass, key);
                    if (output[i] == null) {
                        return null;
                    }
                }
            }
        }
        return output;
    }

    @Override
    public boolean setTransientHandle(Class<?> model, Object uuid) throws ExceptionInInitializerError {
        final ColumnFamilyHandle columnFamilyHandle;
        try {
            columnFamilyHandle = db.createColumnFamily(
                    new ColumnFamilyDescriptor(
                            uuid.toString().getBytes(),
                            new ColumnFamilyOptions()));
            transientHandles.put(uuid, columnFamilyHandle);
            transientPrimaryKey.put(uuid, modelPrimaryKey.get(model));
            transientColumns.put(uuid,
                    modelColumns.get(model).entrySet().stream().map(set -> {
                        String key = set.getKey();
                        RocksField field = new RocksField(set.getValue().info);
                        try {
                            field.handle =
                                    db.createColumnFamily(
                                            new ColumnFamilyDescriptor(
                                                    new String(uuid.toString() + field.info.name).getBytes(),
                                                    new ColumnFamilyOptions()));
                            if (field.info.belongsTo != null) {
                                field.ownerHandle =
                                        db.createColumnFamily(
                                                new ColumnFamilyDescriptor(
                                                        new String(uuid.toString() + field.info.belongsTo.getName()).getBytes(),
                                                        new ColumnFamilyOptions()));
                            }
                        } catch (RocksDBException e) {
                            throw new ExceptionInInitializerError(e);
                        }
                        return new HashMap.SimpleEntry<String, RocksField>(key, field);
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            return true;
        } catch (RocksDBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void dropTransientHandle(Object uuid) throws Exception {
        ColumnFamilyHandle handle = transientHandles.get(uuid);
        db.dropColumnFamily(handle);
    }


    @Override
    public boolean save(Object uuid, Object model) throws Exception {
        WriteBatch batch = new WriteBatch();

        /*
        ColumnFamilyHandle handle = transientHandles.get(uuid);
        */
        Field keyField = transientPrimaryKey.get(uuid);
        byte[] primaryKey = Serializer.serialize(keyField.get(model));



        boolean exists = mayExist(uuid, primaryKey);
        Class modelClass = model.getClass();
        for (Map.Entry<String, RocksField> set : transientColumns.get(uuid).entrySet()) {
            String fieldName = set.getKey();
            RocksField rocksField = set.getValue();
            Object thingToSerialize;
            thingToSerialize = modelClass.getDeclaredField(fieldName).get(model);
            if (rocksField.info.belongsTo != null && thingToSerialize != null) {
                Field indexField = modelPrimaryKey.get(rocksField.info.belongsTo);
                thingToSerialize = indexField.get(thingToSerialize);
            }
            byte[] fieldValue = Serializer.serialize(thingToSerialize);
            if (fieldValue != null) {
                if (rocksField.handle != null) {
                    batch.put(rocksField.handle, primaryKey, fieldValue);
                }
                if (rocksField.ownerHandle != null) {
                    byte[] current = db.get(rocksField.ownerHandle, fieldValue);
                    if (current == null) current = new byte[0];

                    if (current.length < primaryKey.length || new String(current).indexOf(new String(primaryKey)) == -1) {
                        current = ArrayUtils.addAll(current, primaryKey);
                    }
                    batch.put(rocksField.ownerHandle, fieldValue, current);
                }
            }
        }
        db.write(new WriteOptions(), batch);
        return exists;
    }

    @Override
    public boolean mayExist(Class<?> modelClass, Object index) throws Exception {
        StringBuffer stringBuffer = new StringBuffer();
        byte[] primaryKey = Serializer.serialize(index);
        boolean mayExist = false;
        Field primaryField = modelPrimaryKey.get(modelClass);
        for (RocksField rocksField: modelColumns.get(modelClass).values()) {
            if (rocksField.handle != null) {
                mayExist = db.keyMayExist(rocksField.handle, primaryKey, stringBuffer);
                if (mayExist) break;
            }
            if (rocksField.ownerHandle != null) {
                mayExist = db.keyMayExist(rocksField.ownerHandle, primaryKey, stringBuffer);
                if (mayExist) break;
            }
        }
        return mayExist;
    }

    @Override
    public boolean mayExist(Object uuid, Object index) throws Exception {
        StringBuffer stringBuffer = new StringBuffer();
        byte[] primaryKey = Serializer.serialize(index);
        boolean mayExist = false;
        for (Map.Entry<String, RocksField> set : transientColumns.get(uuid).entrySet()) {
            RocksField rocksField = set.getValue();
            if (rocksField.handle != null) {
                mayExist = db.keyMayExist(rocksField.handle, primaryKey, stringBuffer);
                if (mayExist) break;
            }
            if (rocksField.ownerHandle != null) {
                mayExist = db.keyMayExist(rocksField.ownerHandle, primaryKey, stringBuffer);
                if (mayExist) break;
            }
        }
        return mayExist;
    }

    @Override
    public Object get(Object uuid, Class<?> model, Object key) throws Exception {
        Object out = model.newInstance();
        Field f = transientPrimaryKey.get(uuid);
        f.set(out, key);
        byte[] primaryKey = Serializer.serialize(key);


        for (Map.Entry<String, RocksField> set : transientColumns.get(uuid).entrySet()) {
            RocksField rocksField = set.getValue();
            if (rocksField.handle != null) {
                f = model.getDeclaredField(set.getKey());
                f.set(out, Serializer.deserialize(db.get(rocksField.handle, primaryKey), f.getType()));
            }
            if (rocksField.ownerHandle != null) {
                f = modelPrimaryKey.get(rocksField.ownerHandle);
                f.set(out, Serializer.deserialize(db.get(rocksField.ownerHandle, primaryKey), f.getType()));
            }
        }
        return out;
    }


    @Override
    public void deleteTransientObject(Object uuid, Object key) throws Exception {
        ColumnFamilyHandle handle = transientHandles.get(uuid);
        db.delete(handle, Serializer.serialize(key));
        byte[] primaryKey = Serializer.serialize(key);
        for (Map.Entry<String, RocksField> set : transientColumns.get(uuid).entrySet()) {
            RocksField rocksField = set.getValue();
            if (rocksField.handle != null) {
                db.delete(rocksField.handle, primaryKey);
            }
            if (rocksField.ownerHandle != null) {
                db.delete(rocksField.ownerHandle, primaryKey);
            }
        }
    }

    @Override
    public void copyTransientList(Object sourceId, Object destId) throws Exception {
        RocksIterator iterator;
        WriteBatch batch = new WriteBatch();
        for (Map.Entry<String, RocksField> set : transientColumns.get(sourceId).entrySet()) {
            RocksField rocksField = set.getValue();
            if (rocksField.handle != null) {
                iterator = db.newIterator(set.getValue().handle);
                for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    batch.put(transientColumns.get(destId).get(set.getKey()).handle, iterator.key(), iterator.value());
                    iterator.next();
                }
            }
            if (rocksField.ownerHandle != null) {
                iterator = db.newIterator(set.getValue().ownerHandle);
                for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    batch.put(transientColumns.get(destId).get(set.getKey()).ownerHandle, iterator.key(), iterator.value());
                    iterator.next();
                }
            }
        }
        db.write(new WriteOptions(), batch);
    }

    @Override
    public Object latest(Class<?> model) throws Exception{
        RocksIterator iterator;
        Object thing = model.newInstance();
        byte[] primaryKey, randomBytes = new byte[49];
        random.nextBytes(randomBytes);
        Field primaryField = modelPrimaryKey.get(model), field;
        for(Map.Entry<String, RocksField> set: modelColumns.get(model).entrySet()) {
            RocksField rocksField = set.getValue();
            ColumnFamilyHandle handle = rocksField.handle != null ? rocksField.handle : rocksField.ownerHandle;
            iterator = db.newIterator(handle);
            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                if (primaryField.get(thing) == null) {
                    primaryKey = iterator.key();
                    primaryField.set(thing, Serializer.deserialize(primaryKey, primaryField.getType()));
                }
                field = model.getDeclaredField(set.getKey());
                field.set(thing, Serializer.deserialize(iterator.value(), field.getType()));
                break;
            }
        }
        if (primaryField.get(thing) == null) {
            thing = null;
        }
        return thing;
    }

    @Override
    public Object[] getKeys(Class<?> modelClass) throws Exception {
        Map<Object, Object> objects = new TreeMap<>();
        Field primaryField = modelPrimaryKey.get(modelClass), field;
        byte[] primaryKey;
        Object thing;
        RocksIterator iterator;
        RocksField rocksField;
        for(Map.Entry<String, RocksField> set: modelColumns.get(modelClass).entrySet()) {
            rocksField = set.getValue();
            if(rocksField.handle != null) {
                iterator = db.newIterator();
                for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    primaryKey = iterator.key();
                    if(objects.get(new String(primaryKey)) == null) {
                        thing = modelClass.newInstance();
                        primaryField.set(thing, Serializer.deserialize(primaryKey, primaryField.getType()));
                        objects.put(new String(primaryKey), thing);
                    }
                }
            }
        }
        return objects.values().toArray();
    }

    @Override
    public boolean update(Object thing, String item, Object value) throws Exception {
        Class modelClass = thing.getClass();
        RocksField rocksField = modelColumns.get(modelClass).get(item);
        if (rocksField != null && rocksField.handle != null) {
            Field primaryKeyField = modelPrimaryKey.get(modelClass);
            byte[] primaryKey = Serializer.serialize(primaryKeyField.get(thing));
            byte[] newValue = Serializer.serialize(value);
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
        for (ColumnFamilyDescriptor d : familyDescriptors) {
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
            if (f != null && f.info != null) {
                if (name.equals(f.info.name)) {
                    f.handle = familyHandles.get(familyDescriptors.indexOf(d));
                }
            }
        }
        modelColumns.values()
                .stream()
                .forEach(s -> s.values()
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

    class RocksField {
        public ModelFieldInfo info;
        ColumnFamilyHandle handle;
        ColumnFamilyHandle ownerHandle;

        public RocksField() {
        }

        public RocksField(ModelFieldInfo info) {
            this.info = info;
        }
    }
}
