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

import java.util.*;

import static org.neo4j.driver.v1.Values.parameters;

public class Neo4jPersistenceProvider implements AutoCloseable, PersistenceProvider
{
    private final Driver driver;
    private boolean available;

    public Neo4jPersistenceProvider( String uri, String user, String password )
    {
        driver = GraphDatabase.driver( uri, AuthTokens.basic( user, password ) );
    }

    @Override
    public void close() throws Exception
    {
        driver.close();
    }

    public void init() throws Exception
    {
        //log.info("Initializing Database Backend... ");
    }

    public boolean isAvailable()
    {
        return this.available;
    }

    public void shutdown()
    {
        try {
            close();
        } catch(Exception e) {}
    }

    public boolean save(Persistable model, Indexable index) throws Exception
    {
        return false;
    }

    public void delete(Class<?> model, Indexable  index) throws Exception
    {

    }

    public boolean update(Persistable model, Indexable index, String item) throws Exception
    {
        return false;
    }

    public boolean exists(Class<?> model, Indexable key) throws Exception
    {
        return false;
    }

    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception
    {
        return new Pair<Indexable,Persistable>(new TransactionHash(), new Transaction());
    }

    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass) throws Exception
    {
        return new HashSet<Indexable>();
    }

    public Persistable get(Class<?> model, Indexable index) throws Exception
    {
        return new Transaction();
    }

    public boolean mayExist(Class<?> model, Indexable index) throws Exception
    {
        return false;
    }

    public long count(Class<?> model) throws Exception
    {
        return (long)0;
    }

    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value)
    {
        return new HashSet<Indexable>();
    }

    public Persistable seek(Class<?> model, byte[] key) throws Exception
    {
        return new Transaction();
    }

    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception
    {
        return new Pair<Indexable,Persistable>(new TransactionHash(), new Transaction());
    }

    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception
    {
        return new Pair<Indexable,Persistable>(new TransactionHash(), new Transaction());
    }

    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception
    {
        return new Pair<Indexable,Persistable>(new TransactionHash(), new Transaction());
    }

    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception
    {
        Neo4jNode node = new Neo4jNode(models);

        try ( Session session = driver.session() )
        {
            String greeting = session.writeTransaction( new TransactionWork<String>()
            {
                @Override
                public String execute(org.neo4j.driver.v1.Transaction tx)
                {
                    StatementResult result = tx.run( "CREATE (" + node.txnNodeHash + ":txnNode) " +
                                                     "SET "+ node.txnNodeHash + ".addressHash = $addressHash " +
                                                     "SET "+ node.txnNodeHash + ".bundleHash = $bundleHash " +
                                                     "SET "+ node.txnNodeHash + ".trunkHash = $trunkHash " +
                                                     "SET "+ node.txnNodeHash + ".branchHash = $branchHash " +
                                                     "SET "+ node.txnNodeHash + ".obsoleteTagHash = $obsoleteTagHash " +
                                                     "SET "+ node.txnNodeHash + ".tagHash = $tagHash " +
                                                     "SET "+ node.txnNodeHash + ".txnEncode = $txnEncode " +
                                                     "RETURN " + node.txnNodeHash + ".addressHash + ', from node ' + id(" + node.txnNodeHash + ")",
                            parameters( "addressHash", node.addressHash,
                                        "bundleHash", node.bundleHash,
                                        "trunkHash", node.trunkHash,
                                        "branchHash", node.branchHash,
                                        "obsoleteTagHash", node.obsoleteTagHash,
                                        "tagHash", node.tagHash,
                                        "txnEncode", node.txnEncode) );
                    return result.single().get( 0 ).asString();
                }
            } );
            System.out.println( greeting );
        }
        
        return false;
    }

    /**
     * Atomically delete all {@code models}.
     * @param models key value pairs that to be expunged from the db
     * @throws Exception
     */
    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception
    {

    }

    public void clear(Class<?> column) throws Exception
    {

    }

    public void clearMetadata(Class<?> column) throws Exception
    {

    }
}