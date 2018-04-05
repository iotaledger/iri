package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.math.BigInteger;
import java.security.DigestException;
import java.util.Arrays;

public class Kerl implements Sponge {
    public static final int BIT_HASH_LENGTH = 384;
    public static final int BYTE_HASH_LENGTH = BIT_HASH_LENGTH / 8;

    // radix constant conversion for fast math
    public static final BigInteger RADIX = BigInteger.valueOf(Converter.RADIX);
    // lookups for fast math
    private static final BigInteger[] RADIX_POWERS = makeRadixPowers();
    private static final BigInteger[] RADIX_POSITIVE_ONE_FUNCTION = makeRadixFunctionPowers(true);
    private static final BigInteger[] RADIX_NEGATIVE_ONE_FUNCTION = makeRadixFunctionPowers(false);

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
    public void absorb(final int[] trits, int offset, int length) {

        if (length % 243 != 0) {
            throw new RuntimeException("Illegal length: " + length);
        }

        do {
            //copy trits[offset:offset+length]
            System.arraycopy(trits, offset, trit_state, 0, HASH_LENGTH);

            //convert to bits
            trit_state[HASH_LENGTH - 1] = 0;
            bytesFromBigInt(bigIntFromTrits(trit_state, 0, HASH_LENGTH), byte_state, 0);

            //run keccak
            keccak.update(byte_state);
            offset += HASH_LENGTH;

        } while ((length -= HASH_LENGTH) > 0);
    }

    @Override
    public void squeeze(final int[] trits, int offset, int length) {

        if (length % 243 != 0) {
            throw new RuntimeException("Illegal length: " + length);
        }

        try {
            do {
                this.keccak.digest(byte_state, 0, BYTE_HASH_LENGTH);
                //convert to trits
                tritsFromBigInt(bigIntFromBytes(byte_state, 0, BYTE_HASH_LENGTH), trit_state, 0, Sponge.HASH_LENGTH);

                //copy with offset
                trit_state[HASH_LENGTH - 1] = 0;
                System.arraycopy(trit_state, 0, trits, offset, HASH_LENGTH);

                //calculate hash again
                for (int i = byte_state.length; i-- > 0; ) {
                    byte_state[i] = (byte) (byte_state[i] ^ 0xFF);
                }
                keccak.update(byte_state);
                offset += HASH_LENGTH;

            } while ((length -= HASH_LENGTH) > 0);
        } catch (DigestException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static BigInteger bigIntFromTrits(final int[] trits, final int offset, final int size) {

        BigInteger value = BigInteger.ZERO;
        int power = 0;
        for (int i = offset + size - 1; i >= offset; i--) {
            switch (trits[i]) {
                case 1: {
                    int sequence = 0;
                    while (i - 1 >= offset && trits[i - 1] == 1) {
                        i--;
                        sequence++;
                    }
                    value = value.multiply(RADIX_POWERS[power + 1 + sequence]).add(RADIX_POSITIVE_ONE_FUNCTION[sequence]);
                    power = 0;
                }
                break;
                case -1: {
                    int sequence = 0;
                    while (i - 1 >= offset && trits[i - 1] == -1) {
                        i--;
                        sequence++;
                    }
                    value = value.multiply(RADIX_POWERS[power + 1 + sequence]).add(RADIX_NEGATIVE_ONE_FUNCTION[sequence]);
                    power = 0;
                }
                break;
                default:
                    power++;
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

    public static void tritsFromBigInt(final BigInteger value, int[] destination, int offset, int size) {

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
            int remainder = divRemainder[1].intValue();
            absoluteValue = divRemainder[0];

            if (remainder > Converter.MAX_TRIT_VALUE) {
                remainder = Converter.MIN_TRIT_VALUE;
                absoluteValue = absoluteValue.add(BigInteger.ONE);
            }
            destination[offset + i] = signum < 0 ? -remainder : remainder;
        }
    }

    public static void bytesFromBigInt(final BigInteger value, byte[] destination, int offset) {
        if (destination.length - offset < BYTE_HASH_LENGTH) {
            throw new IllegalArgumentException("Destination array has invalid size.");
        }

        final byte[] bytes = value.toByteArray();
        final byte bVal = (byte) (bytes[0] < 0 ? -1 : 0);
        int i = 0;
        while (i + bytes.length < BYTE_HASH_LENGTH) {

            destination[i++] = bVal;
        }
        for (int j = bytes.length; j-- > 0; ) {

            destination[i++] = bytes[bytes.length - 1 - j];
        }
    }

    private static BigInteger[] makeRadixPowers() {
        BigInteger[] pows = new BigInteger[Sponge.HASH_LENGTH + 1];
        for (int n = 0; n < pows.length; n++) {
            pows[n] = RADIX.pow(n);
        }
        return pows;
    }

    private static BigInteger[] makeRadixFunctionPowers(boolean positive) {
        BigInteger[] fns = new BigInteger[Sponge.HASH_LENGTH + 1];
        fns[0] = positive ? BigInteger.ONE : BigInteger.ONE.negate();
        for (int n = 1; n < fns.length; n++) {
            BigInteger value = positive
                ? RADIX.pow(n).add(fns[n - 1])
                : RADIX.pow(n).negate().add(fns[n - 1]);
            fns[n] = value;
        }
        return fns;

    }
}