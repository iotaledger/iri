package com.iota.iri.utils;

import org.apache.commons.io.IOUtils;
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

    public static List<String> processBatchTxnMsg(final String message) {
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

        List<String> ret = new ArrayList<>();

        // parse json here
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(msgStr);
            JsonNode numNode = rootNode.path("tx_num");
            JsonNode txsNode = rootNode.path("txn_content");
            long txnCount = numNode.asLong();

            int size = TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE / 3;
            if(txsNode.isArray()) {
                BatchTxns tmpBatch = new BatchTxns();
                for (final JsonNode txn : txsNode) {
                    TransactionData.getInstance().readFromStr(txn.toString());
                    tmpBatch.addTxn(TransactionData.getInstance().getLast());
                    if(tmpBatch.getTryteStringLen(tmpBatch) > size) {
                        String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), size, '9');
                        ret.add(s);
                        tmpBatch.clear();
                    }
                }
                if(tmpBatch.tx_num > 0) {
                    String s = StringUtils.rightPad(tmpBatch.getTryteString(tmpBatch), size, '9');
                    ret.add(s);
                    tmpBatch.clear();
                }
            }

            return ret;
        } catch (IOException e) {
            log.error("Parse json error", e);
            return null;
        }
    }

    public static List<String> processNornalMsg(String address, String msg) {
        final int txMessageSize = (int) TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE / 3;
        final int txCount = (int) (msg.length() + txMessageSize - 1) / txMessageSize;

        final byte[] timestampTrits = new byte[TransactionViewModel.TIMESTAMP_TRINARY_SIZE];
        Converter.copyTrits(System.currentTimeMillis(), timestampTrits, 0, timestampTrits.length);
        final String timestampTrytes = StringUtils.rightPad(Converter.trytes(timestampTrits), timestampTrits.length / 3, '9');

        final byte[] lastIndexTrits = new byte[TransactionViewModel.LAST_INDEX_TRINARY_SIZE];
        byte[] currentIndexTrits = new byte[TransactionViewModel.CURRENT_INDEX_TRINARY_SIZE];

        Converter.copyTrits(txCount - 1, lastIndexTrits, 0, lastIndexTrits.length);
        final String lastIndexTrytes = Converter.trytes(lastIndexTrits);

        List<String> transactions = new ArrayList<>();
        for (int i = 0; i < txCount; i++) {
            String tx;
            if (i != txCount - 1) {
                tx = msg.substring(i * txMessageSize, (i + 1) * txMessageSize);
            } else {
                tx = msg.substring(i * txMessageSize);
            }

            Converter.copyTrits(i, currentIndexTrits, 0, currentIndexTrits.length);

            tx = StringUtils.rightPad(tx, txMessageSize, '9');
            tx += address.substring(0, 81);
            // value
            tx += StringUtils.repeat('9', 27);
            // obsolete tag
            tx += StringUtils.repeat('9', 27);
            // timestamp
            tx += timestampTrytes;
            // current index
            tx += StringUtils.rightPad(Converter.trytes(currentIndexTrits), currentIndexTrits.length / 3, '9');
            // last index
            tx += StringUtils.rightPad(lastIndexTrytes, lastIndexTrits.length / 3, '9');
            transactions.add(tx);
        }

        return transactions;
    }
}
