package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.network.Node;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by paul on 5/19/17.
 */
public class NodeIntegrationTests {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGetsSolid() throws Exception {
        int count = 2;
        Iota iotaNodes[] = new Iota[count];
        TemporaryFolder[] folders = new TemporaryFolder[count*2];
        for(int i = 0; i < count; i++) {
            folders[i*2] = new TemporaryFolder();
            folders[i*2 + 1] = new TemporaryFolder();
            iotaNodes[i] = newNode(i, folders[i*2], folders[i*2+1]);
        }
        Node.uri("udp://localhost:14701").ifPresent(uri -> iotaNodes[0].node.addNeighbor(iotaNodes[0].node.newNeighbor(uri, true)));
        Node.uri("udp://localhost:14700").ifPresent(uri -> iotaNodes[1].node.addNeighbor(iotaNodes[1].node.newNeighbor(uri, true)));

        Thread.sleep(1000);
        /*
        TODO: Put some test stuff here
         */

        for(Iota node: iotaNodes) {
            node.api.shutDown();
            node.shutdown();
        }
        for(TemporaryFolder folder: folders) {
            folder.delete();
        }
    }

    public Iota newNode(int index, TemporaryFolder db, TemporaryFolder log) throws Exception {
        db.create();
        log.create();
        Configuration conf = new Configuration();
        Iota iota;
        conf.put(Configuration.DefaultConfSettings.PORT, String.valueOf(14800 + index));
        conf.put(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT, String.valueOf(14700 + index));
        conf.put(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT, String.valueOf(14700 + index));
        conf.put(Configuration.DefaultConfSettings.DB_PATH, db.getRoot().getAbsolutePath());
        conf.put(Configuration.DefaultConfSettings.DB_LOG_PATH, log.getRoot().getAbsolutePath());
        conf.put(Configuration.DefaultConfSettings.TESTNET, "true");
        iota = new Iota(conf);
        iota.init();
        iota.api.init();
        return iota;
    }
}
