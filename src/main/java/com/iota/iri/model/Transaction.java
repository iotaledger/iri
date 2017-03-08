package com.iota.iri.model;

import com.iota.iri.tangle.*;
import com.iota.iri.viewModel.TransactionViewModel;

import java.sql.Time;

/**
 * Created by paul on 3/2/17 for iri.
 */
@Model
public class Transaction {
    @SizedArray(length = 49)
    @ModelIndex public byte[] hash;

    @BelongsTo public Tag tag;
    @BelongsTo public Address address;
    @BelongsTo public Timestamp timestamp;
    @BelongsTo public Bundle bundle;
    @BelongsTo public Approvee trunk;
    @BelongsTo public Approvee branch;

    @HasOne public byte[] bytes;

    @HasOne public int validity;
    @HasOne public long value;
    @HasOne public long currentIndex;
    @HasOne public long lastIndex;
    @HasOne public long arrivalTime;
    @HasOne public boolean isTip;
    @HasOne public boolean analyzedFLag;

    public Transaction() {
        tag = new Tag();
        this.bundle = new Bundle();
        this.trunk = new Approvee();
        this.branch = new Approvee();
        address = new Address();
        timestamp = new Timestamp();
    }
}
