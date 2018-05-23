package com.iota.iri.integration;

import com.iota.iri.IXI;
import com.iota.iri.Iota;
import com.iota.iri.conf.Configuration;

import static com.iota.iri.controllers.TransactionViewModel.*;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Node;
import com.iota.iri.service.API;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.rules.TemporaryFolder;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Created by paul on 5/19/17.
 */
public class NodeIntegrationTests {

    final Object waitObj = new Object();
    AtomicBoolean shutdown = new AtomicBoolean(false);

    @Before
    public void setUp() throws Exception {
        shutdown.set(false);
    }

    @After
    public void tearDown() throws Exception {
    }


    //@Test
    public void testGetsSolid() throws Exception {
        int count = 1;
        long spacing = 5000;
        Iota iotaNodes[] = new Iota[count];
        API api[] = new API[count];
        IXI ixi[] = new IXI[count];
        Thread cooThread, master;
        TemporaryFolder[] folders = new TemporaryFolder[count*2];
        for(int i = 0; i < count; i++) {
            folders[i*2] = new TemporaryFolder();
            folders[i*2 + 1] = new TemporaryFolder();
            iotaNodes[i] = newNode(i, folders[i*2], folders[i*2+1]);
            ixi[i] = new IXI(iotaNodes[i]);
            ixi[i].init(iotaNodes[i].configuration.string(Configuration.DefaultConfSettings.IXI_DIR));
            api[i] = new API(iotaNodes[i], ixi[i]);
            api[i].init();
        }
        Node.uri("udp://localhost:14701").ifPresent(uri -> iotaNodes[0].node.addNeighbor(iotaNodes[0].node.newNeighbor(uri, true)));
        //Node.uri("udp://localhost:14700").ifPresent(uri -> iotaNodes[1].node.addNeighbor(iotaNodes[1].node.newNeighbor(uri, true)));

        cooThread = new Thread(spawnCoordinator(api[0], spacing), "Coordinator");
        master = new Thread(spawnMaster(), "master");
        /*
        TODO: Put some test stuff here
         */
        cooThread.start();
        master.start();

        synchronized (waitObj) {
            waitObj.wait();
        }
        for(int i = 0; i < count; i++) {
            ixi[i].shutdown();
            api[i].shutDown();
            iotaNodes[i].shutdown();
        }
        for(TemporaryFolder folder: folders) {
            folder.delete();
        }
    }

    private Iota newNode(int index, TemporaryFolder db, TemporaryFolder log) throws Exception {
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
        return iota;
    }

    private Runnable spawnMaster() {
        return () -> {
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            shutdown.set(true);
            synchronized (waitObj) {
                waitObj.notifyAll();
            }
        };
    }

    Runnable spawnCoordinator(API api, long spacing) {
        return () -> {
            long index = 0;
            try {
                newMilestone(api, Arrays.asList(Hash.NULL_HASH, Hash.NULL_HASH), index++);
            } catch (Exception e) {
                e.printStackTrace();
            }
            while(!shutdown.get()) {
                try {
                    Thread.sleep(spacing);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    sendMilestone(api, index++);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void sendMilestone(API api, long index) throws Exception {
        newMilestone(api, api.getTransactionToApproveStatement(Optional.empty(), 10), index);
    }

    private void newMilestone(API api, List<Hash> tips, long index) throws Exception {
        List<int[]> transactions = new ArrayList<>();
        transactions.add(new int[TRINARY_SIZE]);
        Converter.copyTrits(index, transactions.get(0), OBSOLETE_TAG_TRINARY_OFFSET, OBSOLETE_TAG_TRINARY_SIZE);
        transactions.add(Arrays.copyOf(transactions.get(0), TRINARY_SIZE));
        Hash coordinator = new Hash(Configuration.TESTNET_COORDINATOR_ADDRESS);
        System.arraycopy(coordinator.trits(), 0, transactions.get(0), ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE);
        setBundleHash(transactions, null);
        List<String> elements = api.attachToTangleStatement(tips.get(0), tips.get(0), 13, transactions.stream().map(Converter::trytes).collect(Collectors.toList()));
        api.storeTransactionStatement(elements);
        api.broadcastTransactionStatement(elements);
    }

    public void setBundleHash(List<int[]> transactions, Curl customCurl) {

        int[] hash = new int[Curl.HASH_LENGTH];

        Sponge curl = customCurl == null ? SpongeFactory.create(SpongeFactory.Mode.CURLP81) : customCurl;
        curl.reset();

        for (int i = 0; i < transactions.size(); i++) {
            int[] t = Arrays.copyOfRange(transactions.get(i), ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_OFFSET + ADDRESS_TRINARY_SIZE);

            int[] valueTrits = Arrays.copyOfRange(transactions.get(i), VALUE_TRINARY_OFFSET, VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE);
            t = ArrayUtils.addAll(t, valueTrits);

            int[] tagTrits = Arrays.copyOfRange(transactions.get(i), OBSOLETE_TAG_TRINARY_OFFSET, OBSOLETE_TAG_TRINARY_OFFSET + OBSOLETE_TAG_TRINARY_SIZE);
            t = ArrayUtils.addAll(t, tagTrits);

            int[] timestampTrits  = Arrays.copyOfRange(transactions.get(i), TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_OFFSET + TIMESTAMP_TRINARY_SIZE);
            t = ArrayUtils.addAll(t, timestampTrits);

            Converter.copyTrits(i, transactions.get(i), CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE);
            int[] currentIndexTrits = Arrays.copyOfRange(transactions.get(i), CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_OFFSET + CURRENT_INDEX_TRINARY_SIZE);
            t = ArrayUtils.addAll(t, currentIndexTrits);

            Converter.copyTrits(transactions.size(), transactions.get(i), LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE);
            int[] lastIndexTrits = Arrays.copyOfRange(transactions.get(i), LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_OFFSET + LAST_INDEX_TRINARY_SIZE);
            t = ArrayUtils.addAll(t, lastIndexTrits);

            curl.absorb(t, 0, t.length);
        }

        curl.squeeze(hash, 0, hash.length);

        for (int i = 0; i < transactions.size(); i++) {
            System.arraycopy(hash, 0, transactions.get(i), BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE);
        }
    }

}
