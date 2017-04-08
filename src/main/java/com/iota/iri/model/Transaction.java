package com.iota.iri.model;

import com.iota.iri.service.TipsManager;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class Transaction {
    public Hash hash;

    public byte[] bytes;
    public int validity;
    public int type;
    public long arrivalTime;
    public int rating = 1;

    public Tag tag = new Tag();
    public Address address = new Address();
    public Bundle bundle = new Bundle();
    public Approvee trunk = new Approvee();
    public Approvee branch = new Approvee();

    public byte[] solid = new byte[]{0};
    public boolean consistent = false;

    public Transaction() {}
    public Transaction(Hash hash) { this.hash = hash;}
}
