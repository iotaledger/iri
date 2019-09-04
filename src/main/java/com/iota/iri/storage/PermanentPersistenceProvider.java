package com.iota.iri.storage;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Hashes;

import java.util.List;

public interface PermanentPersistenceProvider {


    void init() throws Exception;
    boolean isAvailable();
    void shutdown();
    boolean pinTransaction(TransactionViewModel model, Hash index) throws Exception;
    boolean unpinTransaction(Hash index) throws Exception;
    boolean[] isPinned(List<Hash> indexes) throws Exception;


    TransactionViewModel getTransaction(Hash index) throws Exception;

    Hashes findAddress(Hash indexes) throws Exception;
    Hashes findBundle(Hash indexes) throws Exception;
    Hashes findTag(Hash indexes) throws Exception;
    Hashes findApprovee(Hash indexes) throws Exception;
}
