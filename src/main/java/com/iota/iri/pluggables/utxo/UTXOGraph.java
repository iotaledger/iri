package com.iota.iri.pluggables.utxo;

import java.util.*;
import java.io.*;

import com.iota.iri.utils.IotaUtils;

import com.iota.iri.model.Hash;

import static java.util.stream.Collectors.*;
import static java.util.Map.Entry.*;

public class UTXOGraph {

    public Map<String, Set<String>> outGraph;
    public Map<String, String> inGraph; //FIXME, UTXO graph should be a graph instead of a tree
    public Set<String> doubleSpendSet;

    public UTXOGraph(List<Txn> txns) {

        outGraph = new HashMap<String, Set<String>>();
        inGraph = new HashMap<String, String>();
        doubleSpendSet = new HashSet<>();

        for(Txn txn : txns) {
            if(txn.inputs == null) {
                continue;
            }

            for(TxnIn in : txn.inputs) {
                String key = in.txnHash + ":" + String.valueOf(in.idx) +","+ in.userAccount;
                if(outGraph.get(key) == null) {
                    outGraph.put(key, new HashSet<String>());
                }
                
                for(int i=0; i<txn.outputs.size(); i++) {
                    String val = txn.txnHash + ":" + String.valueOf(i) + "," + txn.outputs.get(i).userAccount;
                    Set<String> outs = outGraph.get(key);
                    outs.add(val);
                    outGraph.put(key, outs);

                    inGraph.put(val, key);
                }
            }
        }
    }

    public void markDoubleSpend(List<Hash> order, HashMap<String, Hash> txnToTangleMap) {
        for(String key : outGraph.keySet()) {
            Set<String> valSet = new HashSet<>();
            for(String val : outGraph.get(key)) {
                valSet.add(val.split(":")[0]);
            }
            if(valSet.size() > 1) {
                markTheLaterAsDoubleSpend(order, txnToTangleMap, valSet);
            }
        }
    }

    public void markTheLaterAsDoubleSpend(List<Hash> order, HashMap<String, Hash> txnToTangleMap, Set<String> valSet) {
        Map<String, Integer> toSort = new HashMap<>(); 
        for(String out : valSet) {
            Hash h = txnToTangleMap.get(out);
            int pos = order.indexOf(h);
            if(pos == -1) {
                toSort.put(out, 100000);
            } else {
                toSort.put(out, pos);
            }
        }

        Map<String, Integer> sorted = toSort
        .entrySet()
        .stream()
        .sorted(comparingByValue())
        .collect(
            toMap(e -> e.getKey(), e -> e.getValue(), (e1, e2) -> e2,
                LinkedHashMap::new));
        
        int i = 0;
        for(String key : sorted.keySet()) {    
            if(i>0) {
                doubleSpendSet.add(key);
            }
            i++;
        }
    }

    public Boolean isSpent(String key) {
        return outGraph.containsKey(key);
    }

    public Boolean isDoubleSpend(String key) {

        while(inGraph.containsKey(key)) {
            String[] k = key.split(":");
            if(doubleSpendSet.contains(k[0])) {
                return true;
            }
            key = inGraph.get(key);
        }
        return false;
    }

    //FIXME for debug :: for graphviz visualization
    public void printGraph(Map<String, Set<String>> graph, String k, Set<String> spend) {
        try {
            BufferedWriter writer = null;
            if(k != null) {
                writer = new BufferedWriter(new FileWriter(k));
                writer.write("digraph G {\n");
            }
            for (String key : graph.keySet()) {
                for (String val : graph.get(key)) {
                    if(k != null) {
                        writer.write("\"" + IotaUtils.abbrieviateHash(key, 6) + "\"->" +
                                "\"" + IotaUtils.abbrieviateHash(val, 6) + "\"\n");
                        if(doubleSpendSet.contains((val.split(":")[0]))) {
                            writer.write("\""+IotaUtils.abbrieviateHash(val, 6)+"\"[" + "style=filled, fillcolor=red]\n");
                        } else if (!isSpent(val)) {
                            writer.write("\""+IotaUtils.abbrieviateHash(val, 6)+"\"[" + "style=filled, fillcolor=green]\n");
                            if(spend.contains(val)) {
                                writer.write("\""+IotaUtils.abbrieviateHash(val, 6)+"\"[" + "shape=square]\n");
                            }
                        } 
                    } else {
                        System.out.println("\"" + IotaUtils.abbrieviateHash(key, 6) + "\"->" +
                                "\"" + IotaUtils.abbrieviateHash(val, 6) + "\"");
                        }
                        //System.out.println("come here to check double spend");
                        if(doubleSpendSet.contains((val.split(":")[0]))) {
                            System.out.println("\""+IotaUtils.abbrieviateHash(val, 6)+"\"[" + "style=filled, fillcolor=red]");
                        } else if (!isSpent(val)) {
                            System.out.println("\""+IotaUtils.abbrieviateHash(val, 6)+"\"[" + "style=filled, fillcolor=green]");
                        } 
                }
            }
            if(k != null) {
                writer.write("}\n");
                writer.close();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
