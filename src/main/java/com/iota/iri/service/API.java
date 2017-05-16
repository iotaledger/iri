package com.iota.iri.service;

import static io.undertow.Handlers.path;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.iota.iri.controllers.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.streams.ChannelInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.IRI;
import com.iota.iri.IXI;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.PearlDiver;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Neighbor;
import com.iota.iri.network.Node;
import com.iota.iri.network.TCPNeighbor;
import com.iota.iri.network.UDPNeighbor;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.AccessLimitedResponse;
import com.iota.iri.service.dto.AddedNeighborsResponse;
import com.iota.iri.service.dto.AttachToTangleResponse;
import com.iota.iri.service.dto.ErrorResponse;
import com.iota.iri.service.dto.ExceptionResponse;
import com.iota.iri.service.dto.FindTransactionsResponse;
import com.iota.iri.service.dto.GetBalancesResponse;
import com.iota.iri.service.dto.GetInclusionStatesResponse;
import com.iota.iri.service.dto.GetNeighborsResponse;
import com.iota.iri.service.dto.GetNodeInfoResponse;
import com.iota.iri.service.dto.GetTipsResponse;
import com.iota.iri.service.dto.GetTransactionsToApproveResponse;
import com.iota.iri.service.dto.GetTrytesResponse;
import com.iota.iri.service.dto.RemoveNeighborsResponse;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.MapIdentityManager;

import io.undertow.Undertow;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import io.undertow.util.StatusCodes;

@SuppressWarnings("unchecked")
public class API {

    private static final Logger log = LoggerFactory.getLogger(API.class);

    private Undertow server;

    private final Gson gson = new GsonBuilder().create();
    private volatile PearlDiver pearlDiver = new PearlDiver();

    private final AtomicInteger counter = new AtomicInteger(0);

    private Pattern trytesPattern = Pattern.compile("[9A-Z]*");

    private final static int HASH_SIZE = 81;
    private final static int TRYTES_SIZE = 2673;

