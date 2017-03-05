package com.iota.iri.tangle.rocksDB;

import com.iota.iri.conf.Configuration;
import com.iota.iri.tangle.IPersistenceProvider;
import org.rocksdb.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class RocksDBPersistenceProvider implements IPersistenceProvider {
    RocksDB db;
    DBOptions options;

    private Map<Class<?>, String> modelPrimaryKey;
    private Map<Class<?>, Set<String>> modelIndices;
    private Map<Class<?>, Set<String>> modelStoredItems;
    List<ColumnFamilyHandle> familyHandles = new ArrayList<>();
    List<String> columnFamilyNames = new ArrayList<>();

    @Override
    public void init() throws Exception {
        System.out.println(columnFamilyNames.toString());
        db = initDB(Configuration.string(Configuration.DefaultConfSettings.DB_PATH));
    }

    @Override
    public void shutdown() {
        if (db != null) db.close();
        options.close();
    }

    @Override
    public boolean save(Object o) {
        return false;
    }

    @Override
    public boolean get(Object c, Object key) throws Exception{
        return true;
    }

    @Override
    public void setColumns(Map<Class<?>, String> modelPrimaryKey, Map<Class<?>, Set<String>> modelIndices, Map<Class<?>, Set<String>> modelStoredItems) {
        this.modelPrimaryKey = modelPrimaryKey;
        this.modelIndices = modelIndices;
        this.modelStoredItems = modelStoredItems;
        /*
        columnFamilyNames.addAll(
                modelPrimaryKey.entrySet().stream().map(entrySet ->
                        entrySet.getKey().getSimpleName() + "." + entrySet.getValue()
                ).collect(Collectors.toSet()));
                */
        modelIndices.entrySet().stream().forEach(entry ->
            entry.getValue().stream().forEach(
                    value -> columnFamilyNames.add("SecondaryIndex." + entry.getKey().getSimpleName() + "." + value)));
        modelStoredItems.entrySet().stream().forEach(entry ->
                entry.getValue().stream().forEach(
                        value -> columnFamilyNames.add(entry.getKey().getSimpleName() + "." + value)));
    }

    @Override
    public boolean query(Object model, String index, Object value) throws Exception{
        return true;
    }



    RocksDB initDB(String path) throws RocksDBException {
        RocksDB.loadLibrary();

        options = new DBOptions().setCreateIfMissing(true);
        List<ColumnFamilyDescriptor> familyDescriptors = new ArrayList<>();
        familyDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()));
        columnFamilyNames.stream().forEach(f ->
                familyDescriptors.add(new ColumnFamilyDescriptor(f.getBytes(), new ColumnFamilyOptions())));
        RocksDB db;
        try{
            db = RocksDB.open(options,  path, familyDescriptors, familyHandles);
        } catch (Exception e) {
            createDBAndFamiliesIfMissing(path);
            db = RocksDB.open(options,  path, familyDescriptors, familyHandles);
        }
        return db;
    }



    void createDBAndFamiliesIfMissing(String path) throws RocksDBException {
        try(final Options options = new Options().setCreateIfMissing(true);
            final RocksDB db = RocksDB.open(options, path)) {

            assert(db != null);

            for(String familyName : columnFamilyNames) {
                try(final ColumnFamilyHandle columnFamilyHandle = db.createColumnFamily(
                        new ColumnFamilyDescriptor(familyName.getBytes(),
                                new ColumnFamilyOptions()))) {
                    assert (columnFamilyHandle != null);
                }
            }
        }
    }
}
