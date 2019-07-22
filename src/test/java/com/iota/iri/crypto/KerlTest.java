package com.iota.iri.crypto;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.utils.Converter;

/**
 * Created by alon on 04/08/17.
 */
public class KerlTest {
    final static Random seed = new Random();
    Logger log = LoggerFactory.getLogger(CurlTest.class);
    final static Random rnd_seed = new Random();

    //Test conversion functions:
    @Test
    public void tritsFromBigInt() throws Exception {
        long value = 1433452143;
        int size = 50;
        byte[] trits = new byte[size];
        Converter.copyTrits(value, trits, 0, trits.length);
        BigInteger bigInteger = Kerl.bigIntFromTrits(trits, 0, trits.length);
        byte[] outTrits = new byte[size];
        Kerl.tritsFromBigInt(bigInteger, outTrits, 0, size);
        Assert.assertTrue(Arrays.equals(trits, outTrits));
    }

    @Test
    public void bytesFromBigInt() throws Exception {
        BigInteger bigInteger = new BigInteger("13190295509826637194583200125168488859623001289643321872497025844241981297292953903419783680940401133507992851240799");
        byte[] outBytes = new byte[Kerl.BYTE_HASH_LENGTH];
        Kerl.bytesFromBigInt(bigInteger, outBytes);
        BigInteger outBigInteger = new BigInteger(outBytes);
        Assert.assertTrue(bigInteger.equals(outBigInteger));
    }

    @Test
    public void loopRandBytesFromBigInt() throws Exception {
        //generate random bytes, turn them to trits and back
        int byteSize = 48;
        int tritSize = 243;
        byte[] inBytes = new byte[byteSize];
        byte[] trits = new byte[Kerl.HASH_LENGTH];
        byte[] outBytes = new byte[Kerl.BYTE_HASH_LENGTH];
        for (int i = 0; i < 10_000; i++) {
            seed.nextBytes(inBytes);
            BigInteger inBigInteger = new BigInteger(inBytes);
            Kerl.tritsFromBigInt(inBigInteger, trits, 0, tritSize);
            BigInteger outBigInteger = Kerl.bigIntFromTrits(trits, 0, tritSize);
            Kerl.bytesFromBigInt(outBigInteger, outBytes);
            if (i % 1_000 == 0) {
                System.out.println(String.format("%d iteration: %s", i, inBigInteger));
            }
            Assert.assertTrue(String.format("bigInt that failed: %s", inBigInteger), Arrays.equals(inBytes, outBytes));
        }
    }

    @Test
    public void loopRandTritsFromBigInt() throws Exception {
        //generate random bytes, turn them to trits and back
        int tritSize = 243;
        byte[] inTrits;
        byte[] bytes = new byte[Kerl.BYTE_HASH_LENGTH];
        byte[] outTrits = new byte[Kerl.HASH_LENGTH];
        for (int i = 0; i < 10_000; i++) {
            inTrits = getRandomTrits(tritSize);
            inTrits[242] = 0;

            BigInteger inBigInteger = Kerl.bigIntFromTrits(inTrits, 0, tritSize);
            Kerl.bytesFromBigInt(inBigInteger, bytes);
            BigInteger outBigInteger = new BigInteger(bytes);
            Kerl.tritsFromBigInt(outBigInteger, outTrits, 0, tritSize);

            if (i % 1_000 == 0) {
                System.out.println(String.format("%d iteration: %s", i, inBigInteger));
            }
            Assert.assertTrue(String.format("bigInt that failed: %s", inBigInteger), Arrays.equals(inTrits, outTrits));
        }
    }

    @Test
    public void limitBigIntFromTrits() {
        // this confirms that the long math does not produce an overflow.
        byte[] trits = new byte[Kerl.MAX_POWERS_LONG];

        Arrays.fill(trits, (byte) 1);
        BigInteger result = Kerl.bigIntFromTrits(trits, 0, trits.length);

        Arrays.fill(trits, (byte) 1);
        BigInteger expected = BigInteger.ZERO;
        for (int i = trits.length; i-- > 0; ) {
            expected = expected.multiply(BigInteger.valueOf(Converter.RADIX)).add(BigInteger.valueOf(trits[i]));
        }
        Assert.assertTrue("Overflow in long math", expected.equals(result));
    }

