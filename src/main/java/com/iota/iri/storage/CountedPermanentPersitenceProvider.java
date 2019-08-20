package com.iota.iri.storage;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.persistables.Hashes;

public interface CountedPermanentPersitenceProvider {


    void init() throws Exception;
    boolean isAvailable();
    void shutdown();
    boolean saveTransaction(TransactionViewModel model, Indexable index) throws Exception;

    boolean incrementTransactions(Indexable[] indexes) throws Exception;

    boolean decrementTransactions(Indexable[] indexes) throws Exception;

    boolean setCounter(Indexable index, long counter) throws Exception;

    TransactionViewModel getTransaction(Indexable index) throws Exception;

    Hashes findAddress(Indexable indexes) throws Exception;
    Hashes findBundle(Indexable indexes) throws Exception;
    Hashes findTag(Indexable indexes) throws Exception;
    Hashes findApprovee(Indexable indexes) throws Exception;
}
