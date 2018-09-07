package com.iota.iri.storage;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;
import com.iota.iri.zmq.MessageQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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
        if(!(model instanceof Transaction)) {
            return false;
        }
        if(!item.contains("sender")) {
            return false;
        }

        Transaction transaction = ((Transaction) model);
        TransactionViewModel transactionViewModel = new TransactionViewModel(transaction, (Hash)index);

        publishTx(transactionViewModel);
        publishTxTrytes(transactionViewModel);

        return true;
    }

    private void publishTx(TransactionViewModel transactionViewModel) {
        StringBuilder txStringBuilder = new StringBuilder(600);

        try {
            txStringBuilder.append("tx ");
            txStringBuilder.append(transactionViewModel.getHash()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getAddressHash()); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.value())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getObsoleteTagValue().toString().substring(0,27)); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getTimestamp())); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getCurrentIndex())); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.lastIndex())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getBundleHash()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getTrunkTransactionHash()); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getBranchTransactionHash()); txStringBuilder.append(" ");
            txStringBuilder.append(String.valueOf(transactionViewModel.getArrivalTime())); txStringBuilder.append(" ");
            txStringBuilder.append(transactionViewModel.getTagValue().toString().substring(0,27));

            messageQ.publish(txStringBuilder.toString());
        } catch (Exception e) {
            log.error(txStringBuilder.toString());
            log.error("Error publishing tx to zmq.", e);
        }
    }

    private void publishTxTrytes(TransactionViewModel transactionViewModel) {
        StringBuilder txTrytesStringBuilder = new StringBuilder(TransactionViewModel.TRINARY_SIZE/3);

        try {
            txTrytesStringBuilder.append("tx_trytes ");
            txTrytesStringBuilder.append(Converter.trytes(transactionViewModel.trits())); txTrytesStringBuilder.append(" ");
            txTrytesStringBuilder.append(transactionViewModel.getHash());

            messageQ.publish(txTrytesStringBuilder.toString());
        } catch (Exception e) {
            log.error(txTrytesStringBuilder.toString());
            log.error("Error publishing tx_trytes to zmq.", e);
        }
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
    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {

    }

    @Override
    public void clear(Class<?> column) throws Exception {

    }

    @Override
    public void clearMetadata(Class<?> column) throws Exception {

    }
}