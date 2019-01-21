package com.iota.iri.storage.localinmemorygraph;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.tipselection.impl.KatzCentrality;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;
import org.apache.commons.collections4.CollectionUtils;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

public class LocalInMemoryGraphProvider implements AutoCloseable, PersistenceProvider {
    public HashMap<Hash, Double> score;
    public HashMap<Hash, Set<Hash>> graph;
    static HashMap<Hash, Set<Hash>> revGraph;
    static HashMap<Hash, Integer> degs;
    public HashMap<Integer, Set<Hash>> topOrder;
    public HashMap<Integer, Set<Hash>> topOrderStreaming;
    static HashMap<Hash, Integer> lvlMap;
    static HashMap<Hash, String> nameMap;
    public int totalDepth;
    private Tangle tangle;
    // to use
    private List<Hash> pivotChain;

    private boolean available;

    public LocalInMemoryGraphProvider(String dbDir, Tangle tangle) {
        this.tangle = tangle;
        graph = new HashMap<>();
        revGraph = new HashMap<>();
        degs = new HashMap<>();
        topOrder = new HashMap<>();
        lvlMap = new HashMap<>();
        topOrderStreaming = new HashMap<>();
        score = new HashMap<>();
        totalDepth = 0;
        try {
            buildGraph();
            buildPivotChain();
        } catch (NullPointerException e) {
            ; // initialization failed because tangle has nothing
        }
    }

    //FIXME for debug
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

    public void shutdown() {
        try {
            close();
        } catch (Exception e) {
            ;
        }
    }

    public boolean save(Persistable model, Indexable index) throws Exception {
        return true;
    }

    public void delete(Class<?> model, Indexable index) throws Exception {
        // TODO implement this
    }

    public boolean update(Persistable model, Indexable index, String item) throws Exception {
        // TODO this function is not implemented or referenced
        return true;
    }

