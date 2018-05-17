package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import com.iota.iri.service.TipsManager;
import com.iota.iri.utils.IotaUtils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PrefixRatingMapImpl implements RatingMap <Hash> {
    private Map<Buffer, Long> delegateMap;

    public PrefixRatingMapImpl(int initialCapacity) {
        this.delegateMap = new HashMap<>(initialCapacity);
    }

    public PrefixRatingMapImpl(Map<Buffer, Long> delegateMap) {
        this.delegateMap = delegateMap;
    }

    @Override
    public int size() {
        return delegateMap.size();
    }

    @Override
    public boolean isEmpty() {
        return delegateMap.isEmpty();
    }


    @Override
    public Long get(Hash key) {
        ByteBuffer hashPrefix = IotaUtils.getHashPrefix(key, TipsManager.SUBHASH_LENGTH);
        return delegateMap.get(hashPrefix);
    }

    @Override
    public Long put(Hash key, Long value) {
        ByteBuffer hashPrefix = IotaUtils.getHashPrefix(key, TipsManager.SUBHASH_LENGTH);
        return delegateMap.put(hashPrefix, value);
    }

    @Override
    public Long remove(Hash key) {
        return delegateMap.remove(key);
    }

    @Override
    public boolean containsKey(Hash key) {
        ByteBuffer hashPrefix = IotaUtils.getHashPrefix(key, TipsManager.SUBHASH_LENGTH);
        return delegateMap.containsKey(hashPrefix);
    }

    @Override
    public boolean containsValue(Hash value) {
        return delegateMap.containsValue(value);
    }


    @Override
    public void clear() {
        delegateMap.clear();
    }

    @Override
    public Collection<Long> values() {
        return delegateMap.values();
    }

}
