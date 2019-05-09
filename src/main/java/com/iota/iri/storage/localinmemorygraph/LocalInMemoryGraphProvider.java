package com.iota.iri.storage.localinmemorygraph;

import com.google.gson.Gson;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.io.*;

public class LocalInMemoryGraphProvider implements AutoCloseable, PersistenceProvider {
    private static final Logger log = LoggerFactory.getLogger(LocalInMemoryGraphProvider.class);
    private Map<Hash, Double> score;
    private Map<Hash, Double> parentScore;
    private Map<Hash, Set<Hash>> graph;
    private Map<Hash, Hash> parentGraph;
    private Map<Hash, Set<Hash>> revGraph;
    private Map<Hash, Set<Hash>> parentRevGraph;
    private Map<Hash, Integer> degs;
    private HashMap<Integer, Set<Hash>> topOrder;
    private HashMap<Integer, Set<Hash>> topOrderStreaming;

    private Map<Hash, Integer> lvlMap;
    private HashMap<Hash, String> nameMap;
    private Map<Hash, Pair<Hash,Integer>> bundleMap;
    private Map<Hash, Set<Hash>> bundleContent;
    private int totalDepth;
    private Tangle tangle;
    // to use
    private List<Hash> pivotChain;

    boolean freshScore;
    List<Hash> cachedTotalOrder;

    private boolean available;

