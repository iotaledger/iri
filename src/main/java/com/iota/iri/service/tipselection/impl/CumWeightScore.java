package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CumWeightScore
{
    private static final Logger log = LoggerFactory.getLogger(CumWeightScore.class);

    public static Map<Hash, Double> update(Map<Hash, Set<Hash>> graph, Map<Hash, Double> score, Hash newVet) {
        Map<Hash, Double> ret = score;
        LinkedList<Hash> queue = new LinkedList<>();

        queue.add(newVet);
        Set<Hash> visited = new HashSet<>();
        visited.add(newVet);

        while (!queue.isEmpty()) {
            Hash h = queue.pop();
            for(Hash e : graph.get(h)) {
                if(graph.containsKey(e) && !visited.contains(e)) {
                    queue.add(e);
                    visited.add(e);
                }
            }
            if(ret.get(h)==null) {
                ret.put(h, 1.0);
            } else if(ret.get(h) != null) {
                ret.put(h, ret.get(h)+1.0);
            }

        }
        return ret;
    }

    public static Map<Hash, Double> updateParentScore(Map<Hash, Hash> parentGraph, Map<Hash, Double> parentScore, Hash newVet) {
        Map<Hash, Double> ret = parentScore;
        Hash start = newVet;
        Set<Hash> visited = new HashSet<>();

        while(parentGraph.get(start) != null) {
            if (visited.contains(start)) {
                log.error("Circle exist: " + start);
                break;
            } else {
                visited.add(start);
            }

            if(parentScore.get(start) == null) {
                parentScore.put(start, 0.0);
            }
            parentScore.put(start, parentScore.get(start)+1.0);
            start = parentGraph.get(start);
        }

        return ret;
    }

    public static Map<Hash, Double> computeParentScore(Map<Hash, Hash> parentGraph, Map<Hash, Set<Hash>> revParentGraph) {
        Map<Hash, Double> ret = new ConcurrentHashMap<>();
        
        for(Hash key : parentGraph.keySet()) {
            Hash start = key;
            Set<Hash> visited = new HashSet<>();
            while(start != null && !visited.contains(start)) {
                if(ret.containsKey(start)) {
                    ret.put(start, ret.get(start) + 1);
                } else {
                    ret.put(start, 1.0);
                }
                visited.add(start);
                start = parentGraph.get(start);
            }
        }

        return ret;
    }

    public static Map<Hash, Double> compute(Map<Hash, Set<Hash>> revGraph, Map<Hash, Set<Hash>> graph, Hash genesis) {
        Map<Hash, Double> ret = new ConcurrentHashMap<>();
        LinkedList<Hash> queue = new LinkedList<>();

        queue.add(genesis);
        Set<Hash> visited = new HashSet<>();
        visited.add(genesis);

        while (!queue.isEmpty()) {
            Hash h = queue.pop();
            if(revGraph.containsKey(h)) {
                for(Hash e : revGraph.get(h)) {
                    if((revGraph.containsKey(e) || graph.containsKey(e)) && !visited.contains(e)) {
                        queue.add(e);
                        visited.add(e);
                    }
                }
            }
            ret = update(graph, ret, h);
        }
        return ret;
    }
}
