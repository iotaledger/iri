package com.iota.iri.model;

import com.iota.iri.tangle.IotaModelIndex;
import com.iota.iri.tangle.IotaModel;
import com.iota.iri.tangle.IotaModelStoredItem;
import com.iota.iri.tangle.IotaModelSecondaryIndex;

import java.nio.ByteBuffer;

/**
 * Created by paul on 3/2/17 for iri.
 */
@IotaModel
public class ITransaction {
    @IotaModelStoredItem
    public byte[] bytes;
    @IotaModelIndex
    public byte[] hash;

    @IotaModelStoredItem
    @IotaModelSecondaryIndex
    public byte[] bundle;

    @IotaModelStoredItem
    @IotaModelSecondaryIndex
    public byte[] tag;

    @IotaModelStoredItem
    @IotaModelSecondaryIndex
    public byte[] address;


    @IotaModelStoredItem
    @IotaModelSecondaryIndex
    public ByteBuffer timestamp;
    @IotaModelStoredItem
    @IotaModelSecondaryIndex
    public ByteBuffer trunk;
    @IotaModelStoredItem
    @IotaModelSecondaryIndex
    public ByteBuffer branch;

    @IotaModelStoredItem
    public boolean isTip;
}
