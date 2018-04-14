package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.math.BigInteger;
import java.security.DigestException;
import java.util.Arrays;
import java.util.stream.IntStream;

public final class Kerl implements Sponge {

    public static final int BIT_HASH_LENGTH = 384;
    public static final int BYTE_HASH_LENGTH = BIT_HASH_LENGTH / 8;

    // radix constant conversion for fast math
    public static final BigInteger RADIX = BigInteger.valueOf(Converter.RADIX);
    private static final int MAX_POWERS_LONG = 40;
    private static final BigInteger[] RADIX_POWERS = IntStream.range(0, MAX_POWERS_LONG + 1).mapToObj(RADIX::pow).toArray(BigInteger[]::new);

    private final byte[] byte_state;
    private final int[] trit_state;
    private final Keccak.Digest384 keccak;

    protected Kerl() {
        this.keccak = new Keccak.Digest384();
        this.byte_state = new byte[BYTE_HASH_LENGTH];
        this.trit_state = new int[Sponge.HASH_LENGTH];
    }

    @Override
    public void reset() {
        this.keccak.reset();
    }

    @Override
    public void absorb(final int[] trits, final int offset, final int length) {
        if (length % 243 != 0) {
            throw new RuntimeException("Illegal length: " + length);
        }
        for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {
            //copy trits[offset:offset+length]
            System.arraycopy(trits, pos, trit_state, 0, HASH_LENGTH);

            //convert to bits
            trit_state[HASH_LENGTH - 1] = 0;
            bytesFromBigInt(bigIntFromTrits(trit_state, 0, HASH_LENGTH), byte_state);

            //run keccak
            keccak.update(byte_state);
        }
    }

    @Override
    public void squeeze(final int[] trits, final int offset, final int length) {
        if (length % 243 != 0) {
            throw new IllegalArgumentException("Illegal length: " + length);
        }
        try {
            for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {
                this.keccak.digest(byte_state, 0, BYTE_HASH_LENGTH);
                //convert to trits
                tritsFromBigInt(bigIntFromBytes(byte_state, 0, BYTE_HASH_LENGTH), trit_state, 0, Sponge.HASH_LENGTH);

                //copy with offset
                trit_state[HASH_LENGTH - 1] = 0;
                System.arraycopy(trit_state, 0, trits, pos, HASH_LENGTH);

                //calculate hash again
                for (int i = byte_state.length; i-- > 0; ) {
                    byte_state[i] = (byte) (byte_state[i] ^ 0xFF);
                }
                keccak.update(byte_state);
            }
        } catch (DigestException e) {
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }

    public static BigInteger bigIntFromTrits(final int[] trits, final int offset, final int size) {
        for (int i = offset; i < offset + size; i++) {
            if (trits[i] < -1 || trits[i] > 1) {
                throw new IllegalArgumentException("not a trit: " + trits[i]);
            }
        }
        BigInteger value = BigInteger.ZERO;
        for (int n = offset + size - 1, count = 0; n >= offset; count = 0) {
            long num = 0L;
            for (; n >= offset && count < MAX_POWERS_LONG; count++, n--) {
                num = 3 * num + trits[n];
            }
            value = value.multiply(RADIX_POWERS[count]).add(BigInteger.valueOf(num));
        }
        return value;
    }

    public static BigInteger bigIntFromBytes(final byte[] bytes, final int offset, final int size) {
        return (bytes.length == offset + size) ? new BigInteger(bytes)
            : new BigInteger(Arrays.copyOfRange(bytes, offset, offset + size));
    }

    public static void tritsFromBigInt(final BigInteger value, final int[] destination, final int offset, final int size) {

        if (destination.length - offset < size) {
            throw new IllegalArgumentException("Destination array has invalid size");
        }
        final int signum = value.signum();
        if (signum == 0) {
            Arrays.fill(destination, offset, size, 0);
            return;
        }
        BigInteger absoluteValue = value.abs();
        for (int i = 0; i < size; i++) {
            BigInteger[] divRemainder = absoluteValue.divideAndRemainder(RADIX);
            absoluteValue = divRemainder[0];

            int remainder = divRemainder[1].intValue();
            if (remainder > Converter.MAX_TRIT_VALUE) {
                remainder = Converter.MIN_TRIT_VALUE;
                absoluteValue = absoluteValue.add(BigInteger.ONE);
            }
            destination[offset + i] = signum < 0 ? -remainder : remainder;
        }
    }

    public static void bytesFromBigInt(final BigInteger value, final byte[] destination) {
        if (destination.length < BYTE_HASH_LENGTH) {
            throw new IllegalArgumentException("Destination array has invalid size.");
        }
        int start = BYTE_HASH_LENGTH - (value.bitLength() / 8 + 1);
        Arrays.fill(destination, 0, start, (byte) (value.signum() < 0 ? -1 : 0));
        for (byte aByte : value.toByteArray()) {
            destination[start++] = aByte;
        }
    }
}