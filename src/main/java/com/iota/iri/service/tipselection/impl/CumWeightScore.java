package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import java.util.*;

public class CumWeightScore
{
    public static HashMap<Hash, Double> update(HashMap<Hash, Set<Hash>> graph, HashMap<Hash, Double> score, Hash newVet) {

        HashMap<Hash, Double> ret = score;
        LinkedList<Hash> queue = new LinkedList<>();

        queue.add(newVet);
        Set<Hash> visisted = new HashSet<>();
        visisted.add(newVet);

        while (!queue.isEmpty()) {
            Hash h = queue.pop();
            for(Hash e : graph.get(h)) {
                if(graph.containsKey(e) && !visisted.contains(e)) {
                    queue.add(e);
                    visisted.add(e);
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
        while(parentGraph.get(start) != null) {
            if(parentScore.get(start) == null) {
                parentScore.put(start, 0.0);
            }
            parentScore.put(start, parentScore.get(start)+1.0);
            start = parentGraph.get(start);
        }
  
        return ret;
    }

    public static HashMap<Hash, Double> compute(HashMap<Hash, Set<Hash>> revGraph, HashMap<Hash, Set<Hash>> graph, Hash genesis) {
        HashMap<Hash, Double> ret = new HashMap<>();
        LinkedList<Hash> queue = new LinkedList<>();

        queue.add(genesis);
        Set<Hash> visisted = new HashSet<>();
        visisted.add(genesis);

        while (!queue.isEmpty()) {
            Hash h = queue.pop();
            for(Hash e : revGraph.get(h)) {
                if(revGraph.containsKey(e) && !visisted.contains(e)) {
                    queue.add(e);
                    visisted.add(e);
                }
            }
            ret = update(graph, ret, h);            
        }
        return ret;
    }    
}
