package com.iota.iri.storage.localinmemorygraph;

import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Pair;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.*;
import com.iota.iri.model.persistables.Address;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.model.persistables.ObsoleteTag;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.tipselection.impl.KatzCentrality;

import java.util.*;
import java.io.*;

public class LocalInMemoryGraphProvider implements AutoCloseable, PersistenceProvider
{
    static HashMap<Hash, Double>       score;
    static HashMap<Hash, Set<Hash>>    graph;
    static HashMap<Hash, Set<Hash>>    revGraph;
    static HashMap<Hash, Integer>      degs;
    static HashMap<Integer, Set<Hash>> topOrder;
    static HashMap<Integer, Set<Hash>> topOrderStreaming;
    static HashMap<Hash, Integer>      lvlMap;
    static HashMap<Hash, String>       nameMap;
    static int                         totalDepth;

    private boolean available;

    public LocalInMemoryGraphProvider( String dbDir) {
        graph = new HashMap<>();
        revGraph = new HashMap<>();
        degs = new HashMap<>();
        topOrder = new HashMap<>();
        lvlMap = new HashMap<>();
        topOrderStreaming = new HashMap<>();
        score = new HashMap<>();
        totalDepth = 0;
    }

    public static void setNameMap(HashMap<Hash, String> nameMap) {
        LocalInMemoryGraphProvider.nameMap = nameMap;
    }

    @Override
    public void close() throws Exception {
        graph = new HashMap<>();
        revGraph = new HashMap<>();
        degs = new HashMap<>();
        topOrder = new HashMap<>();
        totalDepth = 0;
    }

    public void init() throws Exception {
    }

    public boolean isAvailable() {
        return this.available;
    }

    public void shutdown()
    {
        try {
            close();
        } catch(Exception e) {
            ;
        }
    }

    public boolean save(Persistable model, Indexable index) throws Exception
    {
        return true;
    }

    public void delete(Class<?> model, Indexable  index) throws Exception
    {
       // TODO implement this
    }

    public boolean update(Persistable model, Indexable index, String item) throws Exception
    {
        // TODO this function is not implemented or referenced
        return true;
    }

