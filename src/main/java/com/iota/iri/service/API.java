package com.iota.iri.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executors;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.IRI;
import com.iota.iri.Milestone;
import com.iota.iri.Neighbor;
import com.iota.iri.Snapshot;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.PearlDiver;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;

public class API {

	private static final Logger log = LoggerFactory.getLogger(API.class);

    public static final int PORT = 14265;

    static ScriptEngine scriptEngine;
    static AsynchronousServerSocketChannel serverChannel;
    static final PearlDiver pearlDiver = new PearlDiver();

    public static void launch() throws IOException {

        scriptEngine = (new ScriptEngineManager()).getEngineByName("JavaScript");

        serverChannel = AsynchronousServerSocketChannel.open(AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool()));
        serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT));
        serverChannel.accept(null, handler);
    }

    static String array(final List<String> elements) {

        final StringBuilder array = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {

            if (i > 0) {

                array.append(", ");
            }

            array.append(elements.get(i));
        }

        return "[" + array + "]";
    }

    static class Request {

        static final int READING_BUFFER_SIZE = 4096;

        final AsynchronousSocketChannel channel;
        ByteBuffer buffer;
        final ByteArrayOutputStream accumulatedData;

        Request(final AsynchronousSocketChannel channel) {

            this.channel = channel;
            buffer = ByteBuffer.allocateDirect(READING_BUFFER_SIZE);
            accumulatedData = new ByteArrayOutputStream();

            channel.read(buffer, this, new CompletionHandler<Integer, Request>() {

                @Override
                public void completed(final Integer numberOfBytes, final Request request) {

                    try {

                        if (numberOfBytes >= 0) {

                            buffer.flip();
                            final byte[] bufferBytes = new byte[buffer.remaining()];
                            buffer.get(bufferBytes);
                            accumulatedData.write(bufferBytes, 0, bufferBytes.length);
                            final String requestString = accumulatedData.toString();

                            final int crlfcrlfOffset = requestString.indexOf("\r\n\r\n");
                            if (crlfcrlfOffset >= 0) {

                                final int contentLengthOffset = requestString.indexOf("Content-Length:");
                                if (contentLengthOffset >= 0) {

                                    final int contentLengthCRLFOffset = requestString.indexOf("\r\n", contentLengthOffset);
                                    if (contentLengthCRLFOffset >= 0) {

                                        final String body = requestString.substring(crlfcrlfOffset + 4);
                                        final int contentLengthValue = Integer.parseInt(requestString.substring(contentLengthOffset + 15, contentLengthCRLFOffset).trim());
                                        if (body.length() == contentLengthValue) {

                                            process(body);

                                            return;
                                        }
                                    }
                                }
                            }

                            buffer.clear();
                            channel.read(buffer, request, this);

                        } else {

                            channel.close();
                        }

                    } catch (final Exception e) {
                        e.printStackTrace();
                    } finally {
                    	IOUtils.closeQuietly(channel);
                    }
                }

                @Override
                public void failed(final Throwable e, final Request request) {
                    e.printStackTrace();
                    IOUtils.closeQuietly(channel);
                }
            });
        }

        void process(final String requestString) throws UnsupportedEncodingException {

            String response;
            final long beginningTime = System.currentTimeMillis();

            try {

                final Map<String, Object> request = (Map<String, Object>)scriptEngine.eval("Java.asJSONCompatible(" + requestString + ");");

                final String command = (String)request.get("command");
                if (command == null) {

                    response = "\"error\": \"'command' parameter has not been specified\"";

                } else {

                    switch (command) {

                        case "addNeighbors": {

                            int numberOfAddedNeighbors = 0;
                            for (final String uriString : (List<String>)request.get("uris")) {

                                final URI uri = new URI(uriString);
                                if (uri.getScheme() != null && uri.getScheme().equals("udp")) {

                                    final Neighbor neighbor = new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort()));
                                    if (!Node.neighbors.contains(neighbor)) {

                                        Node.neighbors.add(neighbor);
                                        numberOfAddedNeighbors++;
                                    }
                                }
                            }

                            response = "{\"addedNeighbors\": " + numberOfAddedNeighbors;

                        } break;

                        case "attachToTangle": {

                            final Hash trunkTransaction = new Hash((String)request.get("trunkTransaction"));
                            final Hash branchTransaction = new Hash((String)request.get("branchTransaction"));
                            final int minWeightMagnitude = (Integer)request.get("minWeightMagnitude");

                            final List<Transaction> transactions = new LinkedList<>();

                            Hash prevTransaction = null;
                            final List<String> trytes = (List<String>)request.get("trytes");
                            for (int i = 0; i < trytes.size(); i++) {

                                final int[] transactionTrits = Converter.trits(trytes.get(i));
                                System.arraycopy((prevTransaction == null ? trunkTransaction : prevTransaction).trits(), 0, transactionTrits, Transaction.TRUNK_TRANSACTION_TRINARY_OFFSET, Transaction.TRUNK_TRANSACTION_TRINARY_SIZE);
                                System.arraycopy((prevTransaction == null ? branchTransaction : trunkTransaction).trits(), 0, transactionTrits, Transaction.BRANCH_TRANSACTION_TRINARY_OFFSET, Transaction.BRANCH_TRANSACTION_TRINARY_SIZE);

                                if (pearlDiver.search(transactionTrits, minWeightMagnitude, 0)) {

                                    transactions.clear();

                                    break;
                                }

                                final Transaction transaction = new Transaction(transactionTrits);
                                transactions.add(transaction);
                                prevTransaction = new Hash(transaction.hash, 0, Transaction.HASH_SIZE);
                            }

                            final List<String> elements = new LinkedList<>();
                            for (int i = transactions.size(); i-- > 0; ) {

                                elements.add("\"" + Converter.trytes(transactions.get(i).trits()) + "\"");
                            }

                            response = "\"trytes\": " + array(elements);

                        } break;

                        case "broadcastTransactions": {

                            for (final String trytes : (List<String>)request.get("trytes")) {

                                final Transaction transaction = new Transaction(Converter.trits(trytes));
                                transaction.weightMagnitude = Curl.HASH_LENGTH;
                                Node.broadcast(transaction);
                            }

                            response = "";

                        } break;

                        case "findTransactions": {

                            final Set<Long> bundlesTransactions;
                            if (request.containsKey("bundles")) {

                                bundlesTransactions = new HashSet<>();
                                for (final String bundle : (List<String>)request.get("bundles")) {

                                    bundlesTransactions.addAll(Storage.bundleTransactions(Storage.bundlePointer((new Hash(bundle)).bytes())));
                                }

                            } else {

                                bundlesTransactions = null;
                            }

                            final Set<Long> addressesTransactions;
                            if (request.containsKey("addresses")) {

                                addressesTransactions = new HashSet<>();
                                for (final String address : (List<String>)request.get("addresses")) {

                                    addressesTransactions.addAll(Storage.addressTransactions(Storage.addressPointer((new Hash(address)).bytes())));
                                }

                            } else {

                                addressesTransactions = null;
                            }

                            final Set<Long> tagsTransactions;
                            if (request.containsKey("tags")) {

                                tagsTransactions = new HashSet<>();
                                for (String tag : (List<String>)request.get("tags")) {

                                    while (tag.length() < Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE) {

                                        tag += Converter.TRYTE_ALPHABET.charAt(0);
                                    }
                                    tagsTransactions.addAll(Storage.tagTransactions(Storage.tagPointer((new Hash(tag)).bytes())));
                                }

                            } else {

                                tagsTransactions = null;
                            }

                            final Set<Long> approveeTransactions;
                            if (request.containsKey("approvees")) {

                                approveeTransactions = new HashSet<>();
                                for (final String approvee : (List<String>)request.get("approvees")) {

                                    approveeTransactions.addAll(Storage.approveeTransactions(Storage.approveePointer((new Hash(approvee)).bytes())));
                                }

                            } else {

                                approveeTransactions = null;
                            }

                            final Set<Long> foundTransactions = bundlesTransactions == null ? (addressesTransactions == null ? (tagsTransactions == null ? (approveeTransactions == null ? new HashSet<>() : approveeTransactions) : tagsTransactions) : addressesTransactions) : bundlesTransactions;
                            if (addressesTransactions != null) {

                                foundTransactions.retainAll(addressesTransactions);
                            }
                            if (tagsTransactions != null) {

                                foundTransactions.retainAll(tagsTransactions);
                            }
                            if (approveeTransactions != null) {

                                foundTransactions.retainAll(approveeTransactions);
                            }

                            final List<String> elements = new LinkedList<>();
                            for (final long pointer : foundTransactions) {

                                elements.add("\"" + new Hash(Storage.loadTransaction(pointer).hash, 0, Transaction.HASH_SIZE) + "\"");
                            }

                            response = "\"hashes\": " + array(elements);

                        } break;

                        case "getBalances": {

                            final List<Hash> addresses = new LinkedList<>();
                            for (final String address : (List<String>)request.get("addresses")) {

                                addresses.add((new Hash(address)));
                            }

                            final int threshold = (Integer)request.get("threshold");
                            if (threshold <= 0 || threshold > 100) {

                                response = "\"error\": \"Illegal 'threshold'\"";

                                break;
                            }

                            final Map<Hash, Long> balances = new HashMap<>();
                            for (final Hash address : addresses) {

                                balances.put(address, Snapshot.initialState.containsKey(address) ? Snapshot.initialState.get(address) : Long.valueOf(0));
                            }

                            final Hash milestone = Milestone.latestSolidSubtangleMilestone;
                            final int milestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;

                            synchronized (Storage.analyzedTransactionsFlags) {

                                Storage.clearAnalyzedTransactionsFlags();

                                final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(Storage.transactionPointer(milestone.bytes())));
                                Long pointer;
                                while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                                    if (Storage.setAnalyzedTransactionFlag(pointer)) {

                                        final Transaction transaction = Storage.loadTransaction(pointer);

                                        if (transaction.value != 0) {

                                            final Hash address = new Hash(transaction.address, 0, Transaction.ADDRESS_SIZE);
                                            final Long balance = balances.get(address);
                                            if (balance != null) {

                                                balances.put(address, balance + transaction.value);
                                            }
                                        }

                                        nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                                        nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                                    }
                                }
                            }

                            final List<String> elements = new LinkedList<>();
                            for (final Hash address : addresses) {

                                elements.add("\"" + balances.get(address) + "\"");
                            }

                            response = "\"balances\": " + array(elements) + ", \"milestone\": \"" + milestone + "\", \"milestoneIndex\": " + milestoneIndex;

                        } break;

                        case "getInclusionStates": {

                            final List<Hash> transactions = new LinkedList<>();
                            for (final String transaction : (List<String>)request.get("transactions")) {

                                transactions.add((new Hash(transaction)));
                            }

                            final List<Hash> tips = new LinkedList<>();
                            for (final String tip : (List<String>)request.get("tips")) {

                                tips.add(new Hash(tip));
                            }

                            int numberOfNonMetTransactions = transactions.size();
                            final boolean[] inclusionStates = new boolean[numberOfNonMetTransactions];

                            synchronized (Storage.analyzedTransactionsFlags) {

                                Storage.clearAnalyzedTransactionsFlags();

                                response = null;
                                final Queue<Long> nonAnalyzedTransactions = new LinkedList<>();
                                for (final Hash tip : tips) {

                                    final long pointer = Storage.transactionPointer(tip.bytes());
                                    if (pointer <= 0) {

                                        response = "\"error\": \"One of the tips absents\"";

                                        break;
                                    }
                                    nonAnalyzedTransactions.offer(pointer);
                                }

                                if (response == null) {

                                    Long pointer;
                                MAIN_LOOP:
                                    while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                                        if (Storage.setAnalyzedTransactionFlag(pointer)) {

                                            final Transaction transaction = Storage.loadTransaction(pointer);
                                            if (transaction.type == Storage.PREFILLED_SLOT) {

                                                response = "\"error\": \"The subtangle is not solid\"";

                                                break;

                                            } else {

                                                final Hash transactionHash = new Hash(transaction.hash, 0, Transaction.HASH_SIZE);
                                                for (int i = 0; i < inclusionStates.length; i++) {

                                                    if (!inclusionStates[i] && transactionHash.equals(transactions.get(i))) {

                                                        inclusionStates[i] = true;

                                                        if (--numberOfNonMetTransactions <= 0) {
                                                            break MAIN_LOOP;
                                                        }
                                                    }
                                                }

                                                nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                                                nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                                            }
                                        }
                                    }

                                    if (response == null) {
                                        response = "\"states\": " + Arrays.toString(inclusionStates);
                                    }
                                }
                            }

                        } break;

                        case "getNeighbors": {

                            final List<String> elements = new LinkedList<>();
                            for (final Neighbor neighbor : Node.neighbors) {

                                elements.add("{" + neighbor + "}");
                            }

                            response = "\"neighbors\": " + array(elements);

                        } break;

                        case "getNodeInfo": {

                            response = "\"appName\": \"" + IRI.NAME + "\""
                                    + ", \"appVersion\": \"" + IRI.VERSION + "\""
                                    + ", \"jreAvailableProcessors\": " + Runtime.getRuntime().availableProcessors()
                                    + ", \"jreFreeMemory\": " + Runtime.getRuntime().freeMemory()
                                    + ", \"jreMaxMemory\": " + Runtime.getRuntime().maxMemory()
                                    + ", \"jreTotalMemory\": " + Runtime.getRuntime().totalMemory()
                                    + ", \"latestMilestone\": \"" + Milestone.latestMilestone + "\""
                                    + ", \"latestMilestoneIndex\": " + Milestone.latestMilestoneIndex
                                    + ", \"latestSolidSubtangleMilestone\": \"" + Milestone.latestSolidSubtangleMilestone + "\""
                                    + ", \"latestSolidSubtangleMilestoneIndex\": " + Milestone.latestSolidSubtangleMilestoneIndex
                                    + ", \"neighbors\": " + Node.neighbors.size()
                                    + ", \"packetsQueueSize\": " + Node.queuedTransactions.size()
                                    + ", \"time\": " + System.currentTimeMillis()
                                    + ", \"tips\": " + Storage.tips().size()
                                    + ", \"transactionsToRequest\": " + Storage.numberOfTransactionsToRequest;

                        }
                        break;

                        case "getTips": {

                            final List<String> elements = new LinkedList<>();
                            for (final Hash tip : Storage.tips()) {

                                elements.add("\"" + tip + "\"");
                            }

                            response = "\"hashes\": " + array(elements);

                        } break;

                        case "getTransactionsToApprove": {

                            final int depth = (Integer)request.get("depth");

                            final Hash trunkTransactionToApprove = TipsManager.transactionToApprove(null, depth);
                            if (trunkTransactionToApprove == null) {

                                response = "\"error\": \"The subtangle is not solid\"";

                            } else {

                                final Hash branchTransactionToApprove = TipsManager.transactionToApprove(trunkTransactionToApprove, depth);
                                if (branchTransactionToApprove == null) {

                                    response = "\"error\": \"The subtangle is not solid\"";

                                } else {

                                    response = "\"trunkTransaction\": \"" + trunkTransactionToApprove + "\", \"branchTransaction\": \"" + branchTransactionToApprove + "\"";
                                }
                            }

                        } break;

                        case "getTrytes": {

                            final List<String> elements = new LinkedList<>();

                            for (final String hash : (List<String>)request.get("hashes")) {

                                final Transaction transaction = Storage.loadTransaction((new Hash(hash)).bytes());
                                elements.add(transaction == null ? "null" : ("\"" + Converter.trytes(transaction.trits()) + "\""));
                            }

                            response = "\"trytes\": " + array(elements);

                        } break;

                        case "interruptAttachingToTangle": {

                            pearlDiver.interrupt();

                            response = "";

                        } break;

                        case "removeNeighbors": {

                            int numberOfRemovedNeighbors = 0;
                            for (final String uriString : (List<String>)request.get("uris")) {

                                final URI uri = new URI(uriString);
                                if (uri.getScheme() != null && uri.getScheme().equals("udp")) {

                                    if (Node.neighbors.remove(new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort())))) {

                                        numberOfRemovedNeighbors++;
                                    }
                                }
                            }

                            response = "{\"removedNeighbors\": " + numberOfRemovedNeighbors;

                        } break;

                        case "storeTransactions": {

                            for (final String trytes : (List<String>)request.get("trytes")) {

                                final Transaction transaction = new Transaction(Converter.trits(trytes));
                                Storage.storeTransaction(transaction.hash, transaction, false);
                            }

                            response = "";

                        } break;

                        default: {

                            response = "\"error\": \"Command '" + command + "' is unknown\"";
                        }
                    }
                }

            } catch (final Exception e) {

                log.error("API Exception: ", e);
                response = "\"exception\": \"" + e.toString().replaceAll("\"", "'").replace("\r", "\\r").replace("\n", "\\n") + "\"";
            }

            response = "{" + response + (response.length() == 0 ? "" : ", ") + "\"duration\": " + (System.currentTimeMillis() - beginningTime) + "}";
            response = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " + response.getBytes("UTF-8").length + "\r\nConnection: close\r\n\r\n" + response;
            final byte[] responseBytes = response.getBytes("UTF-8");
            buffer = ByteBuffer.allocateDirect(responseBytes.length);
            buffer.put(responseBytes);
            buffer.flip();
            channel.write(buffer, this, new CompletionHandler<Integer, Request>() {

                @Override
                public void completed(final Integer numberOfBytes, final Request request) {

                    if (buffer.hasRemaining()) {
                        channel.write(buffer, request, this);
                    } else {
                    	IOUtils.closeQuietly(channel);
                    }
                }

                @Override
                public void failed(final Throwable e, final Request request) {
                	IOUtils.closeQuietly(channel);
                }

            });
        }
    }
    
    private static CompletionHandler<AsynchronousSocketChannel, Void> handler = new CompletionHandler<AsynchronousSocketChannel, Void>() {

        @Override
        public void completed(final AsynchronousSocketChannel clientChannel, final Void attachment) {
            serverChannel.accept(null, this);
            new Request(clientChannel);
        }

        @Override
        public void failed(final Throwable e, final Void attachment) {
        }
    };

    public static void shutDown() {
    	IOUtils.closeQuietly(serverChannel);
    }
}
