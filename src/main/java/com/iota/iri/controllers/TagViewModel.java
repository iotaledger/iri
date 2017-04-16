package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Tag;
import com.iota.iri.storage.Tangle;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class TagViewModel {
    private final Tag tag;
    public static final int TAG_BINARY_SIZE = 17;

    public TagViewModel(Hash hash) {
        tag = new Tag();
        tag.value = hash;
    }
    public TagViewModel(Tag tag) {
        this.tag = tag;
    }

    public Hash getHash() {
        return tag.value;
    }

    public Hash[] getTransactionHashes() throws ExecutionException, InterruptedException {
        Tangle.instance().load(tag).get();
        return tag.transactions;
    }

}
