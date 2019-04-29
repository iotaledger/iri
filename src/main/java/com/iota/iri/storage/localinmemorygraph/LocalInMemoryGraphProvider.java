package com.iota.iri.storage.localinmemorygraph;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.tipselection.impl.CumWeightScore;
import com.iota.iri.service.tipselection.impl.KatzCentrality;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.IotaUtils;
import com.iota.iri.utils.Pair;
import org.apache.commons.collections4.CollectionUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import java.io.*;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.utils.*;

public class LocalInMemoryGraphProvider implements AutoCloseable, PersistenceProvider {
    private static final Logger log = LoggerFactory.getLogger(LocalInMemoryGraphProvider.class);
    private HashMap<Hash, Double> score;
    private HashMap<Hash, Double> parentScore;
    private HashMap<Hash, Set<Hash>> graph;
    private Map<Hash, Hash> parentGraph;
    private HashMap<Hash, Set<Hash>> revGraph;
    private HashMap<Hash, Set<Hash>> parentRevGraph;
    private HashMap<Hash, Integer> degs;
    private HashMap<Integer, Set<Hash>> topOrder;
    private HashMap<Integer, Set<Hash>> topOrderStreaming;

    private HashMap<Hash, Integer> lvlMap;
    private HashMap<Hash, String> nameMap;
    private Map<Hash, Pair<Hash,Integer>> bundleMap;
    private Map<Hash, Set<Hash>> bundleContent;
    private int totalDepth;
    private Tangle tangle;
    // to use
    private List<Hash> pivotChain;

    // 未能回溯到genesis的节点
    Queue<Hash> unTracedNodes;
    // 保存可回溯的节点，最坏的情况是保存了整个graph，空间换时间。
    Set<Hash> tracedNodes;

    Queue<Hash> parentUnTracedNodes;
    Set<Hash> parentTracedNodes;
    boolean freshScore;
    List<Hash> cachedTotalOrder;

    private boolean available;

    public LocalInMemoryGraphProvider(String dbDir, Tangle tangle) {
        this.tangle = tangle;
        graph = new HashMap<>();
        revGraph = new HashMap<>();
        parentGraph = new ConcurrentHashMap<>();
        bundleMap = new ConcurrentHashMap<>();
        bundleContent = new ConcurrentHashMap<>();
        parentRevGraph = new HashMap<>();
        degs = new HashMap<>();
        topOrder = new HashMap<>();
        lvlMap = new HashMap<>();
        topOrderStreaming = new HashMap<>();
        score = new HashMap<>();
        parentScore = new HashMap<>();
        totalDepth = 0;
        unTracedNodes = new ConcurrentLinkedDeque<>();
        tracedNodes = ConcurrentHashMap.newKeySet();
        parentUnTracedNodes = new ConcurrentLinkedDeque<>();
        parentTracedNodes = ConcurrentHashMap.newKeySet();
        freshScore = false;
    }

    //FIXME for debug
    public void setNameMap(HashMap<Hash, String> nameMap) {
        this.nameMap = nameMap;
    }