    //@Test
    public void generateBytesFromBigInt() throws Exception {
        System.out.println("bigInteger,ByteArray");
        for (int i = 0; i < 100_000; i++) {
            int byteSize = 48;
            byte[] outBytes = new byte[byteSize];
            seed.nextBytes(outBytes);
            BigInteger outBigInteger = new BigInteger(outBytes);
            System.out.println(String.format("%s,%s", outBigInteger, Arrays.toString(outBigInteger.toByteArray())));
            // Assert.assertTrue(bigInteger.equals(outBigInteger));
        }
    }

    //@Test
    public void benchmarkCurl() {
        int i;
        long start, diff;
        long maxdiff = 0, sumdiff = 0, subSumDiff = 0;
        int max = 100;// was 10000;
        int interval = 1000;

        for (i = 0; i++ < max; ) {
            //pre
            int size = 8019;
            byte[] inTrits = getRandomTrits(size);
            byte[] hashTrits = new byte[Curl.HASH_LENGTH];

            start = System.nanoTime();
            //measured

//            Curl curl;
//            curl = new Curl();
//            curl.absorb(inTrits, 0, inTrits.length);
//            curl.squeeze(hashTrits, 0, Curl.HASH_LENGTH);

            Kerl kerl;
            kerl = new Kerl();
            kerl.absorb(inTrits, 0, inTrits.length);
            kerl.squeeze(hashTrits, 0, Curl.HASH_LENGTH);

            diff = System.nanoTime() - start;
            //post
            Converter.trytes(hashTrits);

            sumdiff += diff;
            subSumDiff += diff;
            if (diff > maxdiff) {
                maxdiff = diff;
            }
            if (i % interval == 0) {
                //log.info("{}", new String(new char[(int) ((diff / 10000))]).replace('\0', '|'));
            }
            if (i % interval == 0) {
                log.info("Run time for {}: {} us.\tInterval Time: {} us.\tMax time per iter: {} us. \tAverage: {} us.\t Total time: {} us.", i,
                    (diff / 1000), subSumDiff / 1000, (maxdiff / 1000), sumdiff / i / 1000, sumdiff / 1000);
                subSumDiff = 0;
                maxdiff = 0;
            }
        }
    }

    @Test
    public void kerlOneAbsorb() throws Exception {
        byte[] initialValue = Converter.allocatingTritsFromTrytes(
                "EMIDYNHBWMBCXVDEFOFWINXTERALUKYYPPHKP9JJFGJEIUY9MUDVNFZHMMWZUYUSWAIOWEVTHNWMHANBH");
        Sponge k = SpongeFactory.create(SpongeFactory.Mode.KERL);
        k.absorb(initialValue, 0, initialValue.length);
        byte[] hashValue = new byte[Curl.HASH_LENGTH];
        k.squeeze(hashValue, 0, hashValue.length);
        String hash = Converter.trytes(hashValue);
        Assert.assertEquals("EJEAOOZYSAWFPZQESYDHZCGYNSTWXUMVJOVDWUNZJXDGWCLUFGIMZRMGCAZGKNPLBRLGUNYWKLJTYEAQX", hash);
    }

    @Test
    public void kerlMultiSqueeze() throws Exception {
        byte[] initialValue = Converter.allocatingTritsFromTrytes(
                "9MIDYNHBWMBCXVDEFOFWINXTERALUKYYPPHKP9JJFGJEIUY9MUDVNFZHMMWZUYUSWAIOWEVTHNWMHANBH");
        Sponge k = SpongeFactory.create(SpongeFactory.Mode.KERL);
        k.absorb(initialValue, 0, initialValue.length);
        byte[] hashValue = new byte[Curl.HASH_LENGTH * 2];
        k.squeeze(hashValue, 0, hashValue.length);
        String hash = Converter.trytes(hashValue);
        Assert.assertEquals("G9JYBOMPUXHYHKSNRNMMSSZCSHOFYOYNZRSZMAAYWDYEIMVVOGKPJBVBM9TDPULSFUNMTVXRKFIDOHUXXVYDLFSZYZTWQYTE9SPYYWYTXJYQ9IFGYOLZXWZBKWZN9QOOTBQMWMUBLEWUEEASRHRTNIQWJQNDWRYLCA", hash);
    }