    private Stack<Hash> ancestors;
    private Double ancestorCreateFrequency = BaseIotaConfig.getInstance().getAncestorCreateFrequency();
    private ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    private ReentrantReadWriteLock graphLock = new ReentrantReadWriteLock();

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
        score = new ConcurrentHashMap<>();
        parentScore = new ConcurrentHashMap<>();
        totalDepth = 0;
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
        bundleMap.clear();
        bundleContent.clear();
    }

    public void init() throws Exception {
        try {
            buildGraph();
            if (BaseIotaConfig.getInstance().isAncestorForwardEnable()) {
                loadAncestorGraph();
                service.scheduleAtFixedRate(new AncestorEngine(), 10, 30, TimeUnit.SECONDS);
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (Throwable e){
            e.printStackTrace();
        }
    }

    private void loadAncestorGraph() {
        Stack<Hash> ancestors = tangle.getAncestors();
        if (null == ancestors || CollectionUtils.isEmpty(ancestors)) {
            return;
        }
        Hash ancestor = ancestors.peek();
        if (graph != null && !graph.isEmpty()) {
            induceGraphFromAncestor(ancestor);
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
                graphLock.writeLock().lock();
                try {
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
                }finally {
                    graphLock.writeLock().unlock();
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
            buildPivotChain();
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
        graphLock.readLock().lock();
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
        } finally {
            graphLock.readLock().unlock();
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
    public String printGraph(Map<Hash, Set<Hash>> graph, String type) {
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
    void printRevGraph(Map<Hash, Set<Hash>> revGraph) {
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
        graphLock.readLock().lock();
        try {
            if (start == null || !graph.keySet().contains(start)) {
                return Collections.emptyList();
            }
            ArrayList<Hash> list = new ArrayList<>();
            list.add(start);
            while (!CollectionUtils.isEmpty(parentRevGraph.get(start))) {
                Hash s = getMax(start);
                if (s == null) {
                    return list;
                }
                start = s;
                list.add(s);
            }
            return list;
        }finally {
            graphLock.readLock().unlock();
        }
    }

    public Hash getPivot(Hash start) {
        graphLock.readLock().lock();
        try {
            if (start == null || !graph.keySet().contains(start)) {
                return null;
            }
            while (!CollectionUtils.isEmpty(parentRevGraph.get(start))) {
                Hash s = getMax(start);
                if (s == null) {
                    return start;
                }
                start = s;
            }
            return start;
        }finally {
            graphLock.readLock().unlock();
        }
    }

    public Hash getGenesis() {
        try {
            if (ancestors != null && !ancestors.empty()) {
                return ancestors.peek();
            }
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

    @Override
    public Stack<Hash> getAncestors() {
        return ancestors;
    }

     @Override
    public void storeAncestors(Stack<Hash> ancestors) {
        this.ancestors = ancestors;
    }

    public double getScore(Hash hash) {
        graphLock.readLock().lock();
        try {
            return score.get(hash);
        }finally {
            graphLock.readLock().unlock();
        }
    }

    public double getParentScore(Hash h) {
        return parentScore.get(h);
    }

    public boolean containsKeyInGraph(Hash hash) {
        return graph.containsKey(hash);
    }

    public Map<Hash, Set<Hash>> getGraph() {
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

    public Map<Hash, Set<Hash>> getRevParentGraph() {
        return this.parentRevGraph;
    }

    public void induceGraphFromAncestor(Hash curAncestor) {
        Map<Hash, Set<Hash>> subGraph = new HashMap<>();
        Map<Hash, Set<Hash>> subRevGraph = new HashMap<>();
        Map<Hash, Hash> subParentGraph = new HashMap<>();
        Map<Hash, Set<Hash>> subParentRevGraph = new HashMap<>();
        degs.clear();

        LinkedList<Hash> queue = new LinkedList<>();
        Set<Hash> visited = new HashSet<Hash>();
        queue.add(curAncestor);
        visited.add(curAncestor);

        while (!queue.isEmpty()) {
            Hash h = queue.poll();
            Set<Hash> children = revGraph.get(h);
            if (null != children) {
                subRevGraph.putIfAbsent(h, children);
                for (Hash keyOfGraph : children){
                    Set<Hash> valueOfGraph = subGraph.get(keyOfGraph);
                    if(null == valueOfGraph){
                        valueOfGraph = new HashSet<>();
                        subGraph.putIfAbsent(keyOfGraph, valueOfGraph);
                    }
                    valueOfGraph.add(h);

                    Hash parentNode = parentGraph.get(keyOfGraph);
                    if (null != parentNode) {
                        subParentGraph.putIfAbsent(keyOfGraph, parentNode);
                    }

                    if (!visited.contains(keyOfGraph)){
                        queue.add(keyOfGraph);
                        visited.add(keyOfGraph);
                    }
                }
            }

            Set<Hash> subSet = parentRevGraph.get(h);
            if (null != subSet) {
                subParentRevGraph.putIfAbsent(h, subSet);
            }

            if (degs.get(h) == null){
                degs.putIfAbsent(h, 2);
            }
        }

        subGraph.putIfAbsent(curAncestor, graph.get(curAncestor));	
        for (Hash h : graph.get(curAncestor)){	
            Set<Hash> set = new HashSet<>();	
            set.add(curAncestor);	
            subRevGraph.putIfAbsent(h, set);	
            degs.putIfAbsent(h, 0);	
        }	
        subParentGraph.putIfAbsent(curAncestor, parentGraph.get(curAncestor));	
        subParentRevGraph.putIfAbsent(subParentGraph.get(curAncestor),new HashSet(){{add(curAncestor);}});

        graphLock.writeLock().lock();
        try {
            graph.clear();
            revGraph.clear();
            parentGraph.clear();
            parentRevGraph.clear();

            graph = new ConcurrentHashMap<>(subGraph);
            revGraph = new ConcurrentHashMap<>(subRevGraph);
            parentGraph = new ConcurrentHashMap<>(subParentGraph);
            parentRevGraph = new ConcurrentHashMap<>(subParentRevGraph);

            topOrder.clear();
            computeToplogicalOrder();
            computeScore();
        }finally {
            graphLock.writeLock().unlock();
        }
        buildPivotChain();
    }

    class AncestorEngine implements Runnable {
        @Override
        public void run() {
            try {
                refreshGraph();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        // get a new ancestor:
        // 1) it's in the pivotal chain
        // 2) the pScore of this node is larger than maxScore of side chain + ancestorCreateFrequency
        public Hash getAncestor() {
            graphLock.readLock().lock();
            buildPivotChain();
            try {
                if (null == parentGraph) {
                    return null;
                }
                Iterator<Hash> iterator = parentGraph.keySet().iterator();
                Double maxSideChainScore = -1d;
                while (iterator.hasNext()) {
                    Hash node = iterator.next();
                    if (pivotChain.contains(node)) { // complexity high
                        continue;
                    }
                    Double score = parentScore.get(node);
                    if (null == score) {
                        throw new NullPointerException("key：" + node);
                    }
                    if (score > maxSideChainScore) {
                        maxSideChainScore = score;
                    }
                }
                log.debug("Max side chain score is: {}", maxSideChainScore);
                log.debug("pivotal chain is: {}", pivotChain);

                for (int i = pivotChain.size()-1; i>=0; i--) {
                    Hash mainChainNode = pivotChain.get(i);
                    double mainChainNodeScore = parentScore.get(mainChainNode);
                    log.debug("node {} pscore {}", mainChainNode, mainChainNodeScore);
                    if (mainChainNodeScore > (maxSideChainScore + new Double(ancestorCreateFrequency))) {
                        return mainChainNode;
                    }
                }
            }finally {
                graphLock.readLock().unlock();
            }
            return null;
        }

        void refreshGraph() {
           log.debug("=========begin to refresh ancestor node==========");
            long begin = System.currentTimeMillis();
            Stack<Hash> ancestors = tangle.getAncestors();
            Hash curAncestor = getAncestor();
            if (curAncestor == null) {
                curAncestor = getGenesis();
            }
            
            if (curAncestor == null) {
                log.debug("=========no new ancestor node,cost:" + (System.currentTimeMillis() - begin) + "ms ==========");
                return;
            }
            if (CollectionUtils.isNotEmpty(ancestors) && ancestors.peek().equals(curAncestor)) {
                log.debug("=========no new ancestor node to reload,cost:" + (System.currentTimeMillis() - begin) + "ms ==========");
                return;
            }

            printAllGraph("before_" + ancestors.size(), curAncestor);
            ancestors = appendNewAncestor(ancestors, curAncestor);
            tangle.storeAncestors(ancestors);
            induceGraphFromAncestor(curAncestor);
        }

        private void printAllGraph(String tag, Hash ancestor) {
            String ret = printGraph(graph, "DOT");
            String ret1 = printGraph(parentRevGraph, "DOT");
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter("graph_" + tag + ".dot"));
                BufferedWriter writer1 = new BufferedWriter(new FileWriter("rev_" + tag + ".dot"));
                writer1.write(ret1);
                writer.write(ret);
                writer.close();
                writer1.close();
            } catch(Exception e) {

            }
        }

        private Stack<Hash> appendNewAncestor(Stack<Hash> ancestors, Hash curAncestor) {
            if (ancestors == null) {
                ancestors = new Stack<>();
            }
            ancestors.push(curAncestor);
            return ancestors;
        }
    }

    public boolean hasBlock(Hash h) {
        return graph.containsKey(h) || revGraph.containsKey(h);
    }
}

