package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import org.bouncycastle.jcajce.provider.digest.Keccak;

import java.math.BigInteger;
import java.security.DigestException;
import java.util.Arrays;

public class Kerl implements Sponge {
    public static final int BIT_HASH_LENGTH = 384;
    public static final int BYTE_HASH_LENGTH = BIT_HASH_LENGTH / 8;

    private byte[] byte_state;
    private int[] trit_state;
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

        if (length % 243 != 0) throw new RuntimeException("Illegal length: " + length);

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

        if (length % 243 != 0) throw new RuntimeException("Illegal length: " + length);

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

        for (int i = size; i-- > 0; ) {

            value = value.multiply(BigInteger.valueOf(Converter.RADIX)).add(BigInteger.valueOf(trits[offset + i]));
        }

        return value;
    }

    public static BigInteger bigIntFromBytes(final byte[] bytes, final int offset, final int size) {

        return new BigInteger(Arrays.copyOfRange(bytes, offset, offset + size));
    }

    public static void tritsFromBigInt(final BigInteger value, int[] destination, int offset, int size) {

        if(destination.length - offset < size) {
            throw new IllegalArgumentException("Destination array has invalid size");
        }

        BigInteger absoluteValue = value.compareTo(BigInteger.ZERO) < 0 ? value.negate() : value;
        for (int i = 0; i < size; i++) {

            BigInteger[] divRemainder = absoluteValue.divideAndRemainder(BigInteger.valueOf(Converter.RADIX));
            int remainder = divRemainder[1].intValue();
            absoluteValue = divRemainder[0];

            if (remainder > Converter.MAX_TRIT_VALUE) {

                remainder = Converter.MIN_TRIT_VALUE;
                absoluteValue = absoluteValue.add(BigInteger.ONE);
            }
            destination[offset  + i] = remainder;
        }

        if (value.compareTo(BigInteger.ZERO) < 0) {

            for (int i = 0; i < size; i++) {

                destination[offset + i] = -destination[offset + i];
            }
        }
    }
    public static void bytesFromBigInt(final BigInteger value, byte[] destination, int offset) {
        if(destination.length - offset < BYTE_HASH_LENGTH) {
            throw new IllegalArgumentException("Destination array has invalid size.");
        }

        final byte[] bytes = value.toByteArray();
        int i = 0;
        while (i + bytes.length < BYTE_HASH_LENGTH) {

            destination[i++] = (byte) (bytes[0] < 0 ? -1 : 0);
        }
        for (int j = bytes.length; j-- > 0; ) {

            destination[i++] = bytes[bytes.length - 1 - j];
        }
    }
}