    @Test
    public void kerlMultiAbsorbMultiSqueeze() throws Exception {
        byte[] initialValue = Converter.allocatingTritsFromTrytes(
                "G9JYBOMPUXHYHKSNRNMMSSZCSHOFYOYNZRSZMAAYWDYEIMVVOGKPJBVBM9TDPULSFUNMTVXRKFIDOHUXXVYDLFSZYZTWQYTE9SPYYWYTXJYQ9IFGYOLZXWZBKWZN9QOOTBQMWMUBLEWUEEASRHRTNIQWJQNDWRYLCA");
        Sponge k = SpongeFactory.create(SpongeFactory.Mode.KERL);
        k.absorb(initialValue, 0, initialValue.length);
        byte[] hashValue = new byte[Curl.HASH_LENGTH * 2];
        k.squeeze(hashValue, 0, hashValue.length);
        String hash = Converter.trytes(hashValue);
        Assert.assertEquals("LUCKQVACOGBFYSPPVSSOXJEKNSQQRQKPZC9NXFSMQNRQCGGUL9OHVVKBDSKEQEBKXRNUJSRXYVHJTXBPDWQGNSCDCBAIRHAQCOWZEBSNHIJIGPZQITIBJQ9LNTDIBTCQ9EUWKHFLGFUVGGUWJONK9GBCDUIMAYMMQX", hash);
    }

    public static byte[] getRandomTrits(int length) {
        byte[] out = new byte[length];

        for(int i = 0; i < out.length; i++) {
            out[i] = (byte) (rnd_seed.nextInt(3) - 1);
        }

        return out;
    }

    public static Hash getRandomTransactionHash() {
        return HashFactory.TRANSACTION.create(getRandomTrits(Hash.SIZE_IN_TRITS));
    }

    //@Test
    public void generateTrytesAndHashes() throws Exception {
        System.out.println("trytes,Kerl_hash");
        for (int i = 0; i < 10000; i++) {
            Hash trytes = getRandomTransactionHash();
            byte[] initialValue = trytes.trits();
            Sponge k = SpongeFactory.create(SpongeFactory.Mode.KERL);
            k.absorb(initialValue, 0, initialValue.length);
            byte[] hashValue = new byte[Curl.HASH_LENGTH];
            k.squeeze(hashValue, 0, hashValue.length);
            String hash = Converter.trytes(hashValue);
            System.out.println(String.format("%s,%s", trytes, hash));
        }
    }

    //@Test
    public void generateTrytesAndMultiSqueeze() throws Exception {
        System.out.println("trytes,Kerl_squeeze1,Kerl_squeeze2,Kerl_squeeze3");
        for (int i = 0; i < 10000; i++) {
            Hash trytes = getRandomTransactionHash();
            byte[] initialValue = trytes.trits();
            Sponge k = SpongeFactory.create(SpongeFactory.Mode.KERL);
            k.absorb(initialValue, 0, initialValue.length);
            byte[] hashValue = new byte[Curl.HASH_LENGTH];
            k.squeeze(hashValue, 0, hashValue.length);
            String hash1 = Converter.trytes(hashValue);
            k.squeeze(hashValue, 0, hashValue.length);
            String hash2 = Converter.trytes(hashValue);
            k.squeeze(hashValue, 0, hashValue.length);
            String hash3 = Converter.trytes(hashValue);
            System.out.println(String.format("%s,%s,%s,%s", trytes, hash1, hash2, hash3));
        }
    }

    //@Test
    public void generateMultiTrytesAndHash() throws Exception {
        System.out.println("multiTrytes,Kerl_hash");
        for (int i = 0; i < 10000; i++) {
            String multi = String.format("%s%s%s", getRandomTransactionHash(), getRandomTransactionHash(), getRandomTransactionHash());
            byte[] initialValue = Converter.allocatingTritsFromTrytes(multi);
            Sponge k = SpongeFactory.create(SpongeFactory.Mode.KERL);
            k.absorb(initialValue, 0, initialValue.length);
            byte[] hashValue = new byte[Curl.HASH_LENGTH];
            k.squeeze(hashValue, 0, hashValue.length);
            String hash = Converter.trytes(hashValue);
            System.out.println(String.format("%s,%s", multi, hash));
        }
    }


    //@Test
    public void generateHashes() throws Exception {
        //System.out.println("trytes,Kerl_hash");
        for (int i = 0; i < 1_000_000; i++) {
            Hash trytes = getRandomTransactionHash();
            byte[] initialValue = trytes.trits();
            Sponge k = SpongeFactory.create(SpongeFactory.Mode.KERL);
            k.absorb(initialValue, 0, initialValue.length);
            byte[] hashValue = new byte[Curl.HASH_LENGTH];
            k.squeeze(hashValue, 0, hashValue.length);
            String hash = Converter.trytes(hashValue);
            //System.out.println(String.format("%s,%s",trytes,hash));
            System.out.println(String.format("%s", hash));
        }
    }

}
