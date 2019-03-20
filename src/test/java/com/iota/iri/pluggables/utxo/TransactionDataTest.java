package com.iota.iri.pluggables.utxo;


import com.google.gson.Gson;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.iota.iri.controllers.TransactionViewModel.TRYTES_SIZE;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;


public class TransactionDataTest {
    private static TransactionData transactionData;
    private static Tangle tangle = new Tangle();

    @BeforeClass
    public static void setUp() throws Exception {
        final TemporaryFolder dbFolder = new TemporaryFolder();
        final TemporaryFolder logFolder = new TemporaryFolder();
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider=  new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.addPersistenceProvider(new LocalInMemoryGraphProvider("", tangle));
        tangle.init();

        transactionData = new TransactionData();
        transactionData.init();
        transactionData.setTangle(tangle);
    }

    @Test
    public void testInitTransaction() {
        assert transactionData.transactions.get(0).outputs.size() == 1;
        assert transactionData.transactions.get(0).inputs == null;
        System.out.println(new Gson().toJson(transactionData.transactions.get(0)));
    }

    @Test
    public void testReadFromStr(){

        transactionData.readFromStr("{\"from\":\"A\",\"to\":\"B\",\"amnt\":100}");
        assert transactionData.transactions.size() == 2;
        assert transactionData.transactions.get(1).inputs.size() == 1;
        assert transactionData.transactions.get(1).inputs.get(0).txnHash.equals(
                transactionData.transactions.get(0).txnHash
        );
        assert transactionData.transactions.get(1).inputs.get(0).idx == 0;
        assert transactionData.transactions.get(1).inputs.get(0).userAccount.equals("A");
        assert transactionData.transactions.get(1).outputs.size() == 2;
        assert transactionData.transactions.get(1).outputs.get(0).userAccount.equals("B");
        assert transactionData.transactions.get(1).outputs.get(0).amount == 100;
        assert transactionData.transactions.get(1).outputs.get(1).userAccount.equals("A");
        assert transactionData.transactions.get(1).outputs.get(1).amount == 999999900;


        transactionData.readFromStr("{\"from\":\"A\",\"to\":\"B\",\"amnt\":200}");
        assert transactionData.transactions.size() == 3;
        assert transactionData.transactions.get(2).inputs.size() == 1;
        assert transactionData.transactions.get(2).inputs.get(0).txnHash.equals(
                transactionData.transactions.get(1).txnHash
        );
        assert transactionData.transactions.get(2).inputs.get(0).idx == 1;
        assert transactionData.transactions.get(2).inputs.get(0).userAccount.equals("A");
        assert transactionData.transactions.get(2).outputs.size() == 2;
        assert transactionData.transactions.get(2).outputs.get(0).userAccount.equals("B");
        assert transactionData.transactions.get(2).outputs.get(0).amount == 200;
        assert transactionData.transactions.get(2).outputs.get(1).userAccount.equals("A");
        assert transactionData.transactions.get(2).outputs.get(1).amount == 999999700;
        System.out.println(new Gson().toJson(transactionData.transactions.get(2)));


        transactionData.readFromStr("{\"from\":\"B\",\"to\":\"C\",\"amnt\":300}");
        assert transactionData.transactions.size() == 4;
        assert transactionData.transactions.get(3).inputs.size() == 2;
        assert transactionData.transactions.get(3).inputs.get(0).txnHash.equals(
                transactionData.transactions.get(2).txnHash
        );
        assert transactionData.transactions.get(3).inputs.get(0).idx == 0;
        assert transactionData.transactions.get(3).inputs.get(0).userAccount.equals("B");
        assert transactionData.transactions.get(3).inputs.get(1).txnHash.equals(
                transactionData.transactions.get(1).txnHash
        );
        assert transactionData.transactions.get(3).inputs.get(1).idx == 0;
        assert transactionData.transactions.get(3).inputs.get(1).userAccount.equals("B");
        assert transactionData.transactions.get(3).outputs.size() == 1;
        assert transactionData.transactions.get(3).outputs.get(0).userAccount.equals("C");
        assert transactionData.transactions.get(3).outputs.get(0).amount == 300;
        System.out.println(new Gson().toJson(transactionData.transactions.get(3)));

        transactionData.readFromStr("{\"from\":\"B\",\"to\":\"C\",\"amnt\":1}");
        assert transactionData.transactions.size() == 4;

    }

    private static void store(String txnStr) {
        try {
            transactionData.readFromStr(txnStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Txn tx = transactionData.getLast();
        System.out.println("tx: " + tx.toString());
        BatchTxns tmpBatch = new BatchTxns();
        tmpBatch.addTxn(tx);
        String trytes = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE / 3, '9');
        byte[] txTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);
        Converter.trits(trytes, txTrits, 0);
        TransactionViewModel v = new TransactionViewModel(txTrits, getRandomTransactionHash());

        try {
            v.store(tangle);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // TODO: add more transference after bugs fixed.
    @Test
    public void testGetBalance() {
        String txnStr = "{\"amnt\": 100, \"from\": \"A\", \"tag\": \"TX\", \"to\": \"b\"}";
        store(txnStr);

        assert (transactionData.getBalance("b") == 100);
    }

}
