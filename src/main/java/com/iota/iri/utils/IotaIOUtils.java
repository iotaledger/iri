package com.iota.iri.utils;

import org.apache.commons.io.IOUtils;
import org.json.JSONTokener;
import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.iota.iri.conf.BaseIotaConfig;

import java.io.*;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import java.util.zip.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.iota.iri.controllers.*;
import com.iota.iri.pluggables.utxo.*;

import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;

public class IotaIOUtils extends IOUtils {

    private static final Logger log = LoggerFactory.getLogger(IotaIOUtils.class);

    public static void closeQuietly(AutoCloseable... autoCloseables) {
        for (AutoCloseable it : autoCloseables) {
            try {
                if (it != null) {
                    it.close();
                }
            } catch (Exception ignored) {
                log.debug("Silent exception occured", ignored);
            }
        }
    }

    public static String processBatchTxnMsg(final String message) {
        // decompression goes here
        String msgStr = message;
        if(BaseIotaConfig.getInstance().isEnableCompressionTxns()) {
            try {
                byte[] bytes = Converter.trytesToBytes(message);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                GZIPInputStream inStream = new GZIPInputStream(in);
                byte[] buffer = new byte[16384];
                int num = 0;
                while ((num = inStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, num);
                }
                byte[] unCompressed = out.toByteArray();
                msgStr = new String(unCompressed);
            } catch (IOException e) {
                log.error("Uncompressing error", e);
                return null;
            }
        }

        StringBuilder ret = new StringBuilder();
        int size = (int)TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE / 3;
        BatchTxns tmpBatch = new BatchTxns();

        // parse json here
        try {
            JSONObject jo = new JSONObject(msgStr);
            long txnCount = jo.getLong("tx_num");
            Object txnObj = jo.get("txn_content");

            Object json = new JSONTokener(txnObj.toString()).nextValue();
            if(json instanceof JSONObject){
                if (txnCount != 1) {
                    log.error("Wrong input - tx_num is {}, but txn_content have 1 item!", txnCount);
                    return null;
                }

                TransactionData.getInstance().readFromStr(txnObj.toString());
                Transaction tx = TransactionData.getInstance().getLast();
                tmpBatch.addTxn(tx);
                String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), size, '9');
                ret.append(s);
            }else if (json instanceof JSONArray) {
                JSONArray jsonArray = (JSONArray) json;
                if (jsonArray.length() != txnCount) {
                    log.error("Wrong input - tx_num is {}, but txn_content have {} items!", txnCount, jsonArray.length());
                    return null;
                }

                for (Object object : jsonArray) {
                    TransactionData.getInstance().readFromStr(object.toString());
                    Transaction tx = TransactionData.getInstance().getLast();
                    if (tmpBatch.getTryteStringLen(tmpBatch) + tx.getTryteStringLen(tx) > size) {
                        String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), size, '9');
                        ret.append(s);
                        tmpBatch.clear();
                    }
                    tmpBatch.addTxn(tx);
                }
                if(tmpBatch.tx_num > 0) {
                    String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), size, '9');
                    ret.append(s);
                    tmpBatch.clear();
                }
            } else {
                log.error("Neither JSONObject nor JSONArray!!!");
                return null;
            }

            return ret.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
