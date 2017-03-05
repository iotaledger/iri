package com.iota.iri.tangle;

import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class TangleAccessor {
    public static TangleAccessor instance = new TangleAccessor();
    IPersistenceProvider persistenceProvider;
    private ExecutorService executor;

    Map<Class<?>, Field> modelPrimaryKeys;
    Map<Class<?>, Set<Field>> modelIndices;
    Map<Class<?>, Set<Field>> modelStoredItems;

    public void setPersistenceProvider(IPersistenceProvider provider) {
        this.persistenceProvider = provider;
    }

    public void init() throws Exception {
        executor = Executors.newCachedThreadPool();
        populateModelMaps();
        this.persistenceProvider.setColumns(modelPrimaryKeys, modelIndices, modelStoredItems);
        this.persistenceProvider.init();
    }

    public void shutdown() {
        this.persistenceProvider.shutdown();
    }

    public IPersistenceProvider getPersistenceProvider() {
        return this.persistenceProvider;
    }

    private void populateModelMaps() {
        modelPrimaryKeys = new HashMap<>();
        modelIndices = new HashMap<>();
        modelStoredItems = new HashMap<>();
        FieldAnnotationsScanner scanner = new FieldAnnotationsScanner();
        TypeAnnotationsScanner typeAnnotationsScanner = new TypeAnnotationsScanner();
        SubTypesScanner subTypesScanner = new SubTypesScanner();
        Reflections reflections = new Reflections("com.iota.iri", scanner, typeAnnotationsScanner, subTypesScanner);
        Set<Field> storageItems = reflections.getFieldsAnnotatedWith(IotaModelStoredItem.class);
        Set<Field> primaryIndex = reflections.getFieldsAnnotatedWith(IotaModelIndex.class);
        Set<Field> secondaryIndex = reflections.getFieldsAnnotatedWith(IotaModelSecondaryIndex.class);
        Set<Field> iotaModelItems = reflections.getFieldsAnnotatedWith(IotaModelItem.class);
        reflections.getTypesAnnotatedWith(IotaModel.class)
                .stream()
                .forEach(model -> {
                    modelPrimaryKeys.put(model,
                            primaryIndex
                                    .stream()
                                    .filter(field -> field.getDeclaringClass().equals(model))
                                    //.map(field -> field.getName())
                                    .findFirst()
                                    .get());
                    modelIndices.put(model,
                            secondaryIndex
                                    .stream()
                                    .filter(field -> field.getDeclaringClass().equals(model))
                                    //.map(field -> field.getName())
                                    .collect(Collectors.toSet()));
                    modelStoredItems.put(model,
                            storageItems
                                    .stream()
                                    .filter(field -> field.getDeclaringClass().equals(model))
                                    //.map(field -> field.getName())
                                    .collect(Collectors.toSet()));
                });
    }

    public Future<Boolean> query(Object model, String index, Object value) {
        return executor.submit(() -> {
            if(!this.persistenceProvider.query(model, index, value)) {
                return false;
            }
            return true;
        });
    }

    public Future<Boolean> load(Object model, Object value) {
        return executor.submit(() -> {
            if(!this.persistenceProvider.get(model, value)) {
                // request from neighbors
                return false;
            }
            return true;
        });
    }

    public Future<Boolean> save(Object model) {
        return executor.submit(() -> {
            boolean isNew = this.persistenceProvider.save(model);
            if(isNew) {
                // notify neighbors
            }
            return isNew;
        });
    }

    public static TangleAccessor instance() {
        return instance;
    }

}
/*
Map<Class<?>, Set<Field>[]> classMap;
classMap = new HashMap<>();
classMap.put(model, new Set[]{
                new HashSet<>(primaryIndex
                        .stream()
                        .filter(field -> field.getDeclaringClass().equals(model))
                        .collect(Collectors.toSet())),
                new HashSet<>(secondaryIndex
                        .stream()
                        .filter(field -> field.getDeclaringClass().equals(model))
                        .collect(Collectors.toSet())),
                new HashSet<>(iotaModelItems
                        .stream()
                        .filter(field -> field.getDeclaringClass().equals(model))
                        .collect(Collectors.toSet())),
                new HashSet<>(storageItems
                        .stream()
                        .filter(field -> field.getDeclaringClass().equals(model))
                        .collect(Collectors.toSet()))
        }
);
 */
