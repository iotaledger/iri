package com.iota.iri.utils;

import io.undertow.security.idm.Account;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.idm.X509CertificateCredential;
import org.junit.Test;

import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MapIdentityManagerTest {
    private String testUser = "testUser";
    private char[] testPassword = "testPassword".toCharArray();

    @Test
    public void verifyAccountWithClearTextPassword() {
        HashMap<String, char[]> users = new HashMap<String, char[]>() {{
            put(testUser, testPassword);
        }};
        MapIdentityManager identityManager = new MapIdentityManager(users);
        Account account = identityManager.verify(testUser, new PasswordCredential(testPassword));

        assertEquals("testUser needs equal to user returned by account", testUser, account.getPrincipal().getName());
        assertThat("Roles must not be implemented", account.getRoles(), is(empty()));
    }

    @Test
    public void verifyAccountWithTrytes() {
        HashMap<String, char[]> users = new HashMap<String, char[]>() {{
            put(testUser, "E9V9PAVSGWQMBDFUFW9SZKV9SO9ATLTLCDTCKKRXZTSFKSCHBISFHZJPIGLP9DEIWYPHJUINIQWSRETQT".toCharArray());
        }};
        MapIdentityManager identityManager = new MapIdentityManager(users);
        Account account = identityManager.verify(testUser, new PasswordCredential(testPassword));

        assertEquals("testUser needs equal to user returned by account", testUser, account.getPrincipal().getName());
    }

    @Test
    public void verifyAccountWithUnsupportedCredentialType() {
        HashMap<String, char[]> users = new HashMap<String, char[]>() {{
            put(testUser, testPassword);
        }};
        MapIdentityManager identityManager = new MapIdentityManager(users);
        Account account = identityManager.verify(testUser, new X509CertificateCredential(null));
        assertNull("Must be null, because credential type is not supported", account);
    }
}
