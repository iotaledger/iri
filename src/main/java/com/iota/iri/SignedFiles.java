package com.iota.iri;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.util.Arrays;

public class SignedFiles {

    public static boolean isFileSignatureValid(String filename, String signatureFilename, String publicKey, int depth, int index) throws IOException {
        int[] signature = digestFile(filename, SpongeFactory.create(SpongeFactory.Mode.KERL));
        return validateSignature(signatureFilename, publicKey, depth, index, signature);
    }

    private static boolean validateSignature(String signatureFilename, String publicKey, int depth, int index, int[] digest) throws IOException {
        //validate signature
        SpongeFactory.Mode mode = SpongeFactory.Mode.CURLP81;
        int[] digests = new int[0];
        int[] bundle = ISS.normalizedBundle(digest);
        int[] root;
        int i;

        try (InputStream inputStream = SignedFiles.class.getResourceAsStream(signatureFilename);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(signatureFilename) : new InputStreamReader(inputStream))) {

            String line;
            for (i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                int[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                Converter.trits(line, lineTrits, 0);
                int[] normalizedBundleFragment = Arrays.copyOfRange(bundle, i * ISS.NORMALIZED_FRAGMENT_LENGTH, (i + 1) * ISS.NORMALIZED_FRAGMENT_LENGTH);
                int[] issDigest = ISS.digest(mode, normalizedBundleFragment, lineTrits);
                digests = ArrayUtils.addAll(digests, issDigest);
            }

            if ((line = reader.readLine()) != null) {
                int[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                Converter.trits(line, lineTrits, 0);
                root = ISS.getMerkleRoot(mode, ISS.address(mode, digests), lineTrits, 0, index, depth);
            } else {
                root = ISS.address(mode, digests);
            }

            int[] pubkeyTrits = Converter.allocateTritsForTrytes(publicKey.length());
            Converter.trits(publicKey, pubkeyTrits, 0);
            return Arrays.equals(pubkeyTrits, root); // valid
        }
    }

    private static int[] digestFile(String filename, Sponge curl) throws IOException {
        try (InputStream inputStream = SignedFiles.class.getResourceAsStream(filename);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(filename) : new InputStreamReader(inputStream))) {

            int[] buffer = new int[Curl.HASH_LENGTH * 3];

            reader.lines().forEach(line -> {
                String trytes = Converter.asciiToTrytes(line); // can return a null
                if (trytes == null) {
                    throw new IllegalArgumentException("TRYTES IS NULL. INPUT= '" + line + "'");
                }
                Converter.trits(trytes, buffer, 0);
                curl.absorb(buffer, 0, buffer.length);
                Arrays.fill(buffer, 0);
            });

            int[] signature = new int[Curl.HASH_LENGTH];
            curl.squeeze(signature, 0, Curl.HASH_LENGTH);
            return signature;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}