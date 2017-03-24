package com.iota.iri.model;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class Transaction {
    public Hash hash;

    public byte[] bytes;
    public int validity;
    public int type;
    public long arrivalTime;

    public Tag tag = new Tag();
    public Address address = new Address();
    public Bundle bundle = new Bundle();
    public Approvee trunk = new Approvee();
    public Approvee branch = new Approvee();

}
