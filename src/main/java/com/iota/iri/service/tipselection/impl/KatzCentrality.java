package com.iota.iri.service.tipselection.impl;

import com.iota.iri.validator.MilestoneTracker;
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

public class KatzCentrality
{
    public static final double DEFAULT_ALPHA = 1.0;
    public static final double DEFAULT_BETA  = 1.0;
    public static int MAX_ITERATIONS         = 100;
    public static double EPSILON             = 1e-6;
    private double alpha;
    private double beta;

    HashMap<Hash, Set<Hash>> network;
    HashMap<Hash, Set<Hash>> revnetwork;
    Set<Hash>                all_vertices;
    HashMap<Hash, Double>    score;

    public KatzCentrality (HashMap<Hash, Set<Hash>> network)
    {
        this(network, null, DEFAULT_ALPHA, DEFAULT_BETA);
    }

    public KatzCentrality (HashMap<Hash, Set<Hash>> network, HashMap<Hash, Set<Hash>> revNetwork, double alpha)
    {
        this(network, revNetwork, alpha,DEFAULT_BETA);
    }

    public KatzCentrality (HashMap<Hash, Set<Hash>> network, double alpha)
    {
        this(network, null, alpha,DEFAULT_BETA);
    }

    public KatzCentrality (HashMap<Hash, Set<Hash>> network, HashMap<Hash, Set<Hash>> revNetwork, double alpha, double beta)
    {
        this.network = network;

        this.alpha = alpha;
        this.beta = beta;
        all_vertices = new HashSet<Hash>();
        for(Hash v : this.network.keySet())
        {
            all_vertices.add(v);
            for(Hash u : this.network.get(v))
            {
                if(network.get(u) != null) {
                    all_vertices.add(u);
                }
            }
        }
        if(revNetwork == null) {
            initRevNetwork();
        } else {
            this.revnetwork = revNetwork;
        }
    }

    public void setScore(HashMap<Hash, Double> score) {
        this.score = score;
    }

    void initRevNetwork()
    {
        revnetwork = new HashMap<Hash, Set<Hash>>();
        for(Hash v : network.keySet())
        {
            for(Hash u : network.get(v))
            {
                if(revnetwork.get(u) == null)
                {
                    revnetwork.put(u, new HashSet<Hash>());
                }
                revnetwork.get(u).add(v);
            }
        }
    }

    public double getAlpha ()
    {
        return alpha;
    }

    public double getBeta ()
    {
        return beta;
    }

    public String getName() 
    {
        return "katz("+alpha+","+beta+")";
    }

    public HashMap<Hash, Double> compute()
    {
        int size = all_vertices.size();
        HashMap<Hash, Double> centrality = new HashMap<Hash, Double>();
        HashMap<Hash, Double> old = new HashMap<Hash, Double>();
        HashMap<Hash, Double> tmp = new HashMap<Hash, Double>();
        double change;
        double sum2;
        double norm;
        int iteration;

        if(score == null) {
            for (Hash v : all_vertices) {
                centrality.put(v, 1.0/size);
                old.put(v, 1.0/size);
            }
        } else {
            for(Hash v : score.keySet()) {
                centrality.put(v, score.get(v));
                old.put(v, score.get(v));
            }
        }
        
        // Power iteration: O(k(n+m))
        // The value of norm converges to the dominant eigenvalue, and the vector 'centrality' to an associated eigenvector
        // ref. http://en.wikipedia.org/wiki/Power_iteration
        change = Double.MAX_VALUE;
        for (iteration=0; (iteration<MAX_ITERATIONS) && (change>EPSILON); iteration++) {
            tmp = old;
            old = centrality;
            centrality = tmp;
            sum2 = 0;		
            for (Hash v : all_vertices) {
                centrality.put(v, 0.0);
                if(revnetwork.get(v) == null)
                {
                    continue;
                }
                for (Hash u : revnetwork.get(v)) {
                    centrality.put(v, centrality.get(v)+old.get(u));
                }
                centrality.put(v, alpha*centrality.get(v)+beta/size);
                sum2 += centrality.get(v)*centrality.get(v);
            }
            // Normalization	
            norm = Math.sqrt(sum2);
            change = 0;	
            for (Hash v : all_vertices)
            {
                centrality.put(v, centrality.get(v)/norm);
                if ( Math.abs(centrality.get(v)-old.get(v)) > change )
                {
                    change = Math.abs(centrality.get(v)-old.get(v));
                }
            }
        }
        return centrality;
    }
}
