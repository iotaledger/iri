package com.iota.iri.hash;

import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 7/23/17.
 */
public class ISSTest {
    static String seed = "NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN";
    static String message = "JCRNMXX9DIEVJJG9VW9QDUMVDGDVHANQDTCPPOPHLTBUBXULSIALRBVUINDPNGUFZLKDPOK9WBJMYCXF9" +
            "MFQN9ZKMROOXHULIDDXRNWMDENBWJWVVA9XPNHQUVDFSMQ9ETWKWGLOLYPWW9GQPVNDYJIRDBWVCBUHUE" +
            "GELSTLEXGAMMQAHSUEABKUSFOVGYRQBXJMORXIDTIPENPAFIUV9DOGZCAEPRJQOISRZDZBWWQQJVQDS9Y" +
            "GCMNADNVSUTXXAONPHBFCMWSVFYYXXWDZXFP9SZGLRCHHGKLNAQPMAXHFUUSQEKDAPH9GFVHMYDITCTFS" +
            "IJEZFADOJVDOEXOTDDPZYLKKDHCGPXYMGRKAGOEQYHTCTGKMZOKMZJLCQOYE9KFVRQLXDPBALUSEQSQDF" +
            "PPUYALCDYWSHANNQYKIMAZMKQQ9XVCSJHAWXLY9IIREZTSOFRMRGKDQPIEMDXTBDTY9DKOAIUEGNLUSRF" +
            "ZYPRNUOHFGDYIWFVKIUNYBGBHICRQTLDQQUTJX9DDSQANVKMCDZ9VEQBCHHSATVFIDYR9XUSDJHQDRBVK" +
            "9JUUZVWGCCWVXAC9ZIOKBWOKCTCJVXIJFBSTLNZCPJMAKDPYLTHMOKLFDNONJLLDBDXNFKPKUBKDU9QFS" +
            "XGVXS9PEDBDDBGFESSKCWUWMTOGHDLOPRILYYPSAQVTSQYLIPK9ATVMMYSTASHEZEFWBUNR9XKGCHR9MB";

    @Test
    public void testSignatureResolvesToAddressISS() throws Exception {
        int index = 10;
        int nof = 1;
        SpongeFactory.Mode[] modes = {SpongeFactory.Mode.CURLP81, SpongeFactory.Mode.KERL};

        int[] seedTrits = new int[Sponge.HASH_LENGTH];

        for (SpongeFactory.Mode mode: modes) {
            Converter.trits(seed, seedTrits, 0);
            int[] subseed = ISS.subseed(mode, seedTrits, index);
            int[] key = ISS.key(mode, subseed, nof);


            Kerl curl = new Kerl();
            int[] messageTrits = Converter.allocateTritsForTrytes(message.length());
            Converter.trits(message, messageTrits, 0);
            curl.absorb(messageTrits, 0, messageTrits.length);
            int[] messageHash = new int[Curl.HASH_LENGTH];
            curl.squeeze(messageHash, 0, Curl.HASH_LENGTH);
            int[] normalizedFragment =
                    Arrays.copyOf(ISS.normalizedBundle(messageHash),
                            ISS.NUMBER_OF_FRAGMENT_CHUNKS);
            int[] signature = ISS.signatureFragment(mode, normalizedFragment, key);
            int[] sigDigest = ISS.digest(mode, normalizedFragment, signature);
            int[] signedAddress = ISS.address(mode, sigDigest);
            int[] digest = ISS.digests(mode, key);
            int[] address = ISS.address(mode, digest);
            assertTrue(Arrays.equals(address, signedAddress));
        }
    }

    @Test
    public void addressGenerationISS() throws Exception {
        int index = 0;
        int nof = 2;
        SpongeFactory.Mode[] modes = {SpongeFactory.Mode.CURLP81, SpongeFactory.Mode.KERL};
        Hash[] hashes = {new Hash("D9XCNSCCAJGLWSQOQAQNFWANPYKYMCQ9VCOMROLDVLONPPLDFVPIZNAPVZLQMPFYJPAHUKIAEKNCQIYJZ"),
                         new Hash("MDWYEJJHJDIUVPKDY9EACGDJUOP9TLYDWETUBOYCBLYXYYYJYUXYUTCTPTDGJYFKMQMCNZDQPTBE9AFIW")};
        for (int i=0;i<modes.length;i++) {
            SpongeFactory.Mode mode = modes[i];
            int[] seedTrits = Converter.allocateTritsForTrytes(seed.length());
            Converter.trits(seed, seedTrits, 0);

            int[] subseed = ISS.subseed(mode, seedTrits, index);
            int[] key = ISS.key(mode, subseed, nof);
            int[] digest = ISS.digests(mode, key);
            int[] address = ISS.address(mode, digest);
            Hash addressTrytes = new Hash(address);
            assertEquals(hashes[i].toString(), addressTrytes.toString());
        }
    }

    public static Hash getRandomTransactionHash() {
        return new Hash(getRandomTrits(Hash.SIZE_IN_TRITS));
    }
    final static Random rnd_seed = new Random();

    public static int[] getRandomTrits(int length) {
        return Arrays.stream(new int[length]).map(i -> rnd_seed.nextInt(3)-1).toArray();
    }

    //@Test
    public void generateNAddressesForSeed() throws Exception {
        int nof = 2;
        System.out.println("seed,address_0,address_1,address_2,address_3");
        for (int i = 0; i< 1000 ; i++) {
            Hash seed = getRandomTransactionHash();
            SpongeFactory.Mode mode = SpongeFactory.Mode.KERL;
            Hash[] addresses = new Hash[4];

            for (int j = 0; j< 4 ; j++) {
                int[] subseed = ISS.subseed(mode, seed.trits(), j);
                int[] key = ISS.key(mode, subseed, nof);
                int[] digest = ISS.digests(mode, key);
                int[] address = ISS.address(mode, digest);
                addresses[j] = new Hash(address);
            }
            System.out.println(String.format("%s,%s,%s,%s,%s", seed, addresses[0],addresses[1],addresses[2],addresses[3]));

        }

    }

}