    public void init() throws IOException {

        final int apiPort = Configuration.integer(DefaultConfSettings.PORT);
        final String apiHost = Configuration.string(DefaultConfSettings.API_HOST);

        log.debug("Binding JSON-REST API Undertown server on {}:{}", apiHost, apiPort);

        server = Undertow.builder().addHttpListener(apiPort, apiHost)
                .setHandler(path().addPrefixPath("/", addSecurity(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        HttpString requestMethod = exchange.getRequestMethod();
                        if (Methods.OPTIONS.equals(requestMethod)) {
                            String allowedMethods = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
                            //return list of allowed methods in response headers
                            exchange.setStatusCode(StatusCodes.OK);
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
                            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
                            exchange.getResponseHeaders().put(Headers.ALLOW, allowedMethods);
                            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
                            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "Origin, X-Requested-With, Content-Type, Accept");
                            exchange.getResponseSender().close();
                            return;
                        }

                        if (exchange.isInIoThread()) {
                            exchange.dispatch(this);
                            return;
                        }
                        processRequest(exchange);
                    }
                }))).build();
        server.start();
    }

    private void processRequest(final HttpServerExchange exchange) throws IOException {
        final ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

        final long beginningTime = System.currentTimeMillis();
        final String body = IOUtils.toString(cis, StandardCharsets.UTF_8);
        final AbstractResponse response = process(body, exchange.getSourceAddress());
        sendResponse(exchange, response, beginningTime);
    }

    private AbstractResponse process(final String requestString, InetSocketAddress sourceAddress) throws UnsupportedEncodingException {

        try {

            final Map<String, Object> request = gson.fromJson(requestString, Map.class);
            if (request == null) {
                return ExceptionResponse.create("Invalid request payload: '" + requestString + "'");
            }

            final String command = (String) request.get("command");
            if (command == null) {
                return ErrorResponse.create("COMMAND parameter has not been specified in the request.");
            }

            if (Configuration.string(DefaultConfSettings.REMOTE_LIMIT_API).contains(command) &&
                    !sourceAddress.getAddress().isLoopbackAddress()) {
                return AccessLimitedResponse.create("COMMAND " + command + " is not available on this node");
            }

            log.debug("# {} -> Requesting command '{}'", counter.incrementAndGet(), command);

            switch (command) {

                case "addNeighbors": {
                    if (!request.containsKey("uris")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> uris = (List<String>) request.get("uris");
                    log.debug("Invoking 'addNeighbors' with {}", uris);

                    return addNeighborsStatement(uris);
                }
                case "attachToTangle": {
                    if (!request.containsKey("trunkTransaction") ||
                            !request.containsKey("branchTransaction") ||
                            !request.containsKey("minWeightMagnitude") ||
                            !request.containsKey("trytes")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    if (!validTrytes((String)request.get("trunkTransaction"), HASH_SIZE)) {
                        return ErrorResponse.create("Invalid trunkTransaction hash");
                    }
                    if (!validTrytes((String)request.get("branchTransaction"), HASH_SIZE)) {
                        return ErrorResponse.create("Invalid branchTransaction hash");
                    }
                    final Hash trunkTransaction = new Hash((String) request.get("trunkTransaction"));
                    final Hash branchTransaction = new Hash((String) request.get("branchTransaction"));
                    final int minWeightMagnitude = ((Double) request.get("minWeightMagnitude")).intValue();
                    final List<String> trytes = (List<String>) request.get("trytes");
                    for (final String tryt : trytes) {
                        if (!validTrytes(tryt, TRYTES_SIZE)) {
                            return ErrorResponse.create("Invalid trytes input");
                        }
                    }
                    return attachToTangleStatement(trunkTransaction, branchTransaction, minWeightMagnitude, trytes);
                }
                case "broadcastTransactions": {
                    if (!request.containsKey("trytes")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> trytes = (List<String>) request.get("trytes");
                    for (final String tryt : trytes) {
                        if (!validTrytes(tryt, TRYTES_SIZE)) {
                            return ErrorResponse.create("Invalid trytes input");
                        }
                    }
                    return broadcastTransactionStatement(trytes);
                }
                case "findTransactions": {
                    if (!request.containsKey("bundles") &&
                            !request.containsKey("addresses") &&
                            !request.containsKey("tags") &&
                            !request.containsKey("approvees")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    return findTransactionStatement(request);
                }
                case "getBalances": {
                    if (!request.containsKey("addresses") || !request.containsKey("threshold")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> addresses = (List<String>) request.get("addresses");
                    for (final String address : addresses) {
                        if (!validTrytes(address, HASH_SIZE)) {
                            return ErrorResponse.create("Invalid addresses input");
                        }
                    }
                    final int threshold = ((Double) request.get("threshold")).intValue();
                    return getBalancesStatement(addresses, threshold);
                }
                case "getInclusionStates": {
                    if (!request.containsKey("transactions") || !request.containsKey("tips")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> trans = (List<String>) request.get("transactions");
                    final List<String> tps = (List<String>) request.get("tips");

                    for (final String tx : trans) {
                        if (!validTrytes(tx, HASH_SIZE)) {
                            return ErrorResponse.create("Invalid transactions input");
                        }
                    }
                    for (final String ti : tps) {
                        if (!validTrytes(ti, HASH_SIZE)) {
                            return ErrorResponse.create("Invalid tips input");
                        }
                    }

                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operations cannot be executed: The subtangle has not been updated yet.");
                    }
                    return getNewInclusionStateStatement(trans, tps);
                }
                case "getNeighbors": {
                    return getNeighborsStatement();
                }
                case "getNodeInfo": {
                    String name = Configuration.booling(Configuration.DefaultConfSettings.TESTNET) ? IRI.TESTNET_NAME : IRI.MAINNET_NAME;
                    return GetNodeInfoResponse.create(name, IRI.VERSION, Runtime.getRuntime().availableProcessors(),
                            Runtime.getRuntime().freeMemory(), System.getProperty("java.version"), Runtime.getRuntime().maxMemory(),
                            Runtime.getRuntime().totalMemory(), Milestone.latestMilestone, Milestone.latestMilestoneIndex,
                            Milestone.latestSolidSubtangleMilestone, Milestone.latestSolidSubtangleMilestoneIndex,
                            Node.instance().howManyNeighbors(), Node.instance().queuedTransactionsSize(),
                            System.currentTimeMillis(), TipsViewModel.size(),
                            TransactionRequester.instance().numberOfTransactionsToRequest());
                }
                case "getTips": {
                    return getTipsStatement();
                }
                case "getTransactionsToApprove": {
                    final int depth = ((Double) request.get("depth")).intValue();
                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operations cannot be executed: The subtangle has not been updated yet.");
                    }
                    return getTransactionToApproveStatement(depth);
                }
                case "getTrytes": {
                    if (!request.containsKey("hashes")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> hashes = (List<String>) request.get("hashes");
                    if (hashes == null || hashes.size() == 0) {
                        return ErrorResponse.create("Wrong arguments");
                    }
                    for (final String hash : hashes) {
                        if (!validTrytes(hash, HASH_SIZE))  {
                            return ErrorResponse.create("Invalid hash input");
                        }
                    }
                    return getTrytesStatement(hashes);
                }

                case "interruptAttachingToTangle": {
                    pearlDiver.cancel();
                    return AbstractResponse.createEmptyResponse();
                }
                case "removeNeighbors": {
                    if (!request.containsKey("uris")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> uris = (List<String>) request.get("uris");
                    log.debug("Invoking 'removeNeighbors' with {}", uris);
                    return removeNeighborsStatement(uris);
                }

                case "storeTransactions": {
                    if (!request.containsKey("trytes")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    List<String> trytes = (List<String>) request.get("trytes");
                    log.debug("Invoking 'storeTransactions' with {}", trytes);
                    return storeTransactionStatement(trytes);
                }
                case "getMissingTransactions": {
                    //TransactionRequester.instance().rescanTransactionsToRequest();
                    synchronized (TransactionRequester.instance()) {
                        List<String> missingTx = Arrays.stream(TransactionRequester.instance().getRequestedTransactions())
                                .map(Hash::toString)
                                .collect(Collectors.toList());
                        return GetTipsResponse.create(missingTx);
                    }
                }
                default: {
                    AbstractResponse response = IXI.instance().processCommand(command, request);
                    return response == null ?
                            ErrorResponse.create("Command [" + command + "] is unknown") :
                            response;
                }
            }

        } catch (final Exception e) {
            log.error("API Exception: ", e);
            return ExceptionResponse.create(e.getLocalizedMessage());
        }
    }

    public static boolean invalidSubtangleStatus() {
        return (Milestone.latestSolidSubtangleMilestoneIndex == Milestone.MILESTONE_START_INDEX);
    }

    private AbstractResponse removeNeighborsStatement(List<String> uris) throws URISyntaxException {
        final AtomicInteger numberOfRemovedNeighbors = new AtomicInteger(0);
        
        for (final String uriString : uris) {
            final URI uri = new URI(uriString);
            
            if ("udp".equals(uri.getScheme()) || "tcp".equals(uri.getScheme())) {
                log.info("Removing neighbor: "+uriString);
                if (Node.instance().removeNeighbor(uri,true)) {
                    numberOfRemovedNeighbors.incrementAndGet();
                }
            }
            else {
                return ErrorResponse.create("Invalid uri scheme");
            }
        }
        return RemoveNeighborsResponse.create(numberOfRemovedNeighbors.get());
    }

    private AbstractResponse getTrytesStatement(List<String> hashes) throws Exception {
        final List<String> elements = new LinkedList<>();
        for (final String hash : hashes) {
            final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(new Hash(hash));
            if (transactionViewModel != null) {
                elements.add(Converter.trytes(transactionViewModel.trits()));
            }
        }
        return GetTrytesResponse.create(elements);
    }

    private static int counter_getTxToApprove = 0;
    public static int getCounter_getTxToApprove() {
        return counter_getTxToApprove;
    }
    public static void incCounter_getTxToApprove() {
        counter_getTxToApprove++;
    }
    
    private static long ellapsedTime_getTxToApprove = 0L;
    public static long getEllapsedTime_getTxToApprove() {
        return ellapsedTime_getTxToApprove;
    }
    public static void incEllapsedTime_getTxToApprove(long ellapsedTime) {
        ellapsedTime_getTxToApprove += ellapsedTime;
    }
   
    private synchronized AbstractResponse getTransactionToApproveStatement(final int depth) throws Exception {
        final SecureRandom random = new SecureRandom();
        final Hash trunkTransactionToApprove = TipsManager.transactionToApprove(null, depth, random);
        if (trunkTransactionToApprove == null) {
            return ErrorResponse.create("The subtangle is not solid");
        }
        final Hash branchTransactionToApprove = TipsManager.transactionToApprove(trunkTransactionToApprove, depth, random);
        if (branchTransactionToApprove == null) {
            return ErrorResponse.create("The subtangle is not solid");
        }
        API.incCounter_getTxToApprove();
        if ( ( getCounter_getTxToApprove() % 100) == 0 ) {
            String sb = "Last 100 getTxToApprove consumed " +
                    API.getEllapsedTime_getTxToApprove() / 1000000000L +
                    " seconds processing time.";
            log.info(sb);
            counter_getTxToApprove = 0;
            ellapsedTime_getTxToApprove = 0L;
        }
        return GetTransactionsToApproveResponse.create(trunkTransactionToApprove, branchTransactionToApprove);
    }

    private AbstractResponse getTipsStatement() throws ExecutionException, InterruptedException {
        return GetTipsResponse.create(TipsViewModel.getTips().stream().map(Hash::toString).collect(Collectors.toList()));
    }

    private AbstractResponse storeTransactionStatement(final List<String> trys) throws Exception {
        for (final String trytes : trys) {

            if (!validTrytes(trytes, TRYTES_SIZE)) {
                return ErrorResponse.create("Invalid trytes input");
            }
            final TransactionViewModel transactionViewModel = TransactionValidator.validate(Converter.trits(trytes));
            transactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
            transactionViewModel.store();
            transactionViewModel.updateStatus();
            transactionViewModel.updateSender("local");
        }
        return AbstractResponse.createEmptyResponse();
    }

    private AbstractResponse getNeighborsStatement() {
        return GetNeighborsResponse.create(Node.instance().getNeighbors());
    }

    private AbstractResponse getNewInclusionStateStatement(final List<String> trans, final List<String> tps) throws Exception {
        final List<Hash> transactions = trans.stream().map(Hash::new).collect(Collectors.toList());
        final List<Hash> tips = tps.stream().map(Hash::new).collect(Collectors.toList());
        int numberOfNonMetTransactions = transactions.size();
        final int[] inclusionStates = new int[numberOfNonMetTransactions];

        int[] tipsIndex = tips.stream().map(TransactionViewModel::quietFromHash)
                .filter(tx -> tx.getType() != TransactionViewModel.PREFILLED_SLOT)
                .mapToInt(TransactionViewModel::snapshotIndex)
                .toArray();
        int minTipsIndex = Arrays.stream(tipsIndex).reduce((a,b) -> a < b ? a : b).orElse(0);
        if(minTipsIndex > 0) {
            int maxTipsIndex = Arrays.stream(tipsIndex).reduce((a,b) -> a > b ? a : b).orElse(0);
            transactions.stream().map(TransactionViewModel::quietFromHash).forEach(transaction -> {
                if(transaction.getType() == TransactionViewModel.PREFILLED_SLOT || transaction.snapshotIndex() == 0) {
                    inclusionStates[transactions.indexOf(transaction.getHash())] = -1;
                } else if(transaction.snapshotIndex() > maxTipsIndex) {
                    inclusionStates[transactions.indexOf(transaction.getHash())] = -1;
                } else if(transaction.snapshotIndex() < maxTipsIndex) {
                    inclusionStates[transactions.indexOf(transaction.getHash())] = 1;
                }
            });
        }

        Set<Hash> analyzedTips = new HashSet<>();
        Map<Integer, Set<Hash>> sameIndexTips = new HashMap<>();
        Map<Integer, Set<Hash>> sameIndexTransactions = new HashMap<>();
        Map<Integer, Queue<Hash>> nonAnalyzedTransactionsMap = new HashMap<>();
        for (final Hash tip : tips) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tip);
            if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT){
                return ErrorResponse.create("One of the tips absents");
            }
            sameIndexTips.putIfAbsent(transactionViewModel.snapshotIndex(), new HashSet<>());
            sameIndexTips.get(transactionViewModel.snapshotIndex()).add(tip);
            nonAnalyzedTransactionsMap.putIfAbsent(transactionViewModel.snapshotIndex(), new LinkedList<>());
            nonAnalyzedTransactionsMap.get(transactionViewModel.snapshotIndex()).offer(tip);
        }
        for(int i = 0; i < inclusionStates.length; i++) {
            if(inclusionStates[i] == 0) {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactions.get(i));
                sameIndexTransactions.putIfAbsent(transactionViewModel.snapshotIndex(), new HashSet<>());
                sameIndexTransactions.get(transactionViewModel.snapshotIndex()).add(transactionViewModel.getHash());
            }
        }
        for(Map.Entry<Integer, Set<Hash>> entry: sameIndexTransactions.entrySet()) {
            if(!exhaustiveSearchWithinIndex(nonAnalyzedTransactionsMap.get(entry.getKey()), analyzedTips, transactions, inclusionStates, entry.getValue().size(), entry.getKey())) {
                return ErrorResponse.create("The subtangle is not solid");
            }
        }
        final boolean[] inclusionStatesBoolean = new boolean[inclusionStates.length];
        for(int i = 0; i < inclusionStates.length; i++) {
            inclusionStatesBoolean[i] = inclusionStates[i] == 1;
        }
        {
            return GetInclusionStatesResponse.create(inclusionStatesBoolean);
        }
    }
    private boolean exhaustiveSearchWithinIndex(Queue<Hash> nonAnalyzedTransactions, Set<Hash> analyzedTips, List<Hash> transactions, int[] inclusionStates, int count, int index) throws Exception {
        Hash pointer;
        MAIN_LOOP:
        while ((pointer = nonAnalyzedTransactions.poll()) != null) {


            if (analyzedTips.add(pointer)) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(pointer);
                if(transactionViewModel.snapshotIndex() == index) {
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        return false;
                    } else {
                        for (int i = 0; i < inclusionStates.length; i++) {

                            if (inclusionStates[i] < 1 && pointer.equals(transactions.get(i))) {
                                inclusionStates[i] = 1;
                                if (--count<= 0) {
                                    break MAIN_LOOP;
                                }
                            }
                        }
                        nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                    }
                }
            }
        }
        return true;
    }

    private AbstractResponse getInclusionStateStatement(final List<String> trans, final List<String> tps) throws Exception {

        final List<Hash> transactions = trans.stream().map(Hash::new).collect(Collectors.toList());
        final List<Hash> tips = tps.stream().map(Hash::new).collect(Collectors.toList());

        int numberOfNonMetTransactions = transactions.size();
        final boolean[] inclusionStates = new boolean[numberOfNonMetTransactions];

            Set<Hash> analyzedTips = new HashSet<>();

            final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>();
            for (final Hash tip : tips) {

                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tip);
                if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT){
                    return ErrorResponse.create("One of the tips absents");
                }

                nonAnalyzedTransactions.offer(tip);
            }

            {
                Hash pointer;
                MAIN_LOOP:
                while ((pointer = nonAnalyzedTransactions.poll()) != null) {


                    if (analyzedTips.add(pointer)) {

                        final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(pointer);
                        if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                            return ErrorResponse.create("The subtangle is not solid");
                        } else {
                            for (int i = 0; i < inclusionStates.length; i++) {

                                if (!inclusionStates[i] && pointer.equals(transactions.get(i))) {
                                    inclusionStates[i] = true;
                                    if(--numberOfNonMetTransactions <= 0) {
                                        break MAIN_LOOP;
                                    }
                                }
                            }
                            nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                            nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                        }
                    }
                }
                return GetInclusionStatesResponse.create(inclusionStates);
            }
    }

    private AbstractResponse findTransactionStatement(final Map<String, Object> request) throws Exception {
        final Set<Hash> bundlesTransactions = new HashSet<>();

        if (request.containsKey("bundles")) {
            for (final String bundle : (List<String>) request.get("bundles")) {
                if (!validTrytes(bundle, HASH_SIZE)) {
                    return ErrorResponse.create("Invalid bundle hash");
                }
                bundlesTransactions.addAll(BundleViewModel.load(new Hash(bundle)).getHashes());
            }
        }

        final Set<Hash> addressesTransactions = new HashSet<>();
        if (request.containsKey("addresses")) {
            final List<String> addresses = (List<String>) request.get("addresses");
            log.debug("Searching: {}", addresses.stream().reduce((a, b) -> a += ',' + b));

            for (final String address : addresses) {
                if (!validTrytes(address, HASH_SIZE)) {
                    return ErrorResponse.create("Invalid address input");
                }
                addressesTransactions.addAll(AddressViewModel.load(new Hash(address)).getHashes());
            }
        }

        final Set<Hash> tagsTransactions = new HashSet<>();
        if (request.containsKey("tags")) {
            for (String tag : (List<String>) request.get("tags")) {
                if (!validTrytes(tag,tag.length())) {
                    return ErrorResponse.create("Invalid tag input");
                }
                while (tag.length() < Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE) {
                    tag += Converter.TRYTE_ALPHABET.charAt(0);
                }
                tagsTransactions.addAll(TagViewModel.load(new Hash(tag)).getHashes());
            }
        }

        final Set<Hash> approveeTransactions = new HashSet<>();

        if (request.containsKey("approvees")) {
            for (final String approvee : (List<String>) request.get("approvees")) {
                if (!validTrytes(approvee,HASH_SIZE)) {
                    return ErrorResponse.create("Invalid approvees hash");
                }
                approveeTransactions.addAll(TransactionViewModel.fromHash(new Hash(approvee)).getApprovers().getHashes());
            }
        }

        // need refactoring
        final Set<Hash> foundTransactions = bundlesTransactions.isEmpty() ? (addressesTransactions.isEmpty()
                ? (tagsTransactions.isEmpty()
                ? (approveeTransactions.isEmpty() ? new HashSet<>() : approveeTransactions) : tagsTransactions)
                : addressesTransactions) : bundlesTransactions;

        if (!addressesTransactions.isEmpty()) {
            foundTransactions.retainAll(addressesTransactions);
        }
        if (!tagsTransactions.isEmpty()) {
            foundTransactions.retainAll(tagsTransactions);
        }
        if (!approveeTransactions.isEmpty()) {
            foundTransactions.retainAll(approveeTransactions);
        }

        final List<String> elements = foundTransactions.stream()
                .map(Hash::toString)
                .collect(Collectors.toCollection(LinkedList::new));

        return FindTransactionsResponse.create(elements);
    }

    private AbstractResponse broadcastTransactionStatement(final List<String> trytes2) {
        for (final String tryte : trytes2) {
            //validate PoW - throws exception if invalid
            final TransactionViewModel transactionViewModel = TransactionValidator.validate(Converter.trits(tryte));
            //push first in line to broadcast
            transactionViewModel.weightMagnitude = Curl.HASH_LENGTH;
            Node.instance().broadcast(transactionViewModel);
        }
        return AbstractResponse.createEmptyResponse();
    }

    private AbstractResponse getBalancesStatement(final List<String> addrss, final int threshold) throws Exception {

        if (threshold <= 0 || threshold > 100) {
            return ErrorResponse.create("Illegal 'threshold'");
        }

        final List<Hash> addresses = addrss.stream().map(address -> (new Hash(address)))
                .collect(Collectors.toCollection(LinkedList::new));

        final Map<Hash, Long> balances = new HashMap<>();
        final int index;
        synchronized (Snapshot.latestSnapshotSyncObject) {
            index = Snapshot.latestSnapshot.index();
            for (final Hash address : addresses) {
                balances.put(address,
                        Snapshot.latestSnapshot.getState().containsKey(address) ?
                                Snapshot.latestSnapshot.getState().get(address) : Long.valueOf(0));
            }
        }

        final Hash milestone = Milestone.latestSolidSubtangleMilestone;
        final int milestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;


            Set<Hash> analyzedTips = new HashSet<>();

            final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(milestone));
                    //Collections.singleton(StorageTransactions.instance().transactionPointer(milestone.value())));
            Hash hash;
            while ((hash = nonAnalyzedTransactions.poll()) != null) {

                if (analyzedTips.add(hash)) {

                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);

                    if(transactionViewModel.snapshotIndex() == 0 || transactionViewModel.snapshotIndex() > index) {
                        if (transactionViewModel.value() != 0) {

                            final Hash address = transactionViewModel.getAddressHash();
                            final Long balance = balances.get(address);
                            if (balance != null) {

                                balances.put(address, balance + transactionViewModel.value());
                            }
                        }
                        nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                    }
                }
            }
        final List<String> elements = addresses.stream().map(address -> balances.get(address).toString())
                .collect(Collectors.toCollection(LinkedList::new));

        return GetBalancesResponse.create(elements, milestone, milestoneIndex);
    }

    private static int counter_PoW = 0;
    public static int getCounter_PoW() {
        return counter_PoW;
    }
    public static void incCounter_PoW() {
        API.counter_PoW++;
    }

    private static long ellapsedTime_PoW = 0L;
    public static long getEllapsedTime_PoW() {
        return ellapsedTime_PoW;
    }
    public static void incEllapsedTime_PoW(long ellapsedTime) {
        ellapsedTime_PoW += ellapsedTime;
    }
    
    private synchronized AbstractResponse attachToTangleStatement(final Hash trunkTransaction, final Hash branchTransaction,
                                                                  final int minWeightMagnitude, final List<String> trytes) {
        final List<TransactionViewModel> transactionViewModels = new LinkedList<>();

        Hash prevTransaction = null;
        pearlDiver = new PearlDiver();

        for (final String tryte : trytes) {
            long startTime = System.nanoTime();
            try {
                final int[] transactionTrits = Converter.trits(tryte);
                System.arraycopy((prevTransaction == null ? trunkTransaction : prevTransaction).trits(), 0,
                        transactionTrits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                        TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
                System.arraycopy((prevTransaction == null ? branchTransaction : trunkTransaction).trits(), 0,
                        transactionTrits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                        TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
                if (!pearlDiver.search(transactionTrits, minWeightMagnitude, 0)) {
                    transactionViewModels.clear();
                    break;
                }
                //validate PoW - throws exception if invalid
                final TransactionViewModel transactionViewModel = TransactionValidator.validate(transactionTrits);

                transactionViewModels.add(transactionViewModel);
                prevTransaction = transactionViewModel.getHash();
            } finally {
                API.incEllapsedTime_PoW(System.nanoTime() - startTime);
                API.incCounter_PoW();
                if ( ( API.getCounter_PoW() % 100) == 0 ) {
                    String sb = "Last 100 PoW consumed " +
                            API.getEllapsedTime_PoW() / 1000000000L +
                            " seconds processing time.";
                    log.info(sb);
                    counter_PoW = 0;
                    ellapsedTime_PoW = 0L;
                }
            }
        }

        final List<String> elements = new LinkedList<>();
        for (int i = transactionViewModels.size(); i-- > 0; ) {
            elements.add(Converter.trytes(transactionViewModels.get(i).trits()));
        }
        return AttachToTangleResponse.create(elements);
    }

    private AbstractResponse addNeighborsStatement(final List<String> uris) throws URISyntaxException {

        int numberOfAddedNeighbors = 0;
        for (final String uriString : uris) {
            final URI uri = new URI(uriString);
            
            if ("udp".equals(uri.getScheme()) || "tcp".equals(uri.getScheme())) {
                // 3rd parameter true if tcp, 4th parameter true (configured tethering)
                final Neighbor neighbor;
                switch(uri.getScheme()) {
                    case "tcp":
                        neighbor = new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()),true);
                        break;
                    case "udp":
                        neighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()),true);
                        break;
                    default:
                        return ErrorResponse.create("Invalid uri scheme");
                }
                if (!Node.instance().getNeighbors().contains(neighbor)) {
                    Node.instance().getNeighbors().add(neighbor);
                    numberOfAddedNeighbors++;
                }
            }
            else {
                return ErrorResponse.create("Invalid uri scheme");
            }
        }
        return AddedNeighborsResponse.create(numberOfAddedNeighbors);
    }

    private void sendResponse(final HttpServerExchange exchange, final AbstractResponse res, final long beginningTime)
            throws IOException {
        res.setDuration((int) (System.currentTimeMillis() - beginningTime));
        final String response = gson.toJson(res);

        if (res instanceof ErrorResponse) {
            exchange.setStatusCode(400); // bad request
        } else if (res instanceof AccessLimitedResponse) {
            exchange.setStatusCode(401); // api method not allowed
        } else if (res instanceof ExceptionResponse) {
            exchange.setStatusCode(500); // internal error
        }

        setupResponseHeaders(exchange);

        ByteBuffer responseBuf = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        exchange.setResponseContentLength(responseBuf.array().length);
        StreamSinkChannel sinkChannel = exchange.getResponseChannel();
        sinkChannel.getWriteSetter().set( channel -> {
            if (responseBuf.remaining() > 0)
                try {
                    sinkChannel.write(responseBuf);
                    if (responseBuf.remaining() == 0) {
                        exchange.endExchange();
                    }
                } catch (IOException e) {
                    log.error("Error writing response",e);
                    exchange.endExchange();
                }
            else {
                exchange.endExchange();
            }
        });
        sinkChannel.resumeWrites();
    }

    private boolean validTrytes(String trytes, int minimalLength) {
        if (trytes.length() < minimalLength) {
            return false;
        }
        Matcher matcher = trytesPattern.matcher(trytes);
        return matcher.matches();
    }

    private static void setupResponseHeaders(final HttpServerExchange exchange) {
        final HeaderMap headerMap = exchange.getResponseHeaders();
        headerMap.add(new HttpString("Access-Control-Allow-Origin"),"*");
        headerMap.add(new HttpString("Keep-Alive"), "timeout=500, max=100");
    }
    
    private HttpHandler addSecurity(final HttpHandler toWrap) {
        String credentials = Configuration.string(DefaultConfSettings.REMOTE_AUTH);
        if(credentials == null || credentials.isEmpty()) return toWrap;

        final Map<String, char[]> users = new HashMap<>(2);
        users.put(credentials.split(":")[0], credentials.split(":")[1].toCharArray());

        IdentityManager identityManager = new MapIdentityManager(users);
        HttpHandler handler = toWrap;
        handler = new AuthenticationCallHandler(handler);
        handler = new AuthenticationConstraintHandler(handler);
        final List<AuthenticationMechanism> mechanisms = Collections.singletonList(new BasicAuthenticationMechanism("Iota Realm"));
        handler = new AuthenticationMechanismsHandler(handler, mechanisms);
        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
        return handler;
    }

    public void shutDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static final API instance = new API();

    public static API instance() {
        return instance;
    }
}