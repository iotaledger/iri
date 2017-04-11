package com.iota.iri.model;

import java.util.Map;

/**
 * Created by paul on 4/11/17.
 */
public class Milestone {
    public Long index;
    public Hash hash;
    public Map<Hash, Long> snapshot;
}
