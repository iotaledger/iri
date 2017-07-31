package com.iota.iri.storage;

import java.util.List;
import java.util.Set;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Pair;
import com.iota.iri.zmq.MessageQ;

public class ZmqPublishProvider implements PersistenceProvider {

    private final MessageQ messageQ;
    
    public ZmqPublishProvider( MessageQ messageQ ) {
        this.messageQ = messageQ;
    }
    
    @Override
    public void init() throws Exception {
        
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void shutdown() {
        
    }

    @Override
    public boolean save(Persistable model, Indexable index) throws Exception {
        return false;
    }

    @Override
    public void delete(Class<?> model, Indexable index) throws Exception {
        
    }

    @Override
    public boolean update(Persistable model, Indexable index, String item) throws Exception {
        if (model instanceof Transaction) {
            Transaction transaction = ((Transaction) model);
            if (item.contains("sender")) {
                TransactionViewModel transactionViewModel = new TransactionViewModel(transaction, (Hash)index);
                messageQ.publish("tx %s %s %d %s %d %d %d %s %s %s", 
                        transactionViewModel.getHash(),
                        transactionViewModel.getAddressHash(), 
                        transactionViewModel.value(),
                        transactionViewModel.getTagValue(), 
                        transactionViewModel.getTimestamp(),
                        transactionViewModel.getCurrentIndex(), 
                        transactionViewModel.lastIndex(),
                        transactionViewModel.getBundleHash(), 
                        transactionViewModel.getTrunkTransactionHash(),
                        transactionViewModel.getBranchTransactionHash());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean exists(Class<?> model, Indexable key) throws Exception {
        return false;
    }

    @Override
    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception {
        return null;
    }

    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass) throws Exception {
        return null;
    }

    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public boolean mayExist(Class<?> model, Indexable index) throws Exception {
        return false;
    }

    @Override
    public long count(Class<?> model) throws Exception {
        return 0;
    }

    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        return null;
    }

    @Override
    public Persistable seek(Class<?> model, byte[] key) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        return null;
    }

    @Override
    public Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception {
        return null;
    }

    @Override
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        return false;
    }

}
