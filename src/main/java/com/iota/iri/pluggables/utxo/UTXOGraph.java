package com.iota.iri.pluggables.utxo;

import java.util.*;
import java.io.*;

import com.iota.iri.utils.IotaUtils;

import com.iota.iri.model.Hash;

import static java.util.stream.Collectors.*;
import static java.util.Map.Entry.*;

public class UTXOGraph {

    class TxnBlock {
        public int pos;
        public int idx;
        public String hash;

        TxnBlock(int pos, int idx, String hash) {
            this.pos = pos;
            this.idx = idx;
            this.hash = hash;
        }

        public String toString() {
            return "[" + pos + "," + idx + "," + hash + "]";
        }
    }

    public Map<String, Set<String>> outGraph;
    public Map<String, Set<String>> inGraph;
    public Set<String> doubleSpendSet;
    public Map<String, Set<TxnBlock>> accountMap;

    public Set<Integer> findUnspentTxnsForAccount(String account) {
        Set<Integer> ret = new HashSet<>();
        Set<TxnBlock> all = accountMap.get(account);
        if(all != null) {
            for(TxnBlock block : all) {
                String key = block.hash + ":" + String.valueOf(block.idx) + "," + account;
                if(!isSpent(key)) {
                    ret.add(block.pos);
                }
            }
        }
        return ret;
    }

    public void addTxn(Txn newTx, int idx) {
        if(newTx.inputs == null) {
            return;
        }

        for(TxnIn in : newTx.inputs) {
            String key = in.txnHash + ":" + String.valueOf(in.idx) +","+ in.userAccount;
            if(outGraph.get(key) == null) {
                outGraph.put(key, new HashSet<String>());
            }
            
            for(int i=0; i<newTx.outputs.size(); i++) {
                String val = newTx.txnHash + ":" + String.valueOf(i) + "," + newTx.outputs.get(i).userAccount;
                Set<String> outs = outGraph.get(key);
                outs.add(val);
                outGraph.put(key, outs);

                if(!inGraph.containsKey(val)) {
                    inGraph.put(val, new HashSet<>());
                }
                Set<String> ins = inGraph.get(val);
                ins.add(key);
                inGraph.put(val, ins);

                if(accountMap.get(newTx.outputs.get(i).userAccount)==null) {
                    accountMap.put(newTx.outputs.get(i).userAccount, new HashSet<>());
                }
                Set<TxnBlock> st = accountMap.get(newTx.outputs.get(i).userAccount);
                st.add(new TxnBlock(idx, i, newTx.txnHash));
                accountMap.put(newTx.outputs.get(i).userAccount, st);
            }
        }
    }

    public UTXOGraph(List<Txn> txns) {
        outGraph = new HashMap<String, Set<String>>();
        inGraph = new HashMap<String, Set<String>>();
        doubleSpendSet = new HashSet<>();
        accountMap = new HashMap<>();

        for(int i=0; i<txns.size(); i++) {
            addTxn(txns.get(i), i);
        }
    }

    public UTXOGraph() {
        outGraph = new HashMap<String, Set<String>>();
        inGraph = new HashMap<String, Set<String>>();
        doubleSpendSet = new HashSet<>();
        accountMap = new HashMap<>();
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
        LinkedList<String> queue = new LinkedList<>();
        queue.add(key);

        Set<String> visited = new HashSet<>();

        while(!queue.isEmpty()) {
            String h = queue.pop();
            String[] k = key.split(":");
            if(doubleSpendSet.contains(k[0])) {
                return true;
            }

            Set<String> ups = inGraph.get(h);
            if(ups != null) {
                for(String up : ups) {
                    if(!visited.contains(up)) {
                        queue.add(up);
                        visited.add(up);
                    }
                }
            }
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
