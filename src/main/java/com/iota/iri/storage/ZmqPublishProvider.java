package com.iota.iri.storage;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Pair;
import com.iota.iri.zmq.MessageQ;

public class ZmqPublishProvider implements PersistenceProvider {

    private static final Logger log = LoggerFactory.getLogger(ZmqPublishProvider.class);
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
                StringBuffer sb = new StringBuffer(600);
                try {
                    sb.append("tx ");
                    sb.append(transactionViewModel.getHash()); sb.append(" ");
                    sb.append(transactionViewModel.getAddressHash()); sb.append(" ");
                    sb.append(String.valueOf(transactionViewModel.value())); sb.append(" ");
                    sb.append(transactionViewModel.getObsoleteTagValue().toString().substring(0,27)); sb.append(" ");
                    sb.append(String.valueOf(transactionViewModel.getTimestamp())); sb.append(" ");
                    sb.append(String.valueOf(transactionViewModel.getCurrentIndex())); sb.append(" ");
                    sb.append(String.valueOf(transactionViewModel.lastIndex())); sb.append(" ");
                    sb.append(transactionViewModel.getBundleHash()); sb.append(" ");
                    sb.append(transactionViewModel.getTrunkTransactionHash()); sb.append(" ");
                    sb.append(transactionViewModel.getBranchTransactionHash()); sb.append(" ");
                    sb.append(String.valueOf(transactionViewModel.getArrivalTime()));
                    messageQ.publish(sb.toString());
                }
                catch (Exception e) {
                    log.error(sb.toString());
                    log.error("Error publishing to zmq.", e);
                }
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

    @Override
    public void clear(Class<?> column) throws Exception {

    }

    @Override
    public void clearMetadata(Class<?> column) throws Exception {

    }

}