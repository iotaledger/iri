package com.iota.iri.model;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.utils.Converter;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Created by paul on 4/29/17.
 */
public class HashTest {
    @Test
    public void calculate() throws Exception {
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, TransactionViewModelTest.getRandomTransactionTrits());
        Assert.assertNotEquals(0, hash.hashCode());
        Assert.assertNotEquals(null, hash.bytes());
        Assert.assertNotEquals(null, hash.trits());
    }

    @Test
    public void calculate1() throws Exception {
        Hash hash = TransactionHash.calculate(TransactionViewModelTest.getRandomTransactionTrits(), 0, 729, SpongeFactory.create(SpongeFactory.Mode.CURLP81));
        Assert.assertNotEquals(null, hash.bytes());
        Assert.assertNotEquals(0, hash.hashCode());
        Assert.assertNotEquals(null, hash.trits());
    }

    @Test
    public void calculate2() throws Exception {
        byte[] trits = TransactionViewModelTest.getRandomTransactionTrits();
        byte[] bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, bytes);
        Hash hash = TransactionHash.calculate(bytes, TransactionViewModel.TRINARY_SIZE, SpongeFactory.create(SpongeFactory.Mode.CURLP81));
        Assert.assertNotEquals(0, hash.hashCode());
        Assert.assertNotEquals(null, hash.bytes());
        Assert.assertNotEquals(null, hash.trits());
    }

    @Test
    public void trailingZeros() throws Exception {
        Hash hash = Hash.NULL_HASH;
        Assert.assertEquals(Hash.SIZE_IN_TRITS, hash.trailingZeros());
    }

    @Test
    public void trits() throws Exception {
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, TransactionViewModelTest.getRandomTransactionTrits());
        Assert.assertFalse(Arrays.equals(new byte[Hash.SIZE_IN_TRITS], hash.trits()));
    }

    @Test
    public void equals() throws Exception {
        byte[] trits = TransactionViewModelTest.getRandomTransactionTrits();
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits);
        Hash hash1 = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits);
        Assert.assertTrue(hash.equals(hash1));
        Assert.assertFalse(hash.equals(Hash.NULL_HASH));
        Assert.assertFalse(hash.equals(TransactionHash.calculate(SpongeFactory.Mode.CURLP81, TransactionViewModelTest.getRandomTransactionTrits())));
    }

    @Test
    public void hashCodeTest() throws Exception {
        byte[] trits = TransactionViewModelTest.getRandomTransactionTrits();
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits);
        Assert.assertNotEquals(hash.hashCode(), 0);
        Assert.assertEquals(Hash.NULL_HASH.hashCode(), -240540129);
    }

    @Test
    public void toStringTest() throws Exception {
        byte[] trits = TransactionViewModelTest.getRandomTransactionTrits();
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits);
        Assert.assertEquals(Hash.NULL_HASH.toString(), "999999999999999999999999999999999999999999999999999999999999999999999999999999999");
        Assert.assertNotEquals(hash.toString(), "999999999999999999999999999999999999999999999999999999999999999999999999999999999");
        Assert.assertNotEquals(hash.toString().length(), 0);

    }

    @Test
    public void bytes() throws Exception {
        byte[] trits = TransactionViewModelTest.getRandomTransactionTrits();
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits);
        Assert.assertTrue(Arrays.equals(new byte[Hash.SIZE_IN_BYTES], Hash.NULL_HASH.bytes()));
        Assert.assertFalse(Arrays.equals(new byte[Hash.SIZE_IN_BYTES], hash.bytes()));
        Assert.assertNotEquals(0, hash.bytes().length);
    }

    @Test
    public void compareTo() throws Exception {
        byte[] trits = TransactionViewModelTest.getRandomTransactionTrits();
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits);
        Assert.assertEquals(hash.compareTo(Hash.NULL_HASH), -Hash.NULL_HASH.compareTo(hash));
    }

}