package com.iota.iri.crypto;

import com.iota.iri.utils.Converter;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.math.BigInteger;
import java.security.DigestException;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Kerl is a cryptographic hash function, from the sponge family. <br>
 * Kerl is a ternary wrapper to {@code Keccak-384}.
 *
 * @see <a href="https://github.com/iotaledger/kerl/blob/master/IOTA-Kerl-spec.md">Kerl spec</a>
 */
public final class Kerl implements Sponge {

    private static final int BIT_HASH_LENGTH = 384;
    static final int BYTE_HASH_LENGTH = BIT_HASH_LENGTH / 8; //Package Private For Testing

    private static final BigInteger RADIX = BigInteger.valueOf(Converter.RADIX);
    static final int MAX_POWERS_LONG = 40; //Package Private For Testing
    private static final BigInteger[] RADIX_POWERS = IntStream.range(0, MAX_POWERS_LONG + 1).mapToObj(RADIX::pow).toArray(BigInteger[]::new);

    //delegate
    private final Keccak.Digest384 keccak;

    protected Kerl() {
        this.keccak = new Keccak.Digest384();
    }

    @Override
    public void reset() {
        this.keccak.reset();
    }

    @Override
    public void absorb(final byte[] trits, final int offset, final int length) {
        if (length % 243 != 0) {
            throw new RuntimeException("Illegal length: " + length);
        }
        for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {
            //convert to bytes && update
            byte[] state = new byte[BYTE_HASH_LENGTH];
            trits[pos + HASH_LENGTH - 1] = 0;
            bytesFromBigInt(bigIntFromTrits(trits, pos, HASH_LENGTH), state);
            keccak.update(state);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     *     <b>Due to a known vulnerability in this implementation, no more than 243 trits should be squeezed.</b>
     * </p>
     */
    @Override
    public void squeeze(final byte[] trits, final int offset, final int length) {
        if (length % 243 != 0) {
            throw new IllegalArgumentException("Illegal length: " + length);
        }
        try {
            for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {

                byte[] state = new byte[BYTE_HASH_LENGTH];
                keccak.digest(state, 0, BYTE_HASH_LENGTH);

                //convert into trits
                BigInteger value = new BigInteger(state);
                tritsFromBigInt(value, trits, pos, Sponge.HASH_LENGTH);
                trits[pos + HASH_LENGTH - 1] = 0;

                //calculate hash again
                for (int i = state.length; i-- > 0; ) {
                    state[i] = (byte) (state[i] ^ 0xFF);
                }
                keccak.update(state);
            }
        } catch (DigestException e) {
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }


    //Bytes<->Trits Converters, used to convert 384bit to 243trits

    static BigInteger bigIntFromTrits(final byte[] trits, final int offset, final int size) {
        for (int i = offset; i < offset + size; i++) {
            if (trits[i] < -1 || trits[i] > 1) {
                throw new IllegalArgumentException("not a trit: " + trits[i]);
            }
        }
        BigInteger value = BigInteger.ZERO;
        for (int n = offset + size - 1; n >= offset; ) {
            int count = 0;
            long num = 0L;
            while (n >= offset && count < MAX_POWERS_LONG) {
                num = 3 * num + trits[n--];
                count++;
            }
            value = value.multiply(RADIX_POWERS[count]).add(BigInteger.valueOf(num));
        }
        return value;
    }

    static void tritsFromBigInt(final BigInteger value, final byte[] destination, final int offset, final int size) {

        if (destination.length - offset < size) {
            throw new IllegalArgumentException("Destination array has invalid size");
        }
        final int signum = value.signum();
        if (signum == 0) {
            Arrays.fill(destination, offset, size, (byte) 0);
            return;
        }
        BigInteger absoluteValue = value.abs();
        for (int i = 0; i < size; i++) {
            BigInteger[] divRemainder = absoluteValue.divideAndRemainder(RADIX);
            absoluteValue = divRemainder[0];

            byte remainder = divRemainder[1].byteValue();
            if (remainder > Converter.MAX_TRIT_VALUE) {
                remainder = Converter.MIN_TRIT_VALUE;
                absoluteValue = absoluteValue.add(BigInteger.ONE);
            }
            destination[offset + i] = signum < 0 ? (byte) -remainder : remainder;
        }
    }

    static void bytesFromBigInt(final BigInteger value, final byte[] destination) {
        if (destination.length < BYTE_HASH_LENGTH) {
            throw new IllegalArgumentException("Destination array has invalid size.");
        }
        byte[] bytes = value.toByteArray();
        int start = BYTE_HASH_LENGTH - bytes.length;
        Arrays.fill(destination, 0, start, (byte) (value.signum() < 0 ? -1 : 0));
        for (int i = 0; i < bytes.length; i++) {
            destination[start++] = bytes[i];
        }
    }
}
