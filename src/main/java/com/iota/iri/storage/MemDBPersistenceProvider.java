package com.iota.iri.storage;

import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class MemDBPersistenceProvider implements PersistenceProvider {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(MemDBPersistenceProvider.class);

    private final Object syncObj = new Object();

    private final AtomicReference<Map<Class<?>, Map<Indexable, byte[]>>> classTreeMap = new AtomicReference<>();
    private final AtomicReference<Map<Class<?>, Map<Indexable, byte[]>>> metadataReference = new AtomicReference<>();

    private final SecureRandom seed = new SecureRandom();

    private boolean available;

    @Override
    public void init() throws Exception {
        initClassTreeMap();
        restoreBackup(Configuration.string(Configuration.DefaultConfSettings.DB_PATH));
        available = true;
    }

    @Override
    public boolean isAvailable() {
        return this.available;
    }

    private void initClassTreeMap() {
        Map<Class<?>, Map<Indexable, byte[]>> classMap = new HashMap<>();
        classMap.put(Transaction.class, new ConcurrentHashMap<>(10000000));
        classMap.put(Milestone.class, new TreeMap<>());
        classMap.put(StateDiff.class, new ConcurrentHashMap<>(10000000));
        classMap.put(Hashes.class, new ConcurrentHashMap<>(10000000));
        classTreeMap.set(classMap);

        Map<Class<?>, Map<Indexable, byte[]>> metadataMap = new HashMap<>();
        metadataMap.put(Transaction.class, new ConcurrentHashMap<>(10000000));
        metadataMap.put(Milestone.class, new TreeMap<>());
        metadataMap.put(StateDiff.class, new ConcurrentHashMap<>(10000000));
        metadataMap.put(Hashes.class, new ConcurrentHashMap<>(10000000));
        metadataReference.set(metadataMap);
    }

    @Override
    public void shutdown() {
        log.info("Shutting down memdb.");
        try {
            createBackup(Configuration.string(Configuration.DefaultConfSettings.DB_PATH));
        } catch (IOException e) {
            log.error("Could not create memdb backup. ", e);
        }
        classTreeMap.get().values().forEach(Map::clear);
    }

    private byte[] objectBytes(Object o) throws IOException {
        byte[] output;
        if(o instanceof byte[]) {
            return (byte[]) o;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        oos.close();
        output = bos.toByteArray();
        bos.close();
        return output;
    }

    private Object objectFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        Object out = null;
        if(bytes.length > 0) {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            out = ois.readObject();
            ois.close();
            bis.close();
        }
        return out;
    }

    @Override
    public boolean save(Persistable thing, Indexable index) throws Exception {
        if(thing.merge()) {
            merge(thing, index);
        } else {
            classTreeMap.get().get(thing.getClass()).put(index, thing.bytes());
        }
        metadataReference.get().get(thing.getClass()).put(index, thing.metadata());
        return true;
    }

    @Override
    public void delete(Class<?> model, Indexable index) throws Exception {
        //deleteMap.get(model).apply(index.bytes());
        classTreeMap.get().get(model).remove(index);
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
    public boolean exists(Class<?> model, Indexable key) throws Exception {
        Map<Indexable, byte[]> map = classTreeMap.get().get(model);
        if(key != null) {
            if (map instanceof ConcurrentHashMap) {
                return map.containsKey(key);
            } else {
                synchronized (syncObj) {
                    return map.containsKey(key);
                }
            }
        }
        return false;
    }

    @Override
    public Persistable latest(Class<?> model) throws Exception {
        Map<Indexable, byte[]> map = classTreeMap.get().get(model);
        Persistable object = (Persistable) model.newInstance();
        Map.Entry<Indexable, byte[]> result = null;
        if(map instanceof TreeMap) {
            synchronized (syncObj) {
                if(!map.isEmpty()) {
                    result = ((TreeMap<Indexable, byte[]>) map).lastEntry();
                }
            }
        } else {
            result = map.entrySet().stream().reduce((a, b) -> a.getKey().compareTo(b.getKey()) > 0 ? a:b).orElse(null);
        }
        if(result == null) {
            object = null;
        } else {
            object.read(result.getValue());
            object.readMetadata(metadataReference.get().get(model).get(result.getKey()));
        }
        return object;
    }

    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass) throws Exception {
        return classTreeMap.get().get(modelClass).keySet().parallelStream()
                .filter(h -> !classTreeMap.get().get(Hashes.class).containsKey(h))
                .collect(Collectors.toSet());
    }


    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        Map<Indexable, byte[]> map = classTreeMap.get().get(model);
        Persistable object = (Persistable) model.newInstance();
        if(index != null) {
            byte[] bytes;
            if (map instanceof ConcurrentHashMap) {
                bytes = map.get(index);
            } else {
                synchronized (syncObj) {
                    bytes = map.get(index);
                }
            }
            object.read(bytes);
            object.readMetadata(metadataReference.get().get(model).get(index));
        }
        return object;
    }

    @Override
    public boolean mayExist(Class<?> model, Indexable index) throws Exception {
        return exists(model, index);
    }

    @Override
    public long count(Class<?> model) throws Exception {
        Map map = classTreeMap.get().get(model);
        return map == null ? 0 : map.size();
    }

    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        Map handle = classTreeMap.get().get(modelClass);
        if(handle != null) {
            Set<Hash> keySet = handle.keySet();
            return keySet.parallelStream().filter(h -> Arrays.equals(Arrays.copyOf(h.bytes(), value.length), value))
                    .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    @Override
    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        Set<Indexable> hashes = keysStartingWith(model, key);
        Indexable out;
        if(hashes.size() == 0) {
            out = null;
        } else {
            out = (Indexable) hashes.toArray()[seed.nextInt(hashes.size())];
        }
        return get(model, out);
    }

    @Override
    public Persistable next(Class<?> model, Indexable index) throws Exception {
        Map<Indexable, byte[]> map = classTreeMap.get().get(model);
        if(map instanceof TreeMap) {
            Map.Entry entry;
            Persistable object = (Persistable) model.newInstance();
            synchronized (syncObj) {
                if (map.isEmpty()) {
                    return null;
                }
                entry = ((TreeMap<Indexable, byte[]>) map).ceilingEntry(index.incremented());
            }
            if (entry == null) {
                return null;
            }
            byte[] result = (byte[]) entry.getValue();
            if(result == null) {
                object = null;
            } else {
                object.read(result);
                object.readMetadata(metadataReference.get().get(model).get(index));
            }
            return object;
        }
        return null;
    }

    @Override
    public Persistable previous(Class<?> model, Indexable index) throws Exception {
        Map.Entry<Indexable, byte[]> entry;
        Map<Indexable, byte[]> map = classTreeMap.get().get(model);
        final Persistable object;
        if(map instanceof TreeMap) {
            synchronized (syncObj) {
                if (map.isEmpty()) {
                    return null;
                }
                entry = ((TreeMap<Indexable, byte[]>) map).floorEntry(index.decremented());
            }
            if (entry == null) {
                return null;
            }
            byte[] result = entry.getValue();
            if (result == null) {
                object = null;
            } else {
                object = (Persistable) model.newInstance();
                object.read(entry.getValue());
                object.readMetadata(metadataReference.get().get(model).get(entry.getKey()));
            }
        } else {
            object = null;
        }
        return object;
    }

    @Override
    public Persistable first(Class<?> model) throws Exception {
        Persistable object = (Persistable) model.newInstance();
        Map<Indexable, byte[]> map = classTreeMap.get().get(model);
        synchronized (syncObj) {
            if(!map.isEmpty() && map instanceof TreeMap) {
                Map.Entry<Indexable, byte[]> entry = ((TreeMap<Indexable, byte[]>) map).firstEntry();
                object.read(entry.getValue());
                object.readMetadata(metadataReference.get().get(model).get(entry.getKey()));
            }
        }
        return object;
    }

    public boolean merge(Persistable model, Indexable index) throws Exception {
        byte[] current = classTreeMap.get().get(model.getClass()).get(index);
        classTreeMap.get().get(model.getClass()).put(index, ArrayUtils.addAll(current == null? new byte[0]: ArrayUtils.add(current, (byte)44), model.bytes()));
        return current != null;
    }

    @Override
    public boolean saveBatch(Map<Indexable, Persistable> models) throws Exception {
        for(Map.Entry<Indexable, Persistable> entry: models.entrySet()) {
            save(entry.getValue(), entry.getKey());
        }
        return true;
    }

    @Override
    public boolean update(Persistable thing, Indexable index, String item) throws Exception {
        //classTreeMap.get().get(thing.getClass()).put(index, thing.bytes());
        metadataReference.get().get(thing.getClass()).put(index, thing.metadata());
        return true;
    }

    private void createBackup(String path) throws IOException {
        Path dbPath = Paths.get(path);
        if(!dbPath.toFile().exists()) {
            dbPath.toFile().mkdir();
        }
        saveBytes(path + "/transaction.map",objectBytes(classTreeMap.get().get(Transaction.class)));
        saveBytes(path + "/statediff.map",objectBytes(classTreeMap.get().get(StateDiff.class)));
        saveBytes(path + "/hashes.map",objectBytes(classTreeMap.get().get(Hashes.class)));
        synchronized (syncObj) {
            saveBytes(path + "/milestone.map", objectBytes(classTreeMap.get().get(Milestone.class)));
        }
    }

    private void saveBytes(String path, byte[] bytes) throws IOException {
        File file = new File(path);
        file.createNewFile();
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(bytes, 0, bytes.length);
        fos.flush();
        fos.close();
    }

    private void restoreBackup(String path) throws Exception {
        Object db;

        if((db = objectFromBytes(loadBytes(path + "/transaction.map"))) != null) {
            classTreeMap.get().get(Transaction.class).putAll((Map<Indexable, byte[]>) db);
        }

        if((db = objectFromBytes(loadBytes(path + "/hashes.map"))) != null) {
            classTreeMap.get().get(Hashes.class).putAll((Map<Indexable, byte[]>) db);
        }

        if((db = objectFromBytes(loadBytes(path + "/statediff.map"))) != null) {
            classTreeMap.get().get(StateDiff.class).putAll((Map<Indexable, byte[]>) db);
        }

        if((db = objectFromBytes(loadBytes(path + "/milestone.map"))) != null) {
            synchronized (syncObj) {
                classTreeMap.get().get(Milestone.class).putAll((Map<Indexable, byte[]>) db);
            }
        }
    }

    private byte[] loadBytes(String path) throws IOException {
        File inputFile = new File(path);
        if(inputFile.exists()) {
            byte[] data = new byte[(int) inputFile.length()];
            FileInputStream fis = new FileInputStream(inputFile);
            fis.read(data, 0, data.length);
            fis.close();
            return data;
        }
        return new byte[0];
    }

    @FunctionalInterface
    private interface MyFunction<T, R> {
        R apply(T t) throws Exception;
    }

    @FunctionalInterface
    private interface DoubleFunction<T, I> {
        void apply(T t, I i) throws Exception;
    }

    @FunctionalInterface
    private interface MyRunnable<R> {
        R run() throws Exception;
    }
    @FunctionalInterface
    private interface IndexFunction<T> {
        void apply(T t) throws Exception;
    }
}
