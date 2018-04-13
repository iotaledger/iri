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
    private static final BigInteger[] RADIX_POWERS = IntStream.range(0, Sponge.HASH_LENGTH + 1).mapToObj(RADIX::pow).toArray(BigInteger[]::new);

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
        long num;
        int power = 0;
        int n = offset + size - 1;
        for (; n > offset + 1; n--) {
            switch (trits[n]) {
                case 0: // a * b
                    power++;
                    break;
                case 1: // a * b + 1
                    num = 9 + (trits[--n] * 3) + trits[--n];
                    for (int count = 0; n > offset && count <= 36; count++, power++) {
                        num = 3 * num + trits[--n];
                    }
                    value = value.multiply(RADIX_POWERS[power + 3]).add(BigInteger.valueOf(num));
                    power = 0;
                    break;
                case -1: // a * b - 1
                    num = 9 - (trits[--n] * 3) - trits[--n];
                    for (int count = 0; n > offset && count <= 36; count++, power++) {
                        num = 3 * num - trits[--n];
                    }
                    value = value.multiply(RADIX_POWERS[power + 3]).subtract(BigInteger.valueOf(num));
                    power = 0;
                    break;

                default:
                    throw new RuntimeException("unreachable");
            }
        }
        for (; n >= offset; n--) {
            switch (trits[n]) {
                case 0:
                    power++;
                    break;
                case 1:
                    value = value.multiply(RADIX_POWERS[power + 1]).add(BigInteger.ONE);
                    power = 0;
                    break;
                case -1:
                    value = value.multiply(RADIX_POWERS[power + 1]).subtract(BigInteger.ONE);
                    power = 0;
                    break;
                default:
                    throw new RuntimeException("unreachable");
            }
        }
        // do leftover pow
        if (power > 0) {
            value = value.multiply(RADIX_POWERS[power]);
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
        for (final byte aByte : value.toByteArray()) {
            destination[start++] = aByte;
        }
    }
}