package com.iota.iri.utils;

import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import io.undertow.security.idm.IdentityManager;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.PasswordCredential;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.iota.iri.hash.Curl;

public class MapIdentityManager implements IdentityManager {

    private final Map<String, char[]> users;

    public MapIdentityManager(final Map<String, char[]> users) {
        this.users = users;
    }

    @Override
    public Account verify(Account account) {
        // An existing account so for testing assume still valid.
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
        Account account = getAccount(id);
        if (account != null && verifyCredential(account, credential)) {
            return account;
        }

        return null;
    }

    @Override
    public Account verify(Credential credential) {
        // TODO Auto-generated method stub
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential) {
        if (credential instanceof PasswordCredential) {
            char[] givenPassword = ((PasswordCredential) credential).getPassword();            
            String trytes = Converter.asciiToTrytes(new String(givenPassword));
            byte[] in_trits = Converter.allocateTritsForTrytes(trytes.length());
            Converter.trits(trytes, in_trits, 0);
            byte[] hash_trits = new byte[Curl.HASH_LENGTH];
            Sponge curl;
            curl = SpongeFactory.create(SpongeFactory.Mode.CURLP81);
            curl.absorb(in_trits, 0, in_trits.length);
            curl.squeeze(hash_trits, 0, Curl.HASH_LENGTH);
            String out_trytes = Converter.trytes(hash_trits);
            char[] char_out_trytes = out_trytes.toCharArray();
            char[] expectedPassword = users.get(account.getPrincipal().getName()); 
            boolean verified = Arrays.equals(givenPassword, expectedPassword);
            // Password can either be clear text or the hash of the password
            if (!verified) {
                verified = Arrays.equals(char_out_trytes, expectedPassword);
            }            
            return verified;
        }
        return false;
    }

    private Account getAccount(final String id) {
        if (users.containsKey(id)) {
            return new Account() {

                private final Principal principal = new Principal() {

                    @Override
                    public String getName() {
                        return id;
                    }
                };

                @Override
                public Principal getPrincipal() {
                    return principal;
                }

                @Override
                public Set<String> getRoles() {
                    return Collections.emptySet();
                }

            };
        }
        return null;
    }

}