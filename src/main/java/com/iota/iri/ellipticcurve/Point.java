package com.iota.iri.ellipticcurve;
import java.math.BigInteger;


public class Point {

    public BigInteger x;
    public BigInteger y;
    public BigInteger z;

    public Point(BigInteger x, BigInteger y) {
        this.x = x;
        this.y = y;
        this.z = BigInteger.ZERO;
    }

    public Point(BigInteger x, BigInteger y, BigInteger z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
