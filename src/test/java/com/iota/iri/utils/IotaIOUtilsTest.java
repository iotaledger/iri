package com.iota.iri.utils;

import org.junit.Assert;
import org.junit.Test;
import java.util.*;

public class IotaIOUtilsTest {

    @Test
    public void testProcessBatchTxnMsg() {
        String mesgs = "{ \"tx_num\" : 30, \"txn_content\" :" +
                       "[{\"from\" : \"LDW9UG4R44\", \"to\":\"5MYL6I1RIM\", \"amount\":73}," +
                        "{\"from\" : \"LF2AFINFBA\", \"to\":\"GAT2OZRSPQ\", \"amount\":33}," +
                        "{\"from\" : \"4ZJY1BI8IH\", \"to\":\"APBJPVHP6Q\", \"amount\":29}," +
                        "{\"from\" : \"4XMTLUP8AN\", \"to\":\"K0LECTTNPZ\", \"amount\":6}," +
                        "{\"from\" : \"ASX3UHBSPB\", \"to\":\"PLDUMJU56P\", \"amount\":49}," +
                        "{\"from\" : \"J511WQF8MY\", \"to\":\"RNVJEKPT46\", \"amount\":61}," +
                        "{\"from\" : \"E9MO10ADAS\", \"to\":\"0A2MPT578I\", \"amount\":14}," +
                        "{\"from\" : \"5TKNOBT6KG\", \"to\":\"8MU3GMG012\", \"amount\":11}," +
                        "{\"from\" : \"LHFG8NTJW8\", \"to\":\"4JAWQ23CFD\", \"amount\":7}," +
                        "{\"from\" : \"VS18QQ1QHX\", \"to\":\"DNYTKJLCYF\", \"amount\":8}," +
                        "{\"from\" : \"MLH83LMT1U\", \"to\":\"FXI6G5TJF3\", \"amount\":79}," +
                        "{\"from\" : \"XJG5P6AMJT\", \"to\":\"MARDC3C3JJ\", \"amount\":62}," +
                        "{\"from\" : \"H4WFMMMB5E\", \"to\":\"MJ1WN4I7PU\", \"amount\":74}," +
                        "{\"from\" : \"4NIU27AOTQ\", \"to\":\"MCL7PYLWKX\", \"amount\":23}," +
                        "{\"from\" : \"NA41B70BHA\", \"to\":\"OYBBL9LU5L\", \"amount\":1}," +
                        "{\"from\" : \"L14J0V2IE8\", \"to\":\"ETOU683VBT\", \"amount\":11}," +
                        "{\"from\" : \"EZTXTFDGEC\", \"to\":\"90FWHN29KL\", \"amount\":10}," +
                        "{\"from\" : \"P654FOKKY6\", \"to\":\"GCCOFFA5FJ\", \"amount\":65}," +
                        "{\"from\" : \"EEOV9QEAVY\", \"to\":\"6V4VUXP63Q\", \"amount\":22}," +
                        "{\"from\" : \"OIGU0TTN18\", \"to\":\"TQGRA75975\", \"amount\":42}," +
                        "{\"from\" : \"DJQAEKLA0B\", \"to\":\"270XR1BWBC\", \"amount\":89}," +
                        "{\"from\" : \"5VR0UXCFNG\", \"to\":\"31LSEMP8G1\", \"amount\":76}," +
                        "{\"from\" : \"7BU6QZWOCK\", \"to\":\"J3KBS9CNR4\", \"amount\":26}," +
                        "{\"from\" : \"PZ6BFDZCU2\", \"to\":\"BB857HZO7M\", \"amount\":19}," +
                        "{\"from\" : \"ZMA323546A\", \"to\":\"63AVM60GUB\", \"amount\":94}," +
                        "{\"from\" : \"M32XAX46J8\", \"to\":\"Y7ZSGDZDVF\", \"amount\":5}," +
                        "{\"from\" : \"1S48QAKQ75\", \"to\":\"8RLXG3JWPZ\", \"amount\":68}," +
                        "{\"from\" : \"OFU1MFUHQA\", \"to\":\"ER0HSUXF15\", \"amount\":78}," +
                        "{\"from\" : \"C8S6T7L1G5\", \"to\":\"IFXESB5QOR\", \"amount\":87}," +
                        "{\"from\" : \"JK641IE0BH\", \"to\":\"K45QDHQYSR\", \"amount\":15}] }";

        List<String> ret = IotaIOUtils.processBatchTxnMsg(mesgs);
        Assert.assertEquals(ret.size(), 2);
    }
}
