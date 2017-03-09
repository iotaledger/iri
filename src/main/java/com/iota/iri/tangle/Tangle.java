package com.iota.iri.tangle;

import com.iota.iri.tangle.annotations.*;
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
public class Tangle {
    private static final char COLUMN_DELIMETER = '.';

    public static Tangle instance = new Tangle();
    List<IPersistenceProvider> persistenceProviders = new ArrayList<>();
    private ExecutorService executor;
    private final Map<Class<?>, Field> modelPrimaryKeys = new HashMap<>();
    private final Map<Class<?>, Map<String, ModelFieldInfo>> modelFieldInfo = new HashMap<>();
    private final List<UUID> transientDBList = new ArrayList<>();

    {
        FieldAnnotationsScanner scanner = new FieldAnnotationsScanner();
        TypeAnnotationsScanner typeAnnotationsScanner = new TypeAnnotationsScanner();
        SubTypesScanner subTypesScanner = new SubTypesScanner();
        Reflections reflections = new Reflections("com.iota.iri", scanner, typeAnnotationsScanner, subTypesScanner);

        Set<Class<?>> modelClasses = reflections.getTypesAnnotatedWith(Model.class);
        Set<Field> primaryIndex = reflections.getFieldsAnnotatedWith(ModelIndex.class);
        Set<Field> hasOneFields = reflections.getFieldsAnnotatedWith(HasOne.class);
        Set<Field> hasManyFields = reflections.getFieldsAnnotatedWith(HasMany.class);
        Set<Field> belongsToFields = reflections.getFieldsAnnotatedWith(BelongsTo.class);
        for(Class<?> model: modelClasses) {
            modelPrimaryKeys.put(model,
                    primaryIndex
                            .stream()
                            .filter(field -> field.getDeclaringClass().equals(model))
                            .findFirst()
                            .get());
            Map<String, ModelFieldInfo> map = new HashMap<>(
                    hasOneFields
                            .stream()
                            .filter(field -> field.getDeclaringClass().equals(model))
                            .map(field -> new HashMap.SimpleEntry<>(field.getName(),
                                    new ModelFieldInfo(referenceFieldName(model, field), field.getDeclaringClass(), false, null, modelClasses.contains(field.getType()) ? field.getType(): null)
                            ))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
            map.putAll(hasManyFields
                    .stream()
                    .filter(field -> field.getDeclaringClass().equals(model) && modelClasses.contains(field.getType().getComponentType()))
                    .map(field -> new HashMap.SimpleEntry<>(field.getName(),
                            new ModelFieldInfo(referenceFieldName(model, field), field.getDeclaringClass(), true, null, modelClasses.contains(field.getType().getComponentType()) ? field.getType().getComponentType(): null)
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
            map.putAll(belongsToFields
                    .stream()
                    .filter(field -> field.getDeclaringClass().equals(model) && modelClasses.contains(field.getType()))
                    .map(field -> new HashMap.SimpleEntry<>(field.getName(),
                            //new ModelFieldInfo(referenceFieldName(model, field), modelClasses.contains(field.getType()) ? field.getType().getComponentType(): null, null)
                            new ModelFieldInfo(referenceFieldName(model, field), field.getDeclaringClass(), false, field.getType(), null)
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
            modelFieldInfo.put(model, map);
        }
    }

    public void addPersistenceProvider(IPersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    public void init() throws Exception {
        executor = Executors.newCachedThreadPool();
        for(IPersistenceProvider provider: this.persistenceProviders) {
            provider.setColumns(modelPrimaryKeys, modelFieldInfo);
            provider.init();
        }
    }

    public void shutdown() {
        this.persistenceProviders.forEach(provider -> transientDBList.stream().forEach(value -> provider.dropTransientHandle(value)));
        this.persistenceProviders.forEach(provider -> provider.shutdown());
        this.persistenceProviders.clear();
    }

    public Object createTransientList(Class<?> model) {
        UUID uuid = UUID.randomUUID();
        this.persistenceProviders.forEach(provider -> provider.setTransientHandle(model,(Object) uuid));
        return uuid;
    }
    public void dropList(Object key) {
        this.persistenceProviders.forEach(provider -> provider.dropTransientHandle(key));
    }

    public List<IPersistenceProvider> getPersistenceProviders() {
        return this.persistenceProviders;
    }

    private String referenceFieldName(Class<?> model, Field field) {
        return model.getName() + COLUMN_DELIMETER + field.getName();
    }

    /*
    public Future<Object> query(Object model, String index, Object value) {
        return executor.submit(() -> {
            boolean success = false;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.query(model, index, value)) {
                    success = true;
                    break;
                }
            }
            return success;
        });
    }
    */

    public Future<Object> load(Class<?> modelClass, Object value) {
        return executor.submit(() -> {
            Object loadableObject = null;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                loadableObject = provider.get(modelClass, value);
                if(loadableObject != null) {
                    break;
                }
            }
            return loadableObject;
        });
    }

    public Future<Boolean> save(Object model) {
        return executor.submit(() -> {
            boolean isNew = true;
            for(IPersistenceProvider provider: persistenceProviders) {
                if(!provider.save(model)) {
                    isNew = false;
                    break;
                }
            }
            return isNew;
        });
    }



    public Future<Boolean> update(Object model, String item, Object value) {
        return executor.submit(() -> {
            boolean success = true;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(!provider.update(model, item, value)) {
                    success = false;
                    break;
                }
            }
            return success;
        });
    }

    public static Tangle instance() {
        return instance;
    }

    public Future<Object[]> query(Class<?> modelClass, String index, Object key, int length) {
        return executor.submit(() -> {
            Object[] output = null;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                try {
                    output = provider.queryMany(modelClass, index, key, length);
                    break;
                }  catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return output;
        });
    }

    public Future<Boolean> maybeHas(Object handle, Object key) {
        return executor.submit(() -> {
            Object[] output = null;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.maybeHas(handle, key)) return true;
            }
            return false;
        });
    }

    public Future<Object> load(Object handle, Class<?> model, byte[] key) {
        return executor.submit(() -> {
            Object loadableObject = null;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                loadableObject = provider.get(handle, model, key);
                if(loadableObject != null) {
                    break;
                }
            }
            return loadableObject;
        });
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



            modelHasOne.put(model,
                    hasOneFields
                            .stream()
                            .filter(field -> field.getDeclaringClass().equals(model))
                            .collect(Collectors.toSet()));
            modelHasMany.put(model,
                    hasManyFields
                            .stream()
                            .filter(field -> field.getDeclaringClass().equals(model))
                            .collect(Collectors.toSet()));
            modelClassReference.put(model,
                    hasOneFields
                            .stream()
                            .filter(field ->{
                              Class<?> c = field.getType();
                              boolean b = modelClasses.contains(field.getType());
                              boolean t = field.getDeclaringClass().equals(model);
                              return field.getDeclaringClass().equals(model) && modelClasses.contains(field.getType());
                            })
                            .collect(Collectors.toSet()));
            modelClassReference.get(model).addAll(
                    hasManyFields
                            .stream()
                            .filter(field -> field.getDeclaringClass().equals(model) && modelClasses.contains(field.getType().getComponentType()))
                            .collect(Collectors.toSet()));
 */
