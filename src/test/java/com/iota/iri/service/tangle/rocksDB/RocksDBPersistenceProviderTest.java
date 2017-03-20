package com.iota.iri.service.tangle.rocksDB;

import com.iota.iri.model.Flag;
import com.iota.iri.service.tangle.Tangle;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class RocksDBPersistenceProviderTest {

    @BeforeClass
    public static void setUp() throws Exception {
        TemporaryFolder dbFolder = new TemporaryFolder();
        dbFolder.create();
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init(dbFolder.getRoot().getAbsolutePath());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Tangle.instance().shutdown();
    }



    @Test
    public void setTransientHandle() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        assertNotNull(UUID.class.equals(uuid.getClass()));
    }

    @Test
    public void dropTransientHandle() throws Exception {
    }

    @Test
    public void transientSave() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        Flag flag = new Flag();
        flag.hash = "SOMESTRINGOROTHER".getBytes();
        try {
            Tangle.instance().save(uuid, flag).get();
            assertTrue("Got this far", true);
        } catch (Exception e) {
            assertTrue("Failed to save", false);
        }

    }

    @Test
    public void maybeHas() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        Flag flag = new Flag();
        flag.hash = "SOMESTRINGOROTHER".getBytes();
        Tangle.instance().save(uuid, flag).get();
        boolean maybeHas = Tangle.instance().maybeHas(uuid, "9OBADBYTES".getBytes()).get();
        assertFalse("Should not contain junk", maybeHas);
        maybeHas = Tangle.instance().maybeHas(uuid, flag.hash).get();
        assertTrue("Should have it in DB", maybeHas);
    }

    @Test
    public void deleteTransientObject() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        Flag flag = new Flag();
        flag.hash = "SOMESTRINGOROTHER".getBytes();
        Tangle.instance().save(uuid, flag).get();
        assertTrue(Tangle.instance().maybeHas(uuid, flag.hash).get());
        Tangle.instance().delete(uuid, flag.hash).get();
        assertFalse(Tangle.instance().maybeHas(uuid, flag.hash).get());
    }
}