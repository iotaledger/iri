package com.iota.iri.service;

import com.iota.iri.service.dto.AbstractResponse;

import java.util.Map;

public interface CallableRequest<V> {
    public V call(Map<String, Object> request);
}
