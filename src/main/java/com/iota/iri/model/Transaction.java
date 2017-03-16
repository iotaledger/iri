package com.iota.iri.model;

import com.iota.iri.service.tangle.annotations.*;

/**
 * Created by paul on 3/2/17 for iri.
 */
@Model
public class Transaction {
    @SizedArray(length = Hash.SIZE_IN_BYTES)
    @ModelIndex
    public byte[] hash;

    @BelongsTo public Tag tag = new Tag();
    @BelongsTo public Address address = new Address();
    @BelongsTo public Timestamp timestamp = new Timestamp();
    @BelongsTo public Bundle bundle = new Bundle();
    @BelongsTo public Approvee trunk = new Approvee();
    @BelongsTo public Approvee branch = new Approvee();
    @HasOne public byte[] signature;
    @HasOne public byte[] nonce;

    //@HasOne public byte[] bytes;


    @HasOne public int validity;
    @HasOne public int type;
    @HasOne public long value;
    @HasOne public long currentIndex;
    @HasOne public long lastIndex;
    @HasOne public long arrivalTime;
    @HasOne public byte analyzed;

}
