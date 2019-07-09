package com.iota.iri.storage;

import java.util.Map.Entry;

import org.apache.commons.collections4.map.ListOrderedMap;

import com.iota.iri.controllers.TransactionViewModel;

public class PersistenceCache<T extends Persistable> implements DataCache<Indexable, T> {

	private Object lock = new Object();

	private PersistenceProvider persistance;

	private ListOrderedMap<Indexable, T> cache;

	private final int calculatedMaxSize;

	private Class<?> model;

	public PersistenceCache(PersistenceProvider persistance, int cacheSizeInBytes, Class<?> persistableModel) {
		this.persistance = persistance;
		this.model = persistableModel;
		
		cache = new ListOrderedMap<Indexable, T>();

		// CacheSize divided by trytes to bytes conversion of size per transaction
		calculatedMaxSize = (int) Math
				.ceil(cacheSizeInBytes / (TransactionViewModel.SIZE * 3 * Math.log(3) / Math.log(2) / 8));
	}

	@Override
	public void shutdown() {
		try {
			writeAll();
		} catch (CacheException e) {
			// We cannot handle this during shutdown
			e.printStackTrace();
		}
		cache.clear();
	}

	@Override
	public void writeAll() throws CacheException {
		synchronized (lock) {
			try {
				for (Entry<Indexable, T> entry : cache.entrySet()) {
					write(entry.getKey(), entry.getValue());
				}
			} catch (Exception e) {
				throw new CacheException(e.getMessage());
			}
		}
	}

	@Override
	public T get(Indexable key) throws CacheException {
		T tvm = cache.get(key);
		if (null != tvm) {
			return tvm;
		}

		try {
			tvm = (T) persistance.get(model, key);
		} catch (Exception e) {
			throw new CacheException(e.getMessage());
		}

		add(key, tvm);
		return tvm;
	}

	@Override
	public boolean contains(Indexable key) {
		return cache.containsKey(key);
	}

	@Override
	public void add(Indexable key, T value) throws CacheException {
		if (isFullAfterAdd()) {
			cleanUp();
		}

		cache.put(key, value);
	}

	private void cleanUp() throws CacheException {
		synchronized (lock) {
			try {
				for (int i = 0; i < getNumEvictions(); i++) {
					Indexable oldest = cache.firstKey();
					write(oldest, cache.get(oldest));
					cache.remove(oldest);
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
	}

	private void write(Indexable key, T value) throws Exception {
		persistance.save(value, key);
	}

	private boolean isFullAfterAdd() {
		return cache.size() + 1 >= calculatedMaxSize;
	}

	private int getNumEvictions() {
		return (int) Math.ceil(calculatedMaxSize / 2.0); // Clean up 5%
	}

	@Override
	public int getMaxSize() {
		return calculatedMaxSize;
	}
}
