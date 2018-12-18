package com.iota.iri.service.tipselection.impl;
 
  import com.iota.iri.MilestoneTracker;
 import com.iota.iri.controllers.MilestoneViewModel;
 import com.iota.iri.model.Hash;
 import com.iota.iri.service.tipselection.EntryPointSelector;
 import com.iota.iri.storage.Tangle;
 import com.iota.iri.storage.Indexable;
 import com.iota.iri.storage.Persistable;
 import com.iota.iri.controllers.TransactionViewModel;
 import com.iota.iri.model.persistables.Transaction;
 import com.iota.iri.model.TransactionHash;
 import com.iota.iri.utils.Pair;
 import com.iota.iri.controllers.ApproveeViewModel;
 import org.apache.commons.collections4.CollectionUtils;
 import java.util.*;
 import java.io.PrintStream;
 
  
  /**
  * This is different from the traditional entry point selector, because it ignores the milestone.
  */
 public class EntryPointSelectorKatz implements EntryPointSelector {
 
      private final Tangle tangle;
 
      public HashMap<Hash, Double> score;
     HashMap<Hash, Set<Hash>> graph;
 
      public EntryPointSelectorKatz(Tangle tangle) {
         this.tangle = tangle;
     }
 
      @Override
     public Hash getEntryPoint(int depth) throws Exception {
         graph = buildGraph();
         try
         {
             KatzCentrality centrality = new KatzCentrality(graph, 0.5);
             score = centrality.compute();
         } catch(Exception e)
         {
             e.printStackTrace(new PrintStream(System.out));
         }
         Hash ret = getThelargestScoreHash();
         return ret;
     }
 
      private Hash getThelargestScoreHash()
     {
         Hash ret = null;
         double s = 0.0;
         for (Hash h : graph.keySet())
         {
             if(graph.get(h) != null && graph.get(h).size()>0)
             {
                 if(score.get(h) > s)
                 {
                     s = score.get(h);
                     ret = h;
                 }
             }
         }
         return ret;
     }
 
      // Get the graph using the BFS method
     private HashMap<Hash, Set<Hash>> buildGraph()
     {
         HashMap<Hash, Set<Hash>> ret = new HashMap<Hash, Set<Hash>>();
         try
         {
             Pair<Indexable, Persistable> one = tangle.getFirst(Transaction.class, TransactionHash.class);
             while(one != null)
             {
                 TransactionViewModel model = new TransactionViewModel((Transaction)one.hi, (TransactionHash)one.low);
                 Hash trunk = model.getTrunkTransactionHash();
                 Hash branch = model.getBranchTransactionHash();
                 if(ret.get(model.getHash()) == null)
                 {
                     ret.put(model.getHash(), new HashSet<Hash>());
                 }
                 ret.get(model.getHash()).add(trunk);
                 ret.get(model.getHash()).add(branch);
 
                  one = tangle.next(Transaction.class, one.low);
             } 
         }
         catch(Exception e) 
         {
             e.printStackTrace(new PrintStream(System.out));
         }
         return ret;
     }
 }
