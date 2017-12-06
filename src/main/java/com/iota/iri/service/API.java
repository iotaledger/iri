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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.iota.iri.*;
import com.iota.iri.controllers.*;
import com.iota.iri.network.*;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Tangle;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.streams.ChannelInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.PearlDiver;
import com.iota.iri.model.Hash;
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
    private final IXI ixi;

    private Undertow server;

    private final Gson gson = new GsonBuilder().create();
    private volatile PearlDiver pearlDiver = new PearlDiver();

    private final AtomicInteger counter = new AtomicInteger(0);

    private Pattern trytesPattern = Pattern.compile("[9A-Z]*");

    private final static int HASH_SIZE = 81;
    private final static int TRYTES_SIZE = 2673;

    private final static long MAX_TIMESTAMP_VALUE = (3^27 - 1) / 2;

    private final int minRandomWalks;
    private final int maxRandomWalks;
    private final int maxFindTxs;
    private final int maxRequestList;
    private final int maxGetTrytes;
    private final int maxBodyLength;
    private final static String overMaxErrorMessage = "Could not complete request";
    private final static String invalidParams = "Invalid parameters";

    private final static char ZERO_LENGTH_ALLOWED = 'Y';
    private final static char ZERO_LENGTH_NOT_ALLOWED = 'N';
    private Iota instance;

    public API(Iota instance, IXI ixi) {
        this.instance = instance;
        this.ixi = ixi;
        minRandomWalks = instance.configuration.integer(DefaultConfSettings.MIN_RANDOM_WALKS);
        maxRandomWalks = instance.configuration.integer(DefaultConfSettings.MAX_RANDOM_WALKS);
        maxFindTxs = instance.configuration.integer(DefaultConfSettings.MAX_FIND_TRANSACTIONS);
        maxRequestList = instance.configuration.integer(DefaultConfSettings.MAX_REQUESTS_LIST);
        maxGetTrytes = instance.configuration.integer(DefaultConfSettings.MAX_GET_TRYTES);
        maxBodyLength = instance.configuration.integer(DefaultConfSettings.MAX_BODY_LENGTH);
    }

    public void init() throws IOException {
        final int apiPort = instance.configuration.integer(DefaultConfSettings.PORT);
        final String apiHost = instance.configuration.string(DefaultConfSettings.API_HOST);

        log.debug("Binding JSON-REST API Undertow server on {}:{}", apiHost, apiPort);

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
                            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "Origin, X-Requested-With, Content-Type, Accept, X-IOTA-API-Version");
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

        final AbstractResponse response;
        if (!exchange.getRequestHeaders().contains("X-IOTA-API-Version")) {
            response = ErrorResponse.create("Invalid API Version");
        } else {
            response = process(body, exchange.getSourceAddress());
        }

        sendResponse(exchange, response, beginningTime);
    }

    private AbstractResponse process(final String requestString, InetSocketAddress sourceAddress) {

        try {

            if (requestString.length() > maxBodyLength) {
                return ErrorResponse.create("Request too long");
            }

            final Map<String, Object> request = gson.fromJson(requestString, Map.class);
            if (request == null) {
                return ErrorResponse.create("Invalid request payload: '" + requestString + "'");
            }

            final String command = (String) request.get("command");
            if (command == null) {
                return ErrorResponse.create("COMMAND parameter has not been specified in the request.");
            }

            if (instance.configuration.string(DefaultConfSettings.REMOTE_LIMIT_API).contains(command) &&
                    !sourceAddress.getAddress().isLoopbackAddress()) {
                return AccessLimitedResponse.create("COMMAND " + command + " is not available on this node");
            }

            log.debug("# {} -> Requesting command '{}'", counter.incrementAndGet(), command);

            switch (command) {

                case "addNeighbors": {
                    List<String> uris = getParameterAsList(request,"uris",0);
                    log.debug("Invoking 'addNeighbors' with {}", uris);
                    return addNeighborsStatement(uris);
                }
                case "attachToTangle": {
                    final Hash trunkTransaction  = new Hash(getParameterAsStringAndValidate(request,"trunkTransaction", HASH_SIZE));
                    final Hash branchTransaction = new Hash(getParameterAsStringAndValidate(request,"branchTransaction", HASH_SIZE));
                    final int minWeightMagnitude = getParameterAsInt(request,"minWeightMagnitude");

                    final List<String> trytes = getParameterAsList(request,"trytes", TRYTES_SIZE);

                    List<String> elements = attachToTangleStatement(trunkTransaction, branchTransaction, minWeightMagnitude, trytes);
                    return AttachToTangleResponse.create(elements);
                }
                case "broadcastTransactions": {
                    broadcastTransactionStatement(getParameterAsList(request,"trytes", TRYTES_SIZE));
                    return AbstractResponse.createEmptyResponse();
                }
                case "findTransactions": {
                    return findTransactionStatement(request);
                }
                case "getBalances": {
                    final List<String> addresses = getParameterAsList(request,"addresses", HASH_SIZE);
                    final int threshold = getParameterAsInt(request, "threshold");
                    return getBalancesStatement(addresses, threshold);
                }
                case "getInclusionStates": {
                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operations cannot be executed: The subtangle has not been updated yet.");
                    }
                    final List<String> transactions = getParameterAsList(request,"transactions", HASH_SIZE);
                    final List<String> tips = getParameterAsList(request,"tips", HASH_SIZE);

                    return getNewInclusionStateStatement(transactions, tips);
                }
                case "getNeighbors": {
                    return getNeighborsStatement();
                }
                case "getNodeInfo": {
                    String name = instance.configuration.booling(Configuration.DefaultConfSettings.TESTNET) ? IRI.TESTNET_NAME : IRI.MAINNET_NAME;
                    return GetNodeInfoResponse.create(name, IRI.VERSION, Runtime.getRuntime().availableProcessors(),
                            Runtime.getRuntime().freeMemory(), System.getProperty("java.version"), Runtime.getRuntime().maxMemory(),
                            Runtime.getRuntime().totalMemory(), instance.milestone.latestMilestone, instance.milestone.latestMilestoneIndex,
                            instance.milestone.latestSolidSubtangleMilestone, instance.milestone.latestSolidSubtangleMilestoneIndex,
                            instance.node.howManyNeighbors(), instance.node.queuedTransactionsSize(),
                            System.currentTimeMillis(), instance.tipsViewModel.size(),
                            instance.transactionRequester.numberOfTransactionsToRequest());
                }
                case "getTips": {
                    return getTipsStatement();
                }
                case "getTransactionsToApprove": {
                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operations cannot be executed: The subtangle has not been updated yet.");
                    }

                    final int depth = getParameterAsInt(request, "depth");
                    final String reference = request.containsKey("reference") ? getParameterAsStringAndValidate(request,"reference", HASH_SIZE) : null;
                    int numWalks = request.containsKey("numWalks") ? getParameterAsInt(request,"numWalks") : 1;
                    if(numWalks < minRandomWalks) {
                        numWalks = minRandomWalks;
                    }

                    final Hash[] tips = getTransactionToApproveStatement(depth, reference, numWalks);
                    if(tips == null) {
                        return ErrorResponse.create("The subtangle is not solid");
                    }
                    return GetTransactionsToApproveResponse.create(tips[0], tips[1]);
                }
                case "getTrytes": {
                    final List<String> hashes = getParameterAsList(request,"hashes", HASH_SIZE);
                    return getTrytesStatement(hashes);
                }

                case "interruptAttachingToTangle": {
                    pearlDiver.cancel();
                    return AbstractResponse.createEmptyResponse();
                }
                case "removeNeighbors": {
                    List<String> uris = getParameterAsList(request,"uris",0);
                    log.debug("Invoking 'removeNeighbors' with {}", uris);
                    return removeNeighborsStatement(uris);
                }

                case "storeTransactions": {
                    try {
                        storeTransactionStatement(getParameterAsList(request, "trytes", TRYTES_SIZE));
                    } catch (Exception e) {
                        //transaction not valid
                        return ErrorResponse.create("Invalid trytes input");
                    }
                    return AbstractResponse.createEmptyResponse();
                }
                case "getMissingTransactions": {
                    //TransactionRequester.instance().rescanTransactionsToRequest();
                    synchronized (instance.transactionRequester) {
                        List<String> missingTx = Arrays.stream(instance.transactionRequester.getRequestedTransactions())
                                .map(Hash::toString)
                                .collect(Collectors.toList());
                        return GetTipsResponse.create(missingTx);
                    }
                }
                default: {
                    AbstractResponse response = ixi.processCommand(command, request);
                    return response == null ?
                            ErrorResponse.create("Command [" + command + "] is unknown") :
                            response;
                }
            }

        } catch (final IllegalArgumentException e) {
            log.info("API Validation failed: " + e.getLocalizedMessage());
            return ErrorResponse.create(e.getLocalizedMessage());
        } catch (final IllegalStateException e) {
            log.error("API Exception: ", e);
            return ExceptionResponse.create(e.getLocalizedMessage());
        }
    }

    private int getParameterAsInt(Map<String, Object> request, String paramName) {
        validateParamExists(request, paramName);
        final int result;
        try {
            result = ((Double) request.get(paramName)).intValue();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Invalid " + paramName + " input");
        }
        return result;
    }

    private String getParameterAsStringAndValidate(Map<String, Object> request, String paramName, int size) {
        validateParamExists(request, paramName);
        String result = (String) request.get(paramName);
        validateTrytes(paramName, size, result);
        return result;
    }

    private void validateTrytes(String paramName, int size, String result) {
        if (!validTrytes(result,size,ZERO_LENGTH_NOT_ALLOWED)) {
            throw new IllegalArgumentException("Invalid " + paramName + " input");
        }
    }

    private void validateParamExists(Map<String, Object> request, String paramName) {
        if (!request.containsKey(paramName)) {
            throw new IllegalArgumentException(invalidParams);
        }
    }

    private List<String> getParameterAsList(Map<String, Object> request, String paramName, int size) {
        validateParamExists(request, paramName);
        final List<String> paramList = (List<String>) request.get(paramName);
        if (paramList.size() > maxRequestList) {
            throw new IllegalArgumentException(overMaxErrorMessage);
        }

        if (size > 0) {
            //validate
            for (final String param : paramList) {
                validateTrytes(paramName, size, param);
            }
        }

        return paramList;

    }

    public boolean invalidSubtangleStatus() {
        return (instance.milestone.latestSolidSubtangleMilestoneIndex == Milestone.MILESTONE_START_INDEX);
    }

    private AbstractResponse removeNeighborsStatement(List<String> uris) {
        final AtomicInteger numberOfRemovedNeighbors = new AtomicInteger(0);

        for (final String uriString : uris) {
            final URI uri = getValidUri(uriString);
            log.info("Removing neighbor: "+uriString);
            if (instance.node.removeNeighbor(uri,true)) {
                numberOfRemovedNeighbors.incrementAndGet();
            }
        }

        return RemoveNeighborsResponse.create(numberOfRemovedNeighbors.get());
    }

    private synchronized AbstractResponse getTrytesStatement(List<String> hashes) {
        final List<String> elements = new LinkedList<>();
        for (final String hash : hashes) {
            final TransactionViewModel transactionViewModel = transactionViewModelFromHash(instance.tangle, new Hash(hash));
            if (transactionViewModel != null) {
                elements.add(Converter.trytes(transactionViewModel.trits()));
            }
        }
        if (elements.size() > maxGetTrytes){
            return ErrorResponse.create(overMaxErrorMessage);
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

    public synchronized Hash[] getTransactionToApproveStatement(final int depth, final String reference, final int numWalks) {
        int tipsToApprove = 2;
        Hash[] tips = new Hash[tipsToApprove];
        final SecureRandom random = new SecureRandom();
        final int randomWalkCount = numWalks > maxRandomWalks || numWalks < 1 ? maxRandomWalks:numWalks;
        Hash referenceHash = null;
        if(reference != null) {
            referenceHash = new Hash(reference);
            if(!transactionViewModelExist(instance.tangle, referenceHash)) {
                referenceHash = null;
            }
        }
        for(int i = 0; i < tipsToApprove; i++) {
            tips[i] = instance.tipsManager.transactionToApprove(referenceHash, tips[0], depth, randomWalkCount, random);
            if (tips[i] == null) {
                return null;
            }
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
        return tips;
    }

    private synchronized AbstractResponse getTipsStatement() {
        return GetTipsResponse.create(instance.tipsViewModel.getTips().stream().map(Hash::toString).collect(Collectors.toList()));
    }

    public void storeTransactionStatement(final List<String> trys) throws Exception {
        for (final String trytes : trys) {
            final TransactionViewModel transactionViewModel = instance.transactionValidator.validate(Converter.trits(trytes),
                    instance.transactionValidator.getMinWeightMagnitude());
            if(transactionViewModel.store(instance.tangle)) {
                transactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                instance.transactionValidator.updateStatus(transactionViewModel);
                transactionViewModel.updateSender("local");
                transactionViewModel.update(instance.tangle, "sender");
            }
        }
    }

    private AbstractResponse getNeighborsStatement() {
        return GetNeighborsResponse.create(instance.node.getNeighbors());
    }

    private AbstractResponse getNewInclusionStateStatement(final List<String> trans, final List<String> tps) {
        final List<Hash> transactions = trans.stream().map(Hash::new).collect(Collectors.toList());
        final List<Hash> tips = tps.stream().map(Hash::new).collect(Collectors.toList());
        int numberOfNonMetTransactions = transactions.size();
        final int[] inclusionStates = new int[numberOfNonMetTransactions];

        List<Integer> tipsIndex = new LinkedList<>();
        {
            for(Hash hash: tips) {
                TransactionViewModel tx = transactionViewModelFromHash(instance.tangle, hash);
                if (tx.getType() != TransactionViewModel.PREFILLED_SLOT) {
                    tipsIndex.add(tx.snapshotIndex());
                }
            }
        }
        int minTipsIndex = tipsIndex.stream().reduce((a,b) -> a < b ? a : b).orElse(0);
        if(minTipsIndex > 0) {
            int maxTipsIndex = tipsIndex.stream().reduce((a,b) -> a > b ? a : b).orElse(0);
            for(Hash hash: transactions) {
                TransactionViewModel transaction = transactionViewModelFromHash(instance.tangle, hash);
                if(transaction.getType() == TransactionViewModel.PREFILLED_SLOT || transaction.snapshotIndex() == 0) {
                    inclusionStates[transactions.indexOf(transaction.getHash())] = -1;
                } else if(transaction.snapshotIndex() > maxTipsIndex) {
                    inclusionStates[transactions.indexOf(transaction.getHash())] = -1;
                } else if(transaction.snapshotIndex() < maxTipsIndex) {
                    inclusionStates[transactions.indexOf(transaction.getHash())] = 1;
                }
            }
        }

        Set<Hash> analyzedTips = new HashSet<>();
        Map<Integer, Set<Hash>> sameIndexTips = new HashMap<>();
        Map<Integer, Set<Hash>> sameIndexTransactions = new HashMap<>();
        Map<Integer, Queue<Hash>> nonAnalyzedTransactionsMap = new HashMap<>();
        for (final Hash tip : tips) {
            TransactionViewModel transactionViewModel = transactionViewModelFromHash(instance.tangle, tip);
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
                TransactionViewModel transactionViewModel = transactionViewModelFromHash(instance.tangle, transactions.get(i));
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
    private boolean exhaustiveSearchWithinIndex(Queue<Hash> nonAnalyzedTransactions, Set<Hash> analyzedTips, List<Hash> transactions, int[] inclusionStates, int count, int index) {
        Hash pointer;
        MAIN_LOOP:
        while ((pointer = nonAnalyzedTransactions.poll()) != null) {


            if (analyzedTips.add(pointer)) {

                final TransactionViewModel transactionViewModel = transactionViewModelFromHash(instance.tangle, pointer);
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

    private synchronized AbstractResponse findTransactionStatement(final Map<String, Object> request) {
        final Set<Hash> foundTransactions =  new HashSet<>();
        boolean containsKey = false;

        final Set<Hash> bundlesTransactions = new HashSet<>();
        if (request.containsKey("bundles")) {
            final HashSet<String> bundles = getParameterAsSet(request,"bundles",HASH_SIZE);
            for (final String bundle : bundles) {
                bundlesTransactions.addAll(bundleViewModelLoad(instance.tangle, new Hash(bundle)).getHashes());
            }
            foundTransactions.addAll(bundlesTransactions);
            containsKey = true;
        }

        final Set<Hash> addressesTransactions = new HashSet<>();
        if (request.containsKey("addresses")) {
            final HashSet<String> addresses = getParameterAsSet(request,"addresses",HASH_SIZE);
            for (final String address : addresses) {
                addressesTransactions.addAll(addressViewModelLoad(instance.tangle, new Hash(address)).getHashes());
            }
            foundTransactions.addAll(addressesTransactions);
            containsKey = true;
        }

        final Set<Hash> tagsTransactions = new HashSet<>();
        if (request.containsKey("tags")) {
            final HashSet<String> tags = getParameterAsSet(request,"tags",0);
            for (String tag : tags) {
                tag = padTag(tag);
                tagsTransactions.addAll(tagViewModelLoad(instance.tangle, new Hash(tag)).getHashes());
            }
            foundTransactions.addAll(tagsTransactions);
            containsKey = true;
        }

        final Set<Hash> approveeTransactions = new HashSet<>();

        if (request.containsKey("approvees")) {
            final HashSet<String> approvees = getParameterAsSet(request,"approvees",HASH_SIZE);
            for (final String approvee : approvees) {
                approveeTransactions.addAll(approveeViewModelGetApprovers(transactionViewModelFromHash(instance.tangle, new Hash(approvee)), instance.tangle).getHashes());
            }
            foundTransactions.addAll(approveeTransactions);
            containsKey = true;
        }

        if (!containsKey) {
            throw new IllegalArgumentException(invalidParams);
        }

        //Using multiple of these input fields returns the intersection of the values.
        if (request.containsKey("bundles")) {
            foundTransactions.retainAll(bundlesTransactions);
        }
        if (request.containsKey("addresses")) {
            foundTransactions.retainAll(addressesTransactions);
        }
        if (request.containsKey("tags")) {
            foundTransactions.retainAll(tagsTransactions);
        }
        if (request.containsKey("approvees")) {
            foundTransactions.retainAll(approveeTransactions);
        }
        if (foundTransactions.size() > maxFindTxs){
            return ErrorResponse.create(overMaxErrorMessage);
        }

        final List<String> elements = foundTransactions.stream()
                .map(Hash::toString)
                .collect(Collectors.toCollection(LinkedList::new));

        return FindTransactionsResponse.create(elements);
    }

    private String padTag(String tag) {
        while (tag.length() < HASH_SIZE) {
            tag += Converter.TRYTE_ALPHABET.charAt(0);
        }
        if (tag.equals(Hash.NULL_HASH.toString())) {
            throw new IllegalArgumentException("Invalid tag input");
        }
        return tag;
    }

    private HashSet<String> getParameterAsSet(Map<String, Object> request, String paramName, int size) {

        HashSet<String> result = getParameterAsList(request,paramName,size).stream().collect(Collectors.toCollection(HashSet::new));
        if (result.contains(Hash.NULL_HASH.toString())) {
            throw new IllegalArgumentException("Invalid " + paramName + " input");
        }
        return result;
    }

    public void broadcastTransactionStatement(final List<String> trytes2) {
        for (final String tryte : trytes2) {
            //validate PoW - throws exception if invalid
            final TransactionViewModel transactionViewModel = instance.transactionValidator.validate(Converter.trits(tryte), instance.transactionValidator.getMinWeightMagnitude());
            //push first in line to broadcast
            transactionViewModel.weightMagnitude = Curl.HASH_LENGTH;
            instance.node.broadcast(transactionViewModel);
        }
    }

    private AbstractResponse getBalancesStatement(final List<String> addrss, final int threshold) {

        if (threshold <= 0 || threshold > 100) {
            return ErrorResponse.create("Illegal 'threshold'");
        }

        final List<Hash> addresses = addrss.stream().map(address -> (new Hash(address)))
                .collect(Collectors.toCollection(LinkedList::new));

        final Map<Hash, Long> balances = new HashMap<>();
        final int index;
        synchronized (Snapshot.latestSnapshotSyncObject) {
            index = instance.latestSnapshot.index();
            for (final Hash address : addresses) {
                balances.put(address,
                        instance.latestSnapshot.getState().containsKey(address) ?
                                instance.latestSnapshot.getState().get(address) : Long.valueOf(0));
            }
        }

        final Hash milestone = instance.milestone.latestSolidSubtangleMilestone;
        final int milestoneIndex = instance.milestone.latestSolidSubtangleMilestoneIndex;


        Set<Hash> analyzedTips = new HashSet<>();

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(milestone));
        Hash hash;
        while ((hash = nonAnalyzedTransactions.poll()) != null) {

            if (analyzedTips.add(hash)) {

                final TransactionViewModel transactionViewModel = transactionViewModelFromHash(instance.tangle, hash);

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

    public synchronized List<String> attachToTangleStatement(final Hash trunkTransaction, final Hash branchTransaction,
                                                                  final int minWeightMagnitude, final List<String> trytes) {
        final List<TransactionViewModel> transactionViewModels = new LinkedList<>();

        Hash prevTransaction = null;
        pearlDiver = new PearlDiver();

        for (final String tryte : trytes) {
            long startTime = System.nanoTime();
            long timestamp = System.currentTimeMillis();
            try {
                final int[] transactionTrits = Converter.trits(tryte);
                //branch and trunk
                System.arraycopy((prevTransaction == null ? trunkTransaction : prevTransaction).trits(), 0,
                        transactionTrits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                        TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
                System.arraycopy((prevTransaction == null ? branchTransaction : trunkTransaction).trits(), 0,
                        transactionTrits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                        TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);

                //attachment fields: tag and timestamps
                //tag - copy the obsolete tag to the attachment tag field only if tag isn't set.
                if(Arrays.stream(transactionTrits, TransactionViewModel.TAG_TRINARY_OFFSET, TransactionViewModel.TAG_TRINARY_OFFSET + TransactionViewModel.TAG_TRINARY_SIZE).allMatch(s -> s == 0)) {
                    System.arraycopy(transactionTrits, TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET,
                        transactionTrits, TransactionViewModel.TAG_TRINARY_OFFSET,
                        TransactionViewModel.TAG_TRINARY_SIZE);
                }

                Converter.copyTrits(timestamp,transactionTrits,TransactionViewModel.ATTACHMENT_TIMESTAMP_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_TRINARY_SIZE);
                Converter.copyTrits(0,transactionTrits,TransactionViewModel.ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE);
                Converter.copyTrits(MAX_TIMESTAMP_VALUE,transactionTrits,TransactionViewModel.ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE);

                if (!pearlDiver.search(transactionTrits, minWeightMagnitude, 0)) {
                    transactionViewModels.clear();
                    break;
                }
                //validate PoW - throws exception if invalid
                final TransactionViewModel transactionViewModel = instance.transactionValidator.validate(transactionTrits, instance.transactionValidator.getMinWeightMagnitude());

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
        return elements;
    }

    private AbstractResponse addNeighborsStatement(final List<String> uris) {
        int numberOfAddedNeighbors = 0;

        for (final String uriString : uris) {
            final URI uri = getValidUri(uriString);
            log.info("Adding neighbor: "+uriString);
            // 3rd parameter true if tcp, 4th parameter true (configured tethering)
            final Neighbor neighbor;
            if ("tcp".equals(uri.getScheme())) {
                neighbor = new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), true);
            } else {
                neighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), instance.node.getUdpSocket(), true);
            }

            if (!instance.node.getNeighbors().contains(neighbor)) {
                instance.node.getNeighbors().add(neighbor);
                numberOfAddedNeighbors++;
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
                    log.error("Lost connection to client - cannot send response");
                    exchange.endExchange();
                    sinkChannel.getWriteSetter().set(null);
                }
            else {
                exchange.endExchange();
            }
        });
        sinkChannel.resumeWrites();
    }

    private boolean validTrytes(String trytes, int length, char zeroAllowed) {
        if (trytes.length() == 0 && zeroAllowed == ZERO_LENGTH_ALLOWED) {
            return true;
        }
        if (trytes.length() != length) {
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
        String credentials = instance.configuration.string(DefaultConfSettings.REMOTE_AUTH);
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

    // TransactionViewModel, ApproveeViewModel, AddressViewModel, BundleViewModel, TagViewModel Interface
    private TransactionViewModel transactionViewModelFromHash(final Tangle tangle, final Hash hash) {
        try {
            return TransactionViewModel.fromHash(tangle, hash);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private ApproveeViewModel approveeViewModelGetApprovers(final TransactionViewModel transactionViewModel, final Tangle tangle) {
        try {
            return transactionViewModel.getApprovers(tangle);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private boolean transactionViewModelExist(final Tangle tangle, final Hash hash) {
        try {
            return TransactionViewModel.exists(tangle, hash);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private AddressViewModel addressViewModelLoad(final Tangle tangle, final Indexable indexable) {
        try {
            return AddressViewModel.load(tangle, indexable);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private BundleViewModel bundleViewModelLoad(final Tangle tangle, final Indexable indexable) {
        try {
            return BundleViewModel.load(tangle, indexable);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    private TagViewModel tagViewModelLoad(final Tangle tangle, final Indexable indexable) {
        try {
            return TagViewModel.load(tangle, indexable);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    /**
     * Gets a valid uri{@link URI} from an uri String
     *
     * @param uriString uri String
     * @return valid uri
     * @throws IllegalArgumentException if scheme is not udp or tcp
     * @throws IllegalArgumentException if uriString has invalid sintax
     */
    private URI getValidUri(final String uriString) {
        try {
            final URI uri = new URI(uriString);
            if (!"udp".equals(uri.getScheme()) && !"tcp".equals(uri.getScheme())) {
                throw new IllegalArgumentException("Invalid uri scheme");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid uri syntax");
        }
    }
}