package com.iota.iri.pluggables.utxo;


import com.google.gson.Gson;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import com.iota.iri.pluggables.utxo.*;


public class TransactionDataTest {
    private static TransactionData transactionData;


    @BeforeClass
    public static void setUp() throws Exception {
        transactionData = new TransactionData();
        transactionData.init();
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
        assert transactionData.transactions.get(1).outputs.get(1).amount == 9900;
        System.out.println(new Gson().toJson(transactionData.transactions.get(1)));


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
        assert transactionData.transactions.get(2).outputs.get(1).amount == 9700;
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

    @Test
    public void testGetBalance() {
        long balanceA = transactionData.getBalance("A");
        String txnStr = "{\"amnt\": 100, \"from\": \"A\", \"tag\": \"TX\", \"to\": \"b\"}";
        transactionData.readFromStr(txnStr);

        txnStr = "{\"amnt\": 100, \"from\": \"A\", \"tag\": \"TX\", \"to\": \"c\"}";
        transactionData.readFromStr(txnStr);

        txnStr = "{\"amnt\": 100, \"from\": \"A\", \"tag\": \"TX\", \"to\": \"d\"}";
        transactionData.readFromStr(txnStr);

        txnStr = "{\"amnt\": 100, \"from\": \"A\", \"tag\": \"TX\", \"to\": \"e\"}";
        transactionData.readFromStr(txnStr);

        txnStr = "{\"amnt\": 100, \"from\": \"A\", \"tag\": \"TX\", \"to\": \"f\"}";
        transactionData.readFromStr(txnStr);

        txnStr = "{\"amnt\": 100, \"from\": \"b\", \"tag\": \"TX\", \"to\": \"c\"}";
        transactionData.readFromStr(txnStr);

        txnStr = "{\"amnt\": 100, \"from\": \"c\", \"tag\": \"TX\", \"to\": \"f\"}";
        transactionData.readFromStr(txnStr);

        txnStr = "{\"amnt\": 10000, \"from\": \"d\", \"tag\": \"TX\", \"to\": \"e\"}";
        transactionData.readFromStr(txnStr);

        assert transactionData.getBalance("A") == balanceA - 100 - 100 - 100 - 100 - 100;
        assert transactionData.getBalance("b") == 0;
        assert transactionData.getBalance("c") == 100 + 100 - 100;
        assert transactionData.getBalance("d") == 100;
        assert transactionData.getBalance("e") == 100;
        assert transactionData.getBalance("f") == 100 + 100;
    }

}
