package com.iota.iri.storage.neo4j;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
//import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;

import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Pair;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.MultipleFoundException;
import com.iota.iri.model.*;
import com.iota.iri.model.persistables.Address;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.model.persistables.ObsoleteTag;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.model.persistables.Transaction;

import com.iota.iri.utils.Converter;



import java.util.*;
import java.io.*;

import static org.neo4j.driver.v1.Values.parameters;

public class Neo4jPersistenceProvider implements AutoCloseable, PersistenceProvider
{
    GraphDatabaseService graphDb;
    private boolean available;
    private Map<Class<?>, Label> classLabelMap;

    public Neo4jPersistenceProvider( String dbDir) {
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( new File(dbDir));
    }

    @Override
    public void close() throws Exception {
        graphDb.shutdown();
    }

    public void init() throws Exception {
        initClassLabelMap();
    }

    private void initClassLabelMap() {
        Map<Class<?>, Label> classMap = new LinkedHashMap<>();
        classMap.put(com.iota.iri.model.persistables.Transaction.class, DynamicLabel.label("Transaction"));
        classMap.put(Milestone.class,   DynamicLabel.label("Milestone"));
        classMap.put(StateDiff.class,   DynamicLabel.label("StateDiff"));
        classMap.put(Address.class,     DynamicLabel.label("Address"));
        classMap.put(Approvee.class,    DynamicLabel.label("Approvee"));
        classMap.put(Bundle.class,      DynamicLabel.label("Bundle"));
        classMap.put(ObsoleteTag.class, DynamicLabel.label("ObsoleteTag"));
        classMap.put(Tag.class,         DynamicLabel.label("Tag"));
        classLabelMap = classMap;
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
        try ( org.neo4j.graphdb.Transaction tx = graphDb.beginTx() ) {
            String keyStr = new String(index.bytes());
            String valStr = new String(model.bytes());
            Label label = classLabelMap.get(model.getClass());

            try {
                Node node = graphDb.createNode();
                node.addLabel(label);
                node.setProperty("key", keyStr);
                node.setProperty("val", valStr);
            } catch(Exception e) {
                // skip saving as for now
            }

            tx.success();
        } 
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
        try ( org.neo4j.graphdb.Transaction tx = graphDb.beginTx() )
        {
            // find hash node first
            Node newNode = graphDb.createNode();
            for (Pair<Indexable, Persistable> entry : models) {
                if(entry.hi.getClass().equals(com.iota.iri.model.persistables.Transaction.class)) {
                    Indexable key = entry.low;
                    Persistable value = entry.hi;
                    Label label = classLabelMap.get(value.getClass());
                    newNode.addLabel(label);
                    String keyStr = Converter.trytes(((Hash)key).trits());
                    newNode.setProperty("key", keyStr);
                    break;
                }
            }
            // create the rest
            for (Pair<Indexable, Persistable> entry : models) {
                if(entry.hi.getClass().equals(Approvee.class)) {
                    Indexable key = entry.low;
                    String keyStr = Converter.trytes(((Hash)key).trits());
                    try {
                        Node prev = graphDb.findNode(DynamicLabel.label("Transaction"), "key", keyStr);
                        newNode.createRelationshipTo(prev, DynamicRelationshipType.withName( "APPROVES"));
                    } catch(MultipleFoundException e) {
                        ; // TODO should triage problem here
                    } catch(IllegalArgumentException e) {
                        ; // TODO How to handle null relationship?
                    }
                }
            }
            tx.success();
        }
        
        return true;
    }

    /**
     * Atomically delete all {@code models}.
     * @param models key value pairs that to be expunged from the db
     * @throws Exception
     */
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
        Label label = classLabelMap.get(com.iota.iri.model.persistables.Transaction.class);
        try ( org.neo4j.graphdb.Transaction tx = graphDb.beginTx() )
        {
            ResourceIterator<Node> iter = graphDb.findNodes(label);
            while(iter.hasNext()) {
                iter.next();
                ret++;
            }
            tx.success();
        }
        
        return ret;
    }
} 
