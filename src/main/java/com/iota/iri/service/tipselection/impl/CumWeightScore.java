package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import java.util.*;

public class CumWeightScore
{
    public static HashMap<Hash, Double> update(HashMap<Hash, Set<Hash>> graph, HashMap<Hash, Double> score, Hash newVet) {

        HashMap<Hash, Double> ret = score;
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

    public static HashMap<Hash, Double> updateParentScore(Map<Hash, Hash> parentGraph, HashMap<Hash, Double> parentScore, Hash newVet) {
        HashMap<Hash, Double> ret = parentScore;
        Hash start = newVet;
        Set<Hash> visited = new HashSet<>();

        while(parentGraph.get(start) != null) {
            if (visited.contains(start)) {
                System.out.println("Circle exist: " + start);
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

    public static HashMap<Hash, Double> computeParentScore(Map<Hash, Hash> parentGraph, Map<Hash, Set<Hash>> revParentGraph) {
        HashMap<Hash, Double> ret = new HashMap<>();
        
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

    public static HashMap<Hash, Double> compute(HashMap<Hash, Set<Hash>> revGraph, HashMap<Hash, Set<Hash>> graph, Hash genesis) {
        HashMap<Hash, Double> ret = new HashMap<>();
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
