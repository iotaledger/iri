package com.iota.iri.utils;

import com.iota.iri.crypto.Sponge;
import com.iota.iri.crypto.SpongeFactory;
import io.undertow.security.idm.IdentityManager;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.PasswordCredential;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.iota.iri.crypto.Curl;

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
            byte[] inTrits = Converter.allocateTritsForTrytes(trytes.length());
            Converter.trits(trytes, inTrits, 0);
            byte[] hashTrits = new byte[Curl.HASH_LENGTH];
            Sponge curl;
            curl = SpongeFactory.create(SpongeFactory.Mode.CURLP81);
            curl.absorb(inTrits, 0, inTrits.length);
            curl.squeeze(hashTrits, 0, Curl.HASH_LENGTH);
            String outTrytes = Converter.trytes(hashTrits);
            char[] charOutTrytes = outTrytes.toCharArray();
            char[] expectedPassword = users.get(account.getPrincipal().getName()); 
            boolean verified = Arrays.equals(givenPassword, expectedPassword);
            // Password can either be clear text or the hash of the password
            if (!verified) {
                verified = Arrays.equals(charOutTrytes, expectedPassword);
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