    @Override
    public void close() throws Exception {
        graph.clear();
        revGraph.clear();
        parentGraph.clear();
        parentRevGraph.clear();
        degs.clear();
        topOrder.clear();
        totalDepth = 0;
        topOrderStreaming.clear();
        lvlMap.clear();
        unTracedNodes.clear();
        tracedNodes.clear();
        parentUnTracedNodes.clear();
        parentTracedNodes.clear();
        bundleMap.clear();
        bundleContent.clear();
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

                if(model.getLastIndex() + 1 > 1) {   
                    bundleMap.put(key, new Pair<Hash, Integer>(model.getBundleHash(), (int)model.getCurrentIndex()));
                    if(!bundleContent.containsKey(model.getBundleHash())) {
                        bundleContent.put(model.getBundleHash(), new HashSet<>());
                    }
                    Set<Hash> content = bundleContent.get(model.getBundleHash());
                    content.add(key);
                    bundleContent.put(model.getBundleHash(), content);
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
                    parentScore = CumWeightScore.updateParentScore(parentGraph, parentScore, vet);
                    freshScore = false;
                } else if (BaseIotaConfig.getInstance().getConfluxScoreAlgo().equals("KATZ")) {
                    score.put(vet, 1.0 / (score.size() + 1));
                    KatzCentrality centrality = new KatzCentrality(graph, revGraph, 0.5);
                    centrality.setScore(score);
                    score = centrality.compute();
                    parentScore = CumWeightScore.updateParentScore(parentGraph, parentScore, vet);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    private void doUpdateScore(Hash h){
        Hash genesis = getGenesis();
        if (null == genesis){
            return;
        }
        if (!tracedNodes.contains(genesis)){
            tracedNodes.add(genesis);
            tracedNodes.addAll(graph.get(genesis));
        }
        // 判断是否可回溯
        if (!traceToGenesis(h)){
            unTracedNodes.offer(h);
            return;
        }

        tracedNodes.add(h);
        score = CumWeightScore.update(graph, score, h);
        checkUntracedNodes();
    }

    private void rebuildParentScore(Hash h){
        Hash genesis = getGenesis();
        if (null == genesis){
            return;
        }
        if (!parentTracedNodes.contains(genesis)){
            parentTracedNodes.add(genesis);
            parentTracedNodes.add(parentGraph.get(genesis));
        }

        if (!parentTraceToGenesis(h)){
            parentUnTracedNodes.offer(h);
            return;
        }

        parentScore = CumWeightScore.updateParentScore(parentGraph, parentScore, h);
        parentTracedNodes.add(h);
        checkUntracedParentNodes();
    }

    private void checkUntracedParentNodes() {
        Queue<Hash> tmpUnTracedQueue = new ArrayDeque<>();
        for(Hash h : parentUnTracedNodes) {
            if (parentTraceToGenesis(h)) {
                parentScore = CumWeightScore.updateParentScore(parentGraph, parentScore, h);
            } else {
                tmpUnTracedQueue.add(h);
            }
        }
        parentUnTracedNodes = tmpUnTracedQueue;
    }

    private boolean parentTraceToGenesis(Hash h) {
        // 遍历，前驱节点是已遍历节点或genesis，返回true
        Queue<Hash> confirmNodes = new ArrayDeque<>();
        Set<Hash> visited = new HashSet<>();
        confirmNodes.add(h);
        while (!confirmNodes.isEmpty()) {
            Hash cur = confirmNodes.poll();
            Hash confirmed = parentGraph.get(cur);
            if (null == confirmed){
                continue;
            }
            if (!parentTracedNodes.contains(confirmed)){
                return false;
            }
            if (!visited.contains(confirmed)) {
                confirmNodes.add(confirmed);
            }
            visited.add(confirmed);
        }
        parentTracedNodes.add(h);
        return true;
    }


    private void checkUntracedNodes() {
        if (unTracedNodes.isEmpty()){
            return;
        }
        // 假设unTracedNodes也是乱序的，确保节点能够在其依赖节点计算完毕后得到计算机会
       Queue<Hash> tmpQueue = new ArrayDeque<>();
        Set<Hash> visited = new HashSet<>();
        while (!unTracedNodes.isEmpty()) {
            Hash h = unTracedNodes.poll();
            if (!traceToGenesis(h)) {
                if (!visited.contains(h)) {
                    tmpQueue.offer(h);
                }
            }else {
                score = CumWeightScore.update(graph, score, h);
            }
            visited.add(h);
        }
        unTracedNodes = tmpQueue;
    }

    // 回溯方法，能够回溯到genesis或者已回溯节点都算作回溯成功
    private boolean traceToGenesis(Hash h){
        //遍历，前驱节点是已遍历节点或genesis，返回true
        Queue<Hash> confirmNodes = new ArrayDeque<>();
        Set<Hash> visited = new HashSet<>();
        confirmNodes.add(h);
        while (!confirmNodes.isEmpty()){
            Hash cur = confirmNodes.poll();
            if (visited.contains(cur)){
                continue;
            }else{
                visited.add(cur);
            }

            Set<Hash> confirmed = graph.get(cur);
            if (null == confirmed){
                continue;
            }
            Set<Hash> checkParentEqual = new HashSet<>();
            for (Hash hash : confirmed){
                if (tracedNodes.contains(hash)){
                    checkParentEqual.add(hash);
                }
            }
            // 父节点不都是可回溯节点
            if (checkParentEqual.size() != confirmed.size()){
                return false;
            }
            confirmed.forEach(e -> confirmNodes.add(e));
        }
        tracedNodes.add(h);
        return true;
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
                    if(!freshScore) {
                        score = CumWeightScore.compute(revGraph, graph, getGenesis());
                        parentScore = CumWeightScore.computeParentScore(parentGraph, parentRevGraph);
                        freshScore = true;
                        cachedTotalOrder = confluxOrder(getPivot(getGenesis()));
                    }
                    // FIXME add parent score here
                } else if (BaseIotaConfig.getInstance().getConfluxScoreAlgo().equals("KATZ")) {
                    KatzCentrality centrality = new KatzCentrality(graph, revGraph, 0.5);
                    score = centrality.compute();
                    // FIXME add parent score here
                }
            }
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    public Hash getPivotalHash(int depth) {
        Hash ret = null;
        buildPivotChain();
        if (depth == -1 || depth >= this.pivotChain.size()) {
            Set<Hash> set = topOrderStreaming.get(1);
            if(CollectionUtils.isEmpty(set)){
                return null;
            }
            ret = set.iterator().next();
            return ret;
        }

        // TODO if the same score, choose randomly
        ret = this.pivotChain.get(this.pivotChain.size()-depth-1);
        return ret;
    }

    //FIXME for debug :: for graphviz visualization
    public String printGraph(HashMap<Hash, Set<Hash>> graph, String type) {
        String ret = "";
        try {
            if(type.equals("DOT")) {
                ret += "digraph G{\n";
                for (Hash key : graph.keySet()) {
                    for (Hash val : graph.get(key)) {
                        if (nameMap != null) {
                            ret += "\"" + nameMap.get(key) + "\"->" +
                            "\"" + nameMap.get(val) + "\"\n";
                        } else {
                            ret += "\"" + IotaUtils.abbrieviateHash(key, 6) + ":" + parentScore.get(key) + "\"->" +
                            "\"" + IotaUtils.abbrieviateHash(val, 6) + ":" + parentScore.get(val) + "\"\n";
                        }
                    }
                }
                ret += "}\n";
            } else if (type.equals("JSON")) {
                Gson gson = new Gson();
                HashMap<String, Set<String>> toPrint = new HashMap<String, Set<String>>();
                for(Hash h : graph.keySet()) {
                    for(Hash s : graph.get(h)) {
                        String from = Converter.trytes(h.trits());
                        String to = Converter.trytes(s.trits());
                        if(!toPrint.containsKey(from)) {
                            toPrint.put(from, new HashSet<>());
                        }
                        Set<String> st = toPrint.get(from);
                        st.add(to);
                        toPrint.put(from, st);
                    }
                }
                ret = gson.toJson(toPrint);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        return ret;
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
                if (parentScore.get(h) > maxScore) {
                    maxScore = parentScore.get(h);
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
        if(freshScore) {
            return cachedTotalOrder;
        }
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
                for(Hash s : noBeforeInTmpGraph) {
                    if(!lvlMap.containsKey(s)) {
                        lvlMap.put(s, Integer.MAX_VALUE); //FIXME this is a bug
                    }
                }
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

    private Hash getMax(Hash start) {
        double tmpMaxScore = -1;
        Hash s = null;
        for (Hash block : parentRevGraph.get(start)) {
            if (parentScore.containsKey(block)) {
                if(parentScore.get(block) > tmpMaxScore) {
                    tmpMaxScore = parentScore.get(block);
                    s = block;
                } else if(parentScore.get(block) == tmpMaxScore) {
                    String sStr = Converter.trytes(s.trits());
                    String blockStr = Converter.trytes(block.trits());
                    if(sStr.compareTo(blockStr) < 0) {
                        s = block;
                    }
                }
            }
        }
        return s;
    }

    public List<Hash> pivotChain(Hash start) {
        if (start == null || !graph.keySet().contains(start)) {
            return Collections.emptyList();
        }
        ArrayList<Hash> list = new ArrayList<>();
        list.add(start);
        while (!CollectionUtils.isEmpty(parentRevGraph.get(start))) {
            Hash s = getMax(start);
            if(s == null) {
                return list;
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
            Hash s = getMax(start);
            if(s == null) {
                return start;
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

    public double getScore(Hash hash) {
        return score.get(hash);
    }

    public double getParentScore(Hash h) {
        return parentScore.get(h);
    }

    public boolean containsKeyInGraph(Hash hash) {
        return graph.containsKey(hash);
    }

    public HashMap<Hash, Set<Hash>> getGraph() {
        return this.graph;
    }

    // if belongs to the same bundle, condense it
    public HashMap<Hash, Set<Hash>> getCondensedGraph() {
        HashMap<Hash, Set<Hash>> ret = new HashMap<>();
        for(Hash h : graph.keySet()) {
            if((bundleMap.containsKey(h) && bundleMap.get(h).hi == 0) || 
               !bundleMap.containsKey(h)) {
                Set<Hash> to = new HashSet<>();           
                for(Hash m : graph.get(h)) {
                    if(bundleMap.containsKey(m)) {
                        to.add(bundleMap.get(m).low);
                    } else {
                        to.add(m);
                    }
                }
                if(bundleMap.containsKey(h)) {
                    ret.put(bundleMap.get(h).low, to);
                } else {
                    ret .put(h, to);
                }
            } 
        }
        return ret;
    }

    public List<Hash> getHashesFromBundle(List<String> bundleHashes) {
        List<Hash> ret = new ArrayList<>();
        for(String h : bundleHashes) {
            Hash hh = HashFactory.BUNDLE.create(h);
            if(bundleContent.containsKey(hh)) {
                ret.addAll(bundleContent.get(hh));
            } else {
                ret.add(HashFactory.TRANSACTION.create(h));
            }
        }
        return ret;
    }

    public Map<Hash, Hash> getParentGraph() {
        return this.parentGraph;
    }

    public HashMap<Hash, Set<Hash>> getRevParentGraph() {
        return this.parentRevGraph;
    }

    public boolean hasBlock(Hash h) {
        return graph.containsKey(h);
    }
}

