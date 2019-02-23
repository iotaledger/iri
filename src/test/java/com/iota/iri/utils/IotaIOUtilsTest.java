package com.iota.iri.utils;

import org.junit.Assert;
import org.junit.Test;
import java.util.*;

import com.iota.iri.controllers.*;

public class IotaIOUtilsTest {

    @Test
    public void testProcessBatchTxnMsg() {
        final int txMessageSize = (int) TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE / 3;
        String mesgs = "{ \"tx_num\" : 31, \"txn_content\" :" +
                       "[{\"from\" : \"A\", \"to\":\"LDW9UG4R44\", \"amnt\":100}," +
                        "{\"from\" : \"LDW9UG4R44\", \"to\":\"5MYL6I1RIM\", \"amnt\":73}," +
                        "{\"from\" : \"A\", \"to\":\"GAT2OZRSPQ\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"APBJPVHP6Q\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"K0LECTTNPZ\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"PLDUMJU56P\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"RNVJEKPT46\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"0A2MPT578I\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"8MU3GMG012\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"4JAWQ23CFD\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"DNYTKJLCYF\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"FXI6G5TJF3\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"MARDC3C3JJ\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"MJ1WN4I7PU\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"MCL7PYLWKX\", \"amnt\":100}," +
                        "{\"from\" : \"A\", \"to\":\"OYBBL9LU5L\", \"amnt\":100}," +
                        "{\"from\" : \"OYBBL9LU5L\", \"to\":\"ETOU683VBT\", \"amnt\":11}," +
                        "{\"from\" : \"MCL7PYLWKX\", \"to\":\"90FWHN29KL\", \"amnt\":10}," +
                        "{\"from\" : \"MJ1WN4I7PU\", \"to\":\"GCCOFFA5FJ\", \"amnt\":65}," +
                        "{\"from\" : \"MARDC3C3JJ\", \"to\":\"6V4VUXP63Q\", \"amnt\":22}," +
                        "{\"from\" : \"FXI6G5TJF3\", \"to\":\"TQGRA75975\", \"amnt\":42}," +
                        "{\"from\" : \"DNYTKJLCYF\", \"to\":\"270XR1BWBC\", \"amnt\":89}," +
                        "{\"from\" : \"4JAWQ23CFD\", \"to\":\"31LSEMP8G1\", \"amnt\":76}," +
                        "{\"from\" : \"8MU3GMG012\", \"to\":\"J3KBS9CNR4\", \"amnt\":26}," +
                        "{\"from\" : \"0A2MPT578I\", \"to\":\"BB857HZO7M\", \"amnt\":19}," +
                        "{\"from\" : \"PLDUMJU56P\", \"to\":\"63AVM60GUB\", \"amnt\":94}," +
                        "{\"from\" : \"A\", \"to\":\"Y7ZSGDZDVF\", \"amnt\":5}," +
                        "{\"from\" : \"A\", \"to\":\"8RLXG3JWPZ\", \"amnt\":68}," +
                        "{\"from\" : \"A\", \"to\":\"ER0HSUXF15\", \"amnt\":78}," +
                        "{\"from\" : \"A\", \"to\":\"IFXESB5QOR\", \"amnt\":87}," +
                        "{\"from\" : \"GAT2OZRSPQ\", \"to\":\"K45QDHQYSR\", \"amnt\":15}] }";

        String ret = IotaIOUtils.processBatchTxnMsg(mesgs);
        Assert.assertEquals(ret.length()/txMessageSize, 11);
    }
}
