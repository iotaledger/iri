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

import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.Hash;

import com.google.gson.Gson;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

                List<Txn> list = TransactionData.getInstance().readFromStr(txnObj.toString());
                tmpBatch.addTxn(list.get(0));
                String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), size, '9');
                ret.append(s);
                TransactionData.getInstance().addTxn(list.get(0));
            }else if (json instanceof JSONArray) {
                JSONArray jsonArray = (JSONArray) json;
                if (jsonArray.length() != txnCount) {
                    log.error("Wrong input - tx_num is {}, but txn_content have {} items!", txnCount, jsonArray.length());
                    return null;
                }

                for (Object object : jsonArray) {
                    List<Txn> list =  TransactionData.getInstance().readFromStr(object.toString());
                    Txn tx = list.get(0);
                    if (tmpBatch.getTryteStringLen(tmpBatch) + tx.getTryteStringLen(tx) > size) {
                        String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), size, '9');
                        ret.append(s);
                        TransactionData.getInstance().createTmpStorageForBlock(tmpBatch);
                        tmpBatch.clear();
                    }
                    tmpBatch.addTxn(tx);
                    TransactionData.getInstance().addTxn(tx);
                }
                if(tmpBatch.tx_num > 0) {
                    String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), size, '9');
                    ret.append(s);
                    TransactionData.getInstance().createTmpStorageForBlock(tmpBatch);
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

    public static byte [] processTxnTrytes(byte [] trits) {
        byte [] ret = trits;
        TransactionViewModel model = new TransactionViewModel(trits, 
            TransactionHash.calculate(trits, TransactionViewModel.TRINARY_SIZE, SpongeFactory.create(SpongeFactory.Mode.CURLP81)));
        // check tag, if it's dag UTXO transaction, do accoridng process
        try {
            Hash tag = model.getTagValue();
            String tagStr = Converter.trytesToAscii(Converter.trytes(tag.trits()));
            String type = tagStr.substring(8, 10);
            if(type.equals("TX") && !BaseIotaConfig.getInstance().isEnableIPFSTxns()) {
                String sig = Converter.trytes(model.getSignature());
                String txnsStr = Converter.trytesToAscii(sig);
                if(!txnsStr.contains("inputs") && !txnsStr.contains("outputs")) { // check if already been processed
                    BatchTxns tmpBatch = new BatchTxns();
                    int sigSize = TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET/3;
                    JSONObject jo = new JSONObject(txnsStr);
                    txnsStr = jo.get("txn_content").toString();
                    List<Txn> tx = TransactionData.getInstance().readFromStr(txnsStr);
                    tmpBatch.addTxn(tx.get(0));

                    String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), sigSize, '9');
                    byte[] sigTrits = new byte[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE];
                    Converter.trits(s, sigTrits, 0);
                    System.arraycopy(sigTrits, 0, ret, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
                }
            }
            return ret;
        } catch(IllegalArgumentException e) {
            return ret;
        } catch(Exception e) {
            e.printStackTrace();
            return ret;
        }
    }

    public static void processReceivedTxn(TransactionViewModel model) {
        try {
            Hash tag = model.getTagValue();
            String tagStr = Converter.trytesToAscii(Converter.trytes(tag.trits()));
            String type = tagStr.substring(8, 10);
            if(type.equals("TX") && !BaseIotaConfig.getInstance().isEnableIPFSTxns()) {
                byte[] sigTrits = model.getSignature();
                String sigTrytes = Converter.trytes(sigTrits);
                String txnInfo = Converter.trytesToAscii(sigTrytes);
                Pattern pattern = Pattern.compile("\\{.*\\}");
                Matcher matcher = pattern.matcher(txnInfo);
                if (matcher.find()) {
                    String info = matcher.group(0);
                    BatchTxns tx = new Gson().fromJson(info, BatchTxns.class);
                    for(Txn txn : tx.txn_content) {
                        TransactionData.getInstance().addTxn(txn);
                        TransactionData.getInstance().putIndex(txn, model.getHash());
                    }
                }
            }
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
