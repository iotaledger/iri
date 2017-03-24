package com.iota.iri.service.viewModels;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Tag;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class TagViewModel {
    private final Tag tag;
    public static final int TAG_BINARY_SIZE = 17;

    public TagViewModel(Hash hash) {
        this(Converter.bigIntegerValue(hash.trits()));
    }

    public TagViewModel(BigInteger bytes) {
        tag = new Tag();
        tag.value = bytes;
    }
    public TagViewModel(Tag tag) {
        this.tag = tag;
    }

    public BigInteger getHash() {
        return tag.value;
    }

    public BigInteger[] getTransactionHashes() throws ExecutionException, InterruptedException {
        Tangle.instance().load(tag).get();
        return tag.transactions;
    }

}
