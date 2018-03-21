package com.iota.iri;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by alon on 26/01/18.
 */
public class SignedFiles {
    private static final Logger log = LoggerFactory.getLogger(SignedFiles.class);

    public static boolean isFileSignatureValid(String filename, String signatureFilename, String publicKey, int depth, int index) {
        int[] trits = new int[Curl.HASH_LENGTH*3];
        Sponge curl = SpongeFactory.create(SpongeFactory.Mode.KERL);
            //digest file
            File file = new File(filename);
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Converter.trits(Converter.asciiToTrytes(line), trits, 0);
                    curl.absorb(trits, 0, trits.length);
                    Arrays.fill(trits, 0);
                }
            } catch (IOException e) {
                log.error("Can't read file " + filename, e);
                return false;
            }
        //validate signature
        trits = new int[Curl.HASH_LENGTH];
        curl.squeeze(trits, 0, Curl.HASH_LENGTH);
        SpongeFactory.Mode mode = SpongeFactory.Mode.CURLP81;
        int[] digests = new int[0];
        int[] bundle = ISS.normalizedBundle(trits);
        int[] root;
        int i;
        file = new File(signatureFilename);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            for (i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                int[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                Converter.trits(line, lineTrits, 0);
                digests = ArrayUtils.addAll(
                        digests,
                        ISS.digest(mode
                                , Arrays.copyOfRange(bundle, i * ISS.NORMALIZED_FRAGMENT_LENGTH, (i + 1) * ISS.NORMALIZED_FRAGMENT_LENGTH)
                                , lineTrits));
            }
            if ((line = reader.readLine()) != null) {
                int[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                Converter.trits(line, lineTrits, 0);
                root = ISS.getMerkleRoot(mode, ISS.address(mode, digests), lineTrits, 0, index, depth);
            }
            else {
                root = ISS.address(mode, digests);
            }

            int[] pubkeyTrits = Converter.allocateTritsForTrytes(publicKey.length());
            Converter.trits(publicKey, pubkeyTrits, 0);
            if (Arrays.equals(pubkeyTrits, root)) {
                //valid
                return true;
            }
        } catch (IOException e) {
            log.error("Can't read signature file " + filename, e);
            return false;
        }
        return false;
    }
}