    public boolean exists(Class<?> model, Indexable key) throws Exception
    {
        // TODO implement this
        return false;
    }

    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception
    {
        // TODO implement this
        return new Pair<Indexable,Persistable>(new TransactionHash(), new Transaction()); 
    }

    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass) throws Exception
    {
        // TODO implement this
        return new HashSet<Indexable>();
    }

    public Persistable get(Class<?> model, Indexable index) throws Exception
    {
        // TODO implement this
        return new Transaction();
    }

    public boolean mayExist(Class<?> model, Indexable index) throws Exception
    {
        // TODO implement this
        return false;
    }

    public long count(Class<?> model) throws Exception
    {
        // TODO implement this
        return (long)0;
    }

    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value)
    {
        // TODO implement this
        return new HashSet<Indexable>();
    }

    public Persistable seek(Class<?> model, byte[] key) throws Exception
    {
        // TODO implement this
        return new Transaction();
    }

    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception
    {
        // TODO implement this
        return new Pair<Indexable,Persistable>(new TransactionHash(), new Transaction());
    }

    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception
    {
        // TODO implement this
        return new Pair<Indexable,Persistable>(new TransactionHash(), new Transaction());
    }

    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception
    {
        // TODO implement this
        return new Pair<Indexable,Persistable>(new TransactionHash(), new Transaction());
    }

    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception
    {
        for (Pair<Indexable, Persistable> entry : models) {
            if(entry.hi.getClass().equals(com.iota.iri.model.persistables.Transaction.class)) {
                
                Hash key = (Hash)entry.low;
                Transaction value = (Transaction)entry.hi;
                TransactionViewModel model = new TransactionViewModel(value, key);
                Hash trunk = model.getTrunkTransactionHash();
                Hash branch = model.getBranchTransactionHash();

                // Approve graph
                if(graph.get(key)==null){
                    graph.put(key, new HashSet<>());
                }
                graph.get(key).add(trunk);
                graph.get(key).add(branch);

                // Approvee graph
                if(revGraph.get(trunk)==null) {
                    revGraph.put(trunk, new HashSet<>());
                }
                revGraph.get(trunk).add(key);
                if(revGraph.get(branch) == null) {
                    revGraph.put(branch, new HashSet<>());
                }
                revGraph.get(branch).add(key);

                // update degrees
                if(degs.get(model.getHash()) == null || degs.get(model.getHash()) == 0) {
                    degs.put(model.getHash(), 2);
                } 
                if(degs.get(trunk) == null) {
                    degs.put(trunk, 0);
                }
                if(degs.get(branch) == null) {
                    degs.put(branch, 0);
                }
                updateTopologicalOrder(key, trunk, branch);
                updateScore(key);
                break;
            }
        }
        return true;
    }

    private void updateTopologicalOrder(Hash vet, Hash trunk, Hash branch) {
        if(topOrderStreaming.isEmpty()) {
            topOrderStreaming.put(1, new HashSet<>());
            topOrderStreaming.get(1).add(vet);
            lvlMap.put(vet, 1);
            totalDepth = 1;
            return;
        } else {
            int trunkLevel = lvlMap.get(trunk);
            int branchLevel = lvlMap.get(branch);
            int lvl = Math.min(trunkLevel, branchLevel) +1;
            if(topOrderStreaming.get(lvl)==null) {
                topOrderStreaming.put(lvl, new HashSet<>());
                totalDepth++;
            }
            topOrderStreaming.get(lvl).add(vet);
            lvlMap.put(vet, lvl);
        }
    }

    private void updateScore(Hash vet) {
        score.put(vet, 1.0/(score.size()+1));
        try {
            KatzCentrality centrality = new KatzCentrality(graph, revGraph, 0.5);
            centrality.setScore(score);
            score = centrality.compute();
        } catch(Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    public void computeToplogicalOrder() {
        Deque<Hash> bfsQ = new ArrayDeque<>();
        Map<Hash, Integer> level = new HashMap<Hash, Integer>();
        Set<Hash> visited = new HashSet<Hash>();

        for(Hash h : degs.keySet()) {
            if(!degs.containsKey(h) || degs.get(h) == 0) {
                bfsQ.addLast(h);
                level.put(h, 0);
                break;
            }
        }
       
        while(!bfsQ.isEmpty()) {
            Hash h = bfsQ.pollFirst();
            int lvl = level.get(h);
            totalDepth = Math.max(totalDepth, lvl+1);
            if(!topOrder.containsKey(lvl)) {
                topOrder.put(lvl, new HashSet<Hash>());
            }            
            topOrder.get(lvl).add(h);

            Set<Hash> out = revGraph.get(h);
            if(out != null) {
                for(Hash o : out) {
                    if(!visited.contains(o)){
                        bfsQ.addLast(o);
                        visited.add(o);
                        level.put(o, lvl+1);
                    }
                }
            }
        }
    }

    public void computeScore() {
        try {
            KatzCentrality centrality = new KatzCentrality(graph, revGraph, 0.5);
            score = centrality.compute();
        } catch(Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    public Hash getPivotalHash(int depth)
    {
        Hash ret = null;
        if(depth == -1) {
            Set<Hash> set = topOrderStreaming.get(1);
            ret = set.iterator().next();
            return ret;
        }

        // TODO if the same score, choose randomly
        Set<Hash> hashsOnLevel = topOrderStreaming.get(totalDepth-depth+1);
        double maxScore = 0;
        for(Hash h : hashsOnLevel) {
            if(score.get(h)>=maxScore){
                ret = h;
                maxScore = score.get(h);
            }
        }
        return ret;
    }

    // for graphviz visualization
    void printGraph(HashMap<Hash, Set<Hash>> graph)
    {
        for(Hash key : graph.keySet())
        {
            for(Hash val : graph.get(key))
            {
                if(nameMap != null) {
                    System.out.println("\""+nameMap.get(key)+"\"->"+
                                       "\""+nameMap.get(val)+"\"");
                } else {
                    System.out.println("\""+key+"\"->"+
                                       "\""+val+"\"");
                }
            }
        }
    }

    // for graphviz visualization
    void printRevGraph(HashMap<Hash, Set<Hash>> revGraph)
    {
        for(Hash key : revGraph.keySet())
        {
            for(Hash val : revGraph.get(key))
            {
                if(nameMap != null) {
                    System.out.println("\""+nameMap.get(key)+"\"->"+
                                       "\""+nameMap.get(val)+"\"");
                } else {
                    System.out.println("\""+key+"\"->"+
                                       "\""+val+"\"");
                }
            }
        }
    }

    // for graphviz visualization
    void printTopOrder(HashMap<Integer, Set<Hash>> topOrder)
    {
        for(Integer key : topOrder.keySet())
        {
            System.out.print(key+": ");
            for(Hash val : topOrder.get(key))
            {
                if(nameMap != null) {
                    System.out.print(nameMap.get(val)+" ");
                } else {
                    System.out.println(val+" ");
                }
            }
            System.out.println();
        }
    }

    void printScore() {
        for(Hash key : score.keySet()) {
            if(nameMap != null) {
                System.out.print(nameMap.get(key)+":"+score.get(key));
            } else {
                System.out.print(key+":"+score.get(key));
            }
            System.out.println();
        }
    }

    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception
    {
        // TODO implement this
    }

    public void clear(Class<?> column) throws Exception
    {
        // TODO implement this
    }

    public void clearMetadata(Class<?> column) throws Exception
    {
        // TODO implement this
    }

    public long getTotalTxns() throws Exception
    {
        long ret = 0;
        return ret;
    }
} 
