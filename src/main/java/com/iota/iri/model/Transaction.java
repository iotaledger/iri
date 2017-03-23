package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.*;
import com.iota.iri.service.viewModels.TransactionViewModel;

import java.math.BigInteger;

/**
 * Created by paul on 3/2/17 for iri.
 */
@Model
public class Transaction {
    private static final int SIZE = 1604;
    @SizedArray(length = Hash.SIZE_IN_BYTES)
    @ModelIndex
    public BigInteger hash;

    @HasOne public byte[] bytes;
    @HasOne public int validity;
    @HasOne public int type;
    @HasOne public long arrivalTime;

    @BelongsTo public Tag tag = new Tag();
    @BelongsTo public Address address = new Address();
    @BelongsTo public Bundle bundle = new Bundle();
    @BelongsTo public Approvee trunk = new Approvee();
    @BelongsTo public Approvee branch = new Approvee();

}
