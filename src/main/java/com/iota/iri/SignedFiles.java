package com.iota.iri;

import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.ISS;
import com.iota.iri.crypto.Sponge;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.util.Arrays;

public class SignedFiles {

    public static boolean isFileSignatureValid(String filename, String signatureFilename, String publicKey, int depth, int index) throws IOException {
        byte[] signature = digestFile(filename, SpongeFactory.create(SpongeFactory.Mode.KERL));
        return validateSignature(signatureFilename, publicKey, depth, index, signature);
    }

    private static boolean validateSignature(String signatureFilename, String publicKey, int depth, int index, byte[] digest) throws IOException {
        //validate signature
        SpongeFactory.Mode mode = SpongeFactory.Mode.CURLP81;
        byte[] digests = new byte[0];
        byte[] bundle = ISS.normalizedBundle(digest);
        byte[] root;
        int i;

        try (InputStream inputStream = SignedFiles.class.getResourceAsStream(signatureFilename);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(signatureFilename) : new InputStreamReader(inputStream))) {

            String line;
            for (i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                byte[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                Converter.trits(line, lineTrits, 0);
                byte[] normalizedBundleFragment = Arrays.copyOfRange(bundle, i * ISS.NORMALIZED_FRAGMENT_LENGTH, (i + 1) * ISS.NORMALIZED_FRAGMENT_LENGTH);
                byte[] issDigest = ISS.digest(mode, normalizedBundleFragment, lineTrits);
                digests = ArrayUtils.addAll(digests, issDigest);
            }

            if ((line = reader.readLine()) != null) {
                byte[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                Converter.trits(line, lineTrits, 0);
                root = ISS.getMerkleRoot(mode, ISS.address(mode, digests), lineTrits, 0, index, depth);
            } else {
                root = ISS.address(mode, digests);
            }

            byte[] pubkeyTrits = Converter.allocateTritsForTrytes(publicKey.length());
            Converter.trits(publicKey, pubkeyTrits, 0);
            return Arrays.equals(pubkeyTrits, root); // valid
        }
    }

    private static byte[] digestFile(String filename, Sponge curl) throws IOException {
        try (InputStream inputStream = SignedFiles.class.getResourceAsStream(filename);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(filename) : new InputStreamReader(inputStream))) {

            byte[] buffer = new byte[Curl.HASH_LENGTH * 3];

            reader.lines().forEach(line -> {
                String trytes = Converter.asciiToTrytes(line); // can return a null
                if (trytes == null) {
                    throw new IllegalArgumentException("TRYTES IS NULL. INPUT= '" + line + "'");
                }
                Converter.trits(trytes, buffer, 0);
                curl.absorb(buffer, 0, buffer.length);
                Arrays.fill(buffer, (byte) 0);
            });

            byte[] signature = new byte[Curl.HASH_LENGTH];
            curl.squeeze(signature, 0, Curl.HASH_LENGTH);
            return signature;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}