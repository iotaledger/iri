package com.iota.iri.storage.localinmemorygraph;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.tipselection.impl.CumWeightScore;
import com.iota.iri.service.tipselection.impl.KatzCentrality;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;
import org.apache.commons.collections4.CollectionUtils;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.io.*;

import com.iota.iri.utils.*;

public class LocalInMemoryGraphProvider implements AutoCloseable, PersistenceProvider {
    public HashMap<Hash, Double> score;
    public HashMap<Hash, Set<Hash>> graph;
    public Map<Hash, Hash> parentGraph;
    static HashMap<Hash, Set<Hash>> revGraph;
    public HashMap<Hash, Set<Hash>> parentRevGraph;
    static HashMap<Hash, Integer> degs;
    public HashMap<Integer, Set<Hash>> topOrder;
    public HashMap<Integer, Set<Hash>> topOrderStreaming;

    static HashMap<Hash, Integer> lvlMap;
    public static HashMap<Hash, String> nameMap;
    public int totalDepth;
    private Tangle tangle;
    // to use
    private List<Hash> pivotChain;

    private boolean available;

    public LocalInMemoryGraphProvider(String dbDir, Tangle tangle) {
        this.tangle = tangle;
        graph = new HashMap<>();
        revGraph = new HashMap<>();
        parentGraph = new ConcurrentHashMap<>();
        parentRevGraph = new HashMap<>();
        degs = new HashMap<>();
        topOrder = new HashMap<>();
        lvlMap = new HashMap<>();
        topOrderStreaming = new HashMap<>();
        score = new HashMap<>();
        totalDepth = 0;
    }

    //FIXME for debug
    public static void setNameMap(HashMap<Hash, String> nameMap) {
        LocalInMemoryGraphProvider.nameMap = nameMap;
    }

    @Override
    public void close() throws Exception {
        graph = new HashMap<>();
        revGraph = new HashMap<>();
        parentGraph = new HashMap<>();
        parentRevGraph = new HashMap<>();
        degs = new HashMap<>();
        topOrder = new HashMap<>();
        totalDepth = 0;
        topOrderStreaming = new HashMap<>();
        lvlMap = new HashMap<>();
    }

    public void init() throws Exception {
        try {
            buildGraph();
//            buildPivotChain();
        } catch (NullPointerException e) {
            ; // initialization failed because tangle has nothing
        }
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

                //parentGraph
                parentGraph.put(key, trunk);

                // Approvee graph
                if (revGraph.get(trunk) == null) {
                    revGraph.put(trunk, new HashSet<>());
                }
                revGraph.get(trunk).add(key);
                if (revGraph.get(branch) == null) {
                    revGraph.put(branch, new HashSet<>());
                }
                revGraph.get(branch).add(key);

                if (parentRevGraph.get(trunk) == null) {
                    parentRevGraph.put(trunk, new HashSet<>());
                }
                parentRevGraph.get(trunk).add(key);

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

                //parentGraph
                parentGraph.put(model.getHash(), trunk);

                if (parentRevGraph.get(trunk) == null) {
                    parentRevGraph.put(trunk, new HashSet<>());
                }
                parentRevGraph.get(trunk).add(model.getHash());

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

                updateScore(model.getHash());
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
    public void buildPivotChain() {
        try {
            this.pivotChain = pivotChain(getGenesis());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTopologicalOrder(Hash vet, Hash trunk, Hash branch) {
        if (topOrderStreaming.isEmpty()) {
            topOrderStreaming.put(1, new HashSet<>());
            topOrderStreaming.get(1).add(vet);
            lvlMap.put(vet, 1);
            topOrderStreaming.put(0, new HashSet<>());
            topOrderStreaming.get(0).add(trunk);
            topOrderStreaming.get(0).add(branch);
            totalDepth = 1;
            return;
        } else {
            try {
                int trunkLevel = lvlMap.get(trunk);
                int branchLevel = lvlMap.get(branch);
                int lvl = Math.min(trunkLevel, branchLevel) + 1;
                if (topOrderStreaming.get(lvl) == null) {
                    topOrderStreaming.put(lvl, new HashSet<>());
                    totalDepth++;
                }
                topOrderStreaming.get(lvl).add(vet);
                lvlMap.put(vet, lvl);
            } catch(NullPointerException e) {
                ; // First block, do nothing here
            }
        }
    }

    private void updateScore(Hash vet) {
        try {
            if(BaseIotaConfig.getInstance().getStreamingGraphSupport()){
                if (BaseIotaConfig.getInstance().getConfluxScoreAlgo().equals("CUM_WEIGHT")) {
                    score = CumWeightScore.update(graph, score, vet);
                } else if (BaseIotaConfig.getInstance().getConfluxScoreAlgo().equals("KATZ")) {
                    score.put(vet, 1.0 / (score.size() + 1));
                    KatzCentrality centrality = new KatzCentrality(graph, revGraph, 0.5);
                    centrality.setScore(score);
                    score = centrality.compute();
                }
            }
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
            if(BaseIotaConfig.getInstance().getStreamingGraphSupport()) {
                if (BaseIotaConfig.getInstance().getConfluxScoreAlgo().equals("CUM_WEIGHT")) {
                    score = CumWeightScore.compute(revGraph, graph, getGenesis());
                } else if (BaseIotaConfig.getInstance().getConfluxScoreAlgo().equals("KATZ")) {
                    KatzCentrality centrality = new KatzCentrality(graph, revGraph, 0.5);
                    score = centrality.compute();
                }
            }
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    public Hash getPivotalHash(int depth) {
        Hash ret = null;
        if (depth == -1 || depth >= totalDepth) {
            Set<Hash> set = topOrderStreaming.get(1);
            if(CollectionUtils.isEmpty(set)){
                return null;
            }
            ret = set.iterator().next();
            return ret;
        }

        // TODO if the same score, choose randomly
        buildPivotChain();
        ret = this.pivotChain.get(this.pivotChain.size()-depth-1);
        return ret;
    }

    //FIXME for debug :: for graphviz visualization
    public void printGraph(HashMap<Hash, Set<Hash>> graph, Hash k) {
        try {
            BufferedWriter writer = null;
            if(k != null) {
                writer = new BufferedWriter(new FileWriter(IotaUtils.abbrieviateHash(k,4)));
            }
            for (Hash key : graph.keySet()) {
                for (Hash val : graph.get(key)) {
                    if (nameMap != null) {
                        if(k != null) {
                            writer.write("\"" + nameMap.get(key) + "\"->" +
                                    "\"" + nameMap.get(val) + "\"\n");
                        } else {
                            System.out.println("\"" + nameMap.get(key) + "\"->" +
                                    "\"" + nameMap.get(val) + "\"");
                        }
                    } else {
                        if(k != null) {
                            writer.write("\"" + IotaUtils.abbrieviateHash(key, 4) + "\"->" +
                                    "\"" + IotaUtils.abbrieviateHash(val, 4) + "\"\n");
                        } else {
                            System.out.println("\"" + IotaUtils.abbrieviateHash(key, 4) + "\"->" +
                                    "\"" + IotaUtils.abbrieviateHash(val, 4) + "\"");
                        }
                    }
                }
            }
            if(k != null) {
                writer.close();
            }
        } catch(Exception e) {
            e.printStackTrace();
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
                    System.out.println("\"" + IotaUtils.abbrieviateHash(key, 4) + "\"->" +
                            "\"" + IotaUtils.abbrieviateHash(val, 4) + "\"");
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
                    System.out.println(IotaUtils.abbrieviateHash(val, 4) + " ");
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
            e.printStackTrace();
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

    public void addTxnCount(long count) {
        // TODO implement this
    }

    public long getTotalTxns() {
        return 0;
    }

    public List<Hash> totalTopOrder() {
        return confluxOrder(getPivot(getGenesis()));
    }

    public List<Hash> confluxOrder(Hash block) {
        LinkedList<Hash> list = new LinkedList<>();
        Set<Hash> covered = new HashSet<Hash>();
        if (block == null || !graph.keySet().contains(block)) {
            return list;
        }
        do {
            Hash parent = parentGraph.get(block);
            List<Hash> subTopOrder = new ArrayList<>();
            List<Hash> diff = getDiffSet(block, parent, covered);
            while (diff.size() != 0) {
                Map<Hash, Set<Hash>> subGraph = buildSubGraph(diff);
                List<Hash> noBeforeInTmpGraph = subGraph.entrySet().stream().filter(e -> CollectionUtils.isEmpty(e.getValue())).map(Map.Entry::getKey).collect(Collectors.toList());
                //TODO consider using SPECTR for sorting
                noBeforeInTmpGraph.sort(Comparator.comparingInt((Hash o) -> lvlMap.get(o)).thenComparing(o -> o));
                subTopOrder.addAll(noBeforeInTmpGraph);
                diff.removeAll(noBeforeInTmpGraph);
            }
            list.addAll(0, subTopOrder);
            covered.addAll(subTopOrder);
            block = parentGraph.get(block);
        } while (parentGraph.get(block) != null && parentGraph.keySet().contains(block));
        return list;
    }

    public Map<Hash, Set<Hash>> buildSubGraph(List<Hash> blocks) {
        Map<Hash, Set<Hash>> subMap = new HashMap<>();
        for(Hash h : blocks) {
            Set<Hash> s = graph.get(h);
            Set<Hash> ss = new HashSet<>();
            
            for (Hash hh : s) {
                if(blocks.contains(hh)) {
                    ss.add(hh);
                }
            }
            subMap.put(h, ss);
        }
        return subMap;
    }

    public List<Hash> pivotChain(Hash start) {
        if (start == null || !graph.keySet().contains(start)) {
            return Collections.emptyList();
        }
        ArrayList<Hash> list = new ArrayList<>();
        list.add(start);
        while (!CollectionUtils.isEmpty(parentRevGraph.get(start))) {
            double tmpMaxScore = -1;
            Hash s = null;
            for (Hash block : parentRevGraph.get(start)) {
                if (score.get(block) > tmpMaxScore || (score.get(block) == tmpMaxScore && block.compareTo(Objects.requireNonNull(s)) < 0)) {
                    tmpMaxScore = score.get(block);
                    s = block;
                }
            }
            start = s;
            list.add(s);
        }
        return list;
    }

    public Hash getPivot(Hash start) {
        if (start == null || !graph.keySet().contains(start)) {
            return null;
        }
        while (!CollectionUtils.isEmpty(parentRevGraph.get(start))) {
            Set<Hash> children = parentRevGraph.get(start);
            double tmpMaxScore = -1;
            Hash s = null;
            for (Hash block : children) {
                if (score.get(block) > tmpMaxScore || (score.get(block) == tmpMaxScore && block.compareTo(Objects.requireNonNull(s)) < 0)) {
                    tmpMaxScore = score.get(block);
                    s = block;
                }
            }
            start = s;
        }
        return start;
    }

    public Hash getGenesis() {
        try {
            for (Hash key : parentGraph.keySet()) {
                if (!parentGraph.keySet().contains(parentGraph.get(key))) {
                    return key;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Set<Hash> past(Hash hash) {
        if (graph.get(hash) == null) {
            return Collections.emptySet();
        }
        Set<Hash> past = new HashSet<>();
        LinkedList<Hash> queue = new LinkedList<>();
        queue.add(hash);
        Hash h;
        while (!queue.isEmpty()) {
            h = queue.pop();
            for (Hash e : graph.get(h)) {
                if (graph.containsKey(e) && !past.contains(e)) {
                    queue.add(e);
                }
            }
            past.add(h);
        }
        return past;
    }

    public List<Hash> getDiffSet(Hash block, Hash parent, Set<Hash> covered) {
        if (graph.get(block) == null) {
            return Collections.emptyList();
        }

        Set<Hash> ret = new HashSet<Hash> ();
        LinkedList<Hash> queue = new LinkedList<>();
        queue.add(block);
        Hash h;
        while (!queue.isEmpty()) {
            h = queue.pop();
            for (Hash e : graph.get(h)) {
                if (graph.containsKey(e) && !ret.contains(e) && !ifCovered(e, parent, covered)) {
                    queue.add(e);
                }
            }
            ret.add(h);
        }
        return new ArrayList<Hash>(ret);
    }

    private boolean ifCovered(Hash block, Hash ancestor, Set<Hash> covered) {
        if (revGraph.get(block) == null) {
            return false;
        }

        if(block.equals(ancestor)) {
            return true;
        }

        Set<Hash> visisted = new HashSet<>();
        LinkedList<Hash> queue = new LinkedList<>();
        queue.add(block);
        visisted.add(block);

        Hash h;
        while (!queue.isEmpty()) {
            h = queue.pop();
            for (Hash e : revGraph.get(h)) {
                if(e.equals(ancestor)) {
                    return true;
                } else {
                    if (revGraph.containsKey(e) && !visisted.contains(e) && !covered.contains(e)) {
                        queue.add(e);
                        visisted.add(e);
                    } 
                }
            }
        }
        return false;
    }

    public int getNumOfTips() {
        int ret = 0;
        for(Hash h : graph.keySet()) {
            if(!revGraph.containsKey(h)) {
                ret++;
            }
        }
        return ret;
    }
}

