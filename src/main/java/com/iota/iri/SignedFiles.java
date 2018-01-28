package com.iota.iri;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

/**
 * Created by alon on 26/01/18.
 */
public class SignedFiles {

    public static boolean isFileSignatureValid(String filename, String signatureFilename, String publicKey, int depth, int index) {
        int[] trits = new int[Curl.HASH_LENGTH*3];
        Sponge curl = SpongeFactory.create(SpongeFactory.Mode.KERL);
        try {
            //digest file
            InputStream in = SignedFiles.class.getResourceAsStream(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while((line = reader.readLine()) != null) {
                Converter.trits(Converter.asciiToTrytes(line), trits, 0);
                curl.absorb(trits, 0, trits.length);
                Arrays.fill(trits, 0);
            }
            //validate signature
            {
                trits = new int[Curl.HASH_LENGTH];
                curl.squeeze(trits, 0, Curl.HASH_LENGTH);
                SpongeFactory.Mode mode = SpongeFactory.Mode.CURLP81;
                int[] digests = new int[0];
                int[] bundle = ISS.normalizedBundle(trits);
                int[] root;
                int i;
                in = SignedFiles.class.getResourceAsStream(signatureFilename);
                reader = new BufferedReader(new InputStreamReader(in));
                for(i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                    int[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                    Converter.trits(line, lineTrits, 0);
                    digests = ArrayUtils.addAll(
                            digests,
                            ISS.digest(mode
                                    , Arrays.copyOfRange(bundle, i*ISS.NORMALIZED_FRAGMENT_LENGTH, (i+1)*ISS.NORMALIZED_FRAGMENT_LENGTH)
                                    , lineTrits));
                }
                if((line = reader.readLine()) != null) {
                    int[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                    Converter.trits(line, lineTrits, 0);
                    root = ISS.getMerkleRoot(mode, ISS.address(mode, digests), lineTrits, 0, index, depth);
                } else {
                    root = ISS.address(mode, digests);
                }

                int[] pubkeyTrits = Converter.allocateTritsForTrytes(publicKey.length());
                Converter.trits(publicKey, pubkeyTrits, 0);
                if(Arrays.equals(pubkeyTrits, root)) {
                    //valid
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