    public boolean exists(Class<?> model, Indexable key) throws Exception {
        // TODO implement this
        return false;
    }

    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception {
        // TODO implement this
        return new Pair<Indexable, Persistable>(new TransactionHash(), new Transaction());
    }

    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass) throws Exception {
        // TODO implement this
        return new HashSet<Indexable>();
    }

    public Persistable get(Class<?> model, Indexable index) throws Exception {
        // TODO implement this
        return new Transaction();
    }

    public boolean mayExist(Class<?> model, Indexable index) throws Exception {
        // TODO implement this
        return false;
    }

    public long count(Class<?> model) throws Exception {
        // TODO implement this
        return (long) 0;
    }

    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        // TODO implement this
        return new HashSet<Indexable>();
    }

    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        // TODO implement this
        return new Transaction();
    }

    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        // TODO implement this
        return new Pair<Indexable, Persistable>(new TransactionHash(), new Transaction());
    }

    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        // TODO implement this
        return new Pair<Indexable, Persistable>(new TransactionHash(), new Transaction());
    }

    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception {
        // TODO implement this
        return new Pair<Indexable, Persistable>(new TransactionHash(), new Transaction());
    }

    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        for (Pair<Indexable, Persistable> entry : models) {
            if (entry.hi.getClass().equals(com.iota.iri.model.persistables.Transaction.class)) {

                Hash key = (Hash) entry.low;
                Transaction value = (Transaction) entry.hi;
                TransactionViewModel model = new TransactionViewModel(value, key);
                Hash trunk = model.getTrunkTransactionHash();
                Hash branch = model.getBranchTransactionHash();

                // Approve graph
                if (graph.get(key) == null) {
                    graph.put(key, new HashSet<>());
                }
                graph.get(key).add(trunk);
                graph.get(key).add(branch);

                // Approvee graph
                if (revGraph.get(trunk) == null) {
                    revGraph.put(trunk, new HashSet<>());
                }
                revGraph.get(trunk).add(key);
                if (revGraph.get(branch) == null) {
                    revGraph.put(branch, new HashSet<>());
                }
                revGraph.get(branch).add(key);

                // update degrees
                if (degs.get(model.getHash()) == null || degs.get(model.getHash()) == 0) {
                    degs.put(model.getHash(), 2);
                }
                if (degs.get(trunk) == null) {
                    degs.put(trunk, 0);
                }
                if (degs.get(branch) == null) {
                    degs.put(branch, 0);
                }
                updateTopologicalOrder(key, trunk, branch);
                updateScore(key);
                break;
            }
        }
        return true;
    }

    // TODO for public  :: Get the graph using the BFS method
    public void buildGraph() {
        try {
            Pair<Indexable, Persistable> one = tangle.getFirst(Transaction.class, TransactionHash.class);
            while (one != null && one.low != null) {
                TransactionViewModel model = new TransactionViewModel((Transaction) one.hi, (TransactionHash) one.low);
                Hash trunk = model.getTrunkTransactionHash();
                Hash branch = model.getBranchTransactionHash();

                // approve direction
                if (graph.get(model.getHash()) == null) {
                    graph.put(model.getHash(), new HashSet<Hash>());
                }
                graph.get(model.getHash()).add(trunk);
                graph.get(model.getHash()).add(branch);

                // approved direction
                if (revGraph.get(trunk) == null) {
                    revGraph.put(trunk, new HashSet<Hash>());
                }
                if (revGraph.get(branch) == null) {
                    revGraph.put(branch, new HashSet<Hash>());
                }
                revGraph.get(trunk).add(model.getHash());
                revGraph.get(branch).add(model.getHash());

                // update degrees
                if (degs.get(model.getHash()) == null || degs.get(model.getHash()) == 0) {
                    degs.put(model.getHash(), 2);
                }
                if (degs.get(trunk) == null) {
                    degs.put(trunk, 0);
                }
                if (degs.get(branch) == null) {
                    degs.put(branch, 0);
                }

                one = tangle.next(Transaction.class, one.low);
            }
            computeToplogicalOrder();
        } catch (NullPointerException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    // base on graph
    public void buildPivotChain(){
        try {
            Pair<Indexable, Persistable> one = tangle.getFirst(Transaction.class, TransactionHash.class);
            TransactionViewModel model = new TransactionViewModel((Transaction) one.hi, (TransactionHash) one.low);
            this.pivotChain = pivotChain(getGenesis(model.getHash()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTopologicalOrder(Hash vet, Hash trunk, Hash branch) {
        if (topOrderStreaming.isEmpty()) {
            topOrderStreaming.put(1, new HashSet<>());
            topOrderStreaming.get(1).add(vet);
            lvlMap.put(vet, 1);
            totalDepth = 1;
            return;
        } else {
            int trunkLevel = lvlMap.get(trunk);
            int branchLevel = lvlMap.get(branch);
            int lvl = Math.min(trunkLevel, branchLevel) + 1;
            if (topOrderStreaming.get(lvl) == null) {
                topOrderStreaming.put(lvl, new HashSet<>());
                totalDepth++;
            }
            topOrderStreaming.get(lvl).add(vet);
            lvlMap.put(vet, lvl);
        }
    }

    private void updateScore(Hash vet) {
        try {
            score.put(vet, 1.0 / (score.size() + 1));
            KatzCentrality centrality = new KatzCentrality(graph, revGraph, 0.5);
            centrality.setScore(score);
            score = centrality.compute();
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    private void computeToplogicalOrder() {
        Deque<Hash> bfsQ = new ArrayDeque<>();
        Map<Hash, Integer> level = new HashMap<Hash, Integer>();
        Set<Hash> visited = new HashSet<Hash>();

        for (Hash h : degs.keySet()) {
            if (!degs.containsKey(h) || degs.get(h) == 0) {
                bfsQ.addLast(h);
                level.put(h, 0);
                break;
            }
        }

        while (!bfsQ.isEmpty()) {
            Hash h = bfsQ.pollFirst();
            int lvl = level.get(h);
            totalDepth = Math.max(totalDepth, lvl + 1);
            if (!topOrder.containsKey(lvl)) {
                topOrder.put(lvl, new HashSet<Hash>());
            }
            topOrder.get(lvl).add(h);

            Set<Hash> out = revGraph.get(h);
            if (out != null) {
                for (Hash o : out) {
                    if (!visited.contains(o)) {
                        bfsQ.addLast(o);
                        visited.add(o);
                        level.put(o, lvl + 1);
                    }
                }
            }
        }
        topOrderStreaming = topOrder;
    }

    public void computeScore() {
        try {
            KatzCentrality centrality = new KatzCentrality(graph, revGraph, 0.5);
            score = centrality.compute();
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    public Hash getPivotalHash(int depth) {
        Hash ret = null;
        if (depth == -1 || depth > totalDepth) {
            Set<Hash> set = topOrderStreaming.get(1);
            ret = set.iterator().next();
            return ret;
        }

        // TODO if the same score, choose randomly
        Set<Hash> hashsOnLevel = topOrderStreaming.get(totalDepth - depth);
        double maxScore = 0;
        for (Hash h : hashsOnLevel) {
            if (score.get(h) >= maxScore) {
                ret = h;
                maxScore = score.get(h);
            }
        }
        return ret;
    }

    //FIXME for debug :: for graphviz visualization
    void printGraph(HashMap<Hash, Set<Hash>> graph) {
        for (Hash key : graph.keySet()) {
            for (Hash val : graph.get(key)) {
                if (nameMap != null) {
                    System.out.println("\"" + nameMap.get(key) + "\"->" +
                            "\"" + nameMap.get(val) + "\"");
                } else {
                    System.out.println("\"" + key + "\"->" +
                            "\"" + val + "\"");
                }
            }
        }
    }

    //FIXME for debug :: for graphviz visualization
    void printRevGraph(HashMap<Hash, Set<Hash>> revGraph) {
        for (Hash key : revGraph.keySet()) {
            for (Hash val : revGraph.get(key)) {
                if (nameMap != null) {
                    System.out.println("\"" + nameMap.get(key) + "\"->" +
                            "\"" + nameMap.get(val) + "\"");
                } else {
                    System.out.println("\"" + key + "\"->" +
                            "\"" + val + "\"");
                }
            }
        }
    }

    //FIXME for debug :: for graphviz visualization
    void printTopOrder(HashMap<Integer, Set<Hash>> topOrder) {
        for (Integer key : topOrder.keySet()) {
            System.out.print(key + ": ");
            for (Hash val : topOrder.get(key)) {
                if (nameMap != null) {
                    System.out.print(nameMap.get(val) + " ");
                } else {
                    System.out.println(val + " ");
                }
            }
            System.out.println();
        }
    }

    public List<Hash> getSiblings(Hash block) {
        try {
            Persistable persistable = this.tangle.find(Transaction.class, block.bytes());
            if (persistable != null && persistable instanceof Transaction) {
                Set<Hash> children = new LinkedHashSet<>();
                children.addAll(revGraph.get(((Transaction) persistable).trunk));
                children.removeIf(t -> t.equals(block));
                return new LinkedList<>(children);
            }
        } catch (Exception e) {
            //
        }
        List<Hash> ret = new LinkedList<Hash>();
        return ret;
    }

    public List<Hash> getChain(HashMap<Integer, Set<Hash>> topOrder) {
        List<Hash> ret = new LinkedList<Hash>();
        Hash b = (Hash) topOrder.get(1).toArray()[0];
        ret.add(b);
        while (true) {
            Set<Hash> children = getChild(b);
            if (children.isEmpty()) {
                break;
            }
            double maxScore = 0;
            for (Hash h : children) {
                if (score.get(h) > maxScore) {
                    maxScore = score.get(h);
                    b = h;
                }
            }
            ret.add(b);
        }
        return ret;
    }

    public Set<Hash> getChild(Hash block) {
        if (revGraph.containsKey(block)) {
            return revGraph.get(block);
        }
        return new HashSet<>();
    }

    //TODO for debug
//    public static void printScore() {
//        for(Hash key : score.keySet()) {
//            if(nameMap != null) {
//                System.out.print(nameMap.get(key)+":"+score.get(key));
//            } else {
//                System.out.print(key+":"+score.get(key));
//            }
//            System.out.println();
//        }
//    }

    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {
        // TODO implement this
    }

    public void clear(Class<?> column) throws Exception {
        // TODO implement this
    }

    public void clearMetadata(Class<?> column) throws Exception {
        // TODO implement this
    }

    public long getTotalTxns() throws Exception {
        long ret = 0;
        return ret;
    }

    public List<Hash> confluxOrder(Hash block) {
        if (getParent(block) == null) {
            return new ArrayList() {{
                add(block);
            }};
        }
        List<Hash> list = confluxOrder(getParent(block));
        List<Hash> diff = new ArrayList<>(past(graph, block));
        diff.removeAll(past(graph, getParent(block)));
        Map<Hash, Set<Hash>> newGraph = new HashMap<>(graph.size());
        graph.entrySet().forEach(e -> newGraph.put(e.getKey(), new HashSet<>(e.getValue())));
        while (diff.size() != 0) {
            Map<Hash, Set<Hash>> subMap = new HashMap<>();
            for (Map.Entry<Hash, Set<Hash>> entry : newGraph.entrySet()) {
                if (diff.contains(entry.getKey())) {
                    Iterator<Hash> iterator = entry.getValue().iterator();
                    while (iterator.hasNext()) {
                        if (!diff.contains(iterator.next())) {
                            iterator.remove();
                        }
                    }
                    subMap.put(entry.getKey(), entry.getValue());
                }
            }
            List<Hash> noBeforeInTmpGraph = subMap.entrySet().stream().filter(e -> CollectionUtils.isEmpty(e.getValue())).map(Map.Entry::getKey).collect(Collectors.toList());
            noBeforeInTmpGraph.sort(Comparable::compareTo);
            list.addAll(noBeforeInTmpGraph);
            diff.removeAll(noBeforeInTmpGraph);
        }
        return list;
    }

    //todo pivot block must be child, for fixing
    public List<Hash> pivotChain(Hash start) {
        try {
            if (revGraph.get(start) == null || revGraph.get(start).size() == 0) {
                return Collections.singletonList(start);
            }
            int w = -1;
            Hash s = null;
            for (Hash b : revGraph.get(start)) {
                int width = revGraph.get(b) == null ? 0 : revGraph.get(b).size();
                if (width > w || (width == w && b.compareTo(s) < 0)) {
                    w = width;
                    s = b;
                }
            }
            List<Hash> chain = new ArrayList<>();
            chain.add(start);
            chain.addAll(pivotChain(s));
            return chain;
        } catch (Exception e) {
//            e.printStackTrace();
        }
        return null;
    }

    private Hash getParent(Hash block) {
        Set<Hash> set = graph.get(block);
        if (CollectionUtils.isEmpty(set) || !CollectionUtils.containsAll(graph.keySet(), set)) {
            return null;
        }
        List<Hash> pivotChain = pivotChain(getGenesis(block));
        return pivotChain.get((pivotChain.indexOf(block) < 1 ? 1 : pivotChain.indexOf(block)) - 1);
    }

    private Hash getGenesis(Hash hash) {
        if (!graph.keySet().containsAll(graph.get(hash))) {
            return hash;
        }
        Set<Hash> parents = graph.get(hash);
        for (Hash h : parents) {
            return getGenesis(h);
        }
        return null;
    }

    public Set<Hash> past(Map<Hash, Set<Hash>> graph, Hash hash) {
        if (!graph.keySet().containsAll(graph.get(hash))) {
            return new HashSet() {{
                add(hash);
            }};
        }
        Set<Hash> past = new HashSet<>();
        for (Hash h : graph.get(hash)) {
            past.addAll(past(graph, h));
        }
        past.add(hash);
        return past;
    }

}
