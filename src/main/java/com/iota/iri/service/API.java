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
    private final int maxGetTrytes;

    private final static char ZERO_LENGTH_ALLOWED = 'Y';
    private final static char ZERO_LENGTH_NOT_ALLOWED = 'N';
    private Iota instance;

    public API(Iota instance, IXI ixi) {
        this.instance = instance;
        this.ixi = ixi;
        minRandomWalks = instance.configuration.integer(DefaultConfSettings.MIN_RANDOM_WALKS);
        maxRandomWalks = instance.configuration.integer(DefaultConfSettings.MAX_RANDOM_WALKS);
        maxFindTxs = instance.configuration.integer(DefaultConfSettings.MAX_FIND_TRANSACTIONS);
        maxGetTrytes = instance.configuration.integer(DefaultConfSettings.MAX_GET_TRYTES);

    }

    public void init() throws IOException {
        final int apiPort = instance.configuration.integer(DefaultConfSettings.PORT);
        final String apiHost = instance.configuration.string(DefaultConfSettings.API_HOST);

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
        final AbstractResponse response;
        if (exchange.getRequestHeaders().contains("X-IOTA-API-Version")) {
            response = process(body, exchange.getSourceAddress());
        } else {
            response = ErrorResponse.create("Invalid API Version");
        }
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

            if (instance.configuration.string(DefaultConfSettings.REMOTE_LIMIT_API).contains(command) &&
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

                    return AddedNeighborsResponse.create(instance.abi.addNeighborsStatement(uris));
                }
                case "attachToTangle": {
                    if (!request.containsKey("trunkTransaction") ||
                            !request.containsKey("branchTransaction") ||
                            !request.containsKey("minWeightMagnitude") ||
                            !request.containsKey("trytes")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    if (!validTrytes((String)request.get("trunkTransaction"), HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                        return ErrorResponse.create("Invalid trunkTransaction hash");
                    }
                    if (!validTrytes((String)request.get("branchTransaction"), HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                        return ErrorResponse.create("Invalid branchTransaction hash");
                    }
                    final Hash trunkTransaction = new Hash((String) request.get("trunkTransaction"));
                    final Hash branchTransaction = new Hash((String) request.get("branchTransaction"));
                    final int minWeightMagnitude;
                    try {
                        minWeightMagnitude = ((Double) request.get("minWeightMagnitude")).intValue();
                    } catch (ClassCastException e) {
                        return ErrorResponse.create("Invalid minWeightMagnitude input");
                    }
                    final List<String> trytes = (List<String>) request.get("trytes");
                    for (final String tryt : trytes) {
                        if (!validTrytes(tryt, TRYTES_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                            return ErrorResponse.create("Invalid trytes input");
                        }
                    }
                    List<String> elements = instance.abi.attachToTangleStatement(trunkTransaction, branchTransaction, minWeightMagnitude, trytes);
                    return AttachToTangleResponse.create(elements);
                }
                case "broadcastTransactions": {
                    if (!request.containsKey("trytes")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> trytes = (List<String>) request.get("trytes");
                    for (final String tryt : trytes) {
                        if (!validTrytes(tryt, TRYTES_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                            return ErrorResponse.create("Invalid trytes input");
                        }
                    }
                    instance.abi.broadcastTransactionStatement(trytes);
                    return AbstractResponse.createEmptyResponse();
                }
                case "findTransactions": {
                    if (!request.containsKey("bundles") &&
                            !request.containsKey("addresses") &&
                            !request.containsKey("tags") &&
                            !request.containsKey("approvees")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    return FindTransactionsResponse.create(instance.abi.findTransactionStatement(request));
                }
                case "getBalances": {
                    if (!request.containsKey("addresses") || !request.containsKey("threshold")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> addresses = (List<String>) request.get("addresses");
                    for (final String address : addresses) {
                        if (!validTrytes(address, HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                            return ErrorResponse.create("Invalid addresses input");
                        }
                    }
                    final int threshold = ((Double) request.get("threshold")).intValue();
                    return GetBalancesResponse.create(instance.abi.getBalancesStatement(addresses, threshold));
                }
                case "getInclusionStates": {
                    if (!request.containsKey("transactions") || !request.containsKey("tips")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> trans = (List<String>) request.get("transactions");
                    final List<String> tps = (List<String>) request.get("tips");

                    for (final String tx : trans) {
                        if (!validTrytes(tx, HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                            return ErrorResponse.create("Invalid transactions input");
                        }
                    }
                    for (final String ti : tps) {
                        if (!validTrytes(ti, HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                            return ErrorResponse.create("Invalid tips input");
                        }
                    }

                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operations cannot be executed: The subtangle has not been updated yet.");
                    }
                    return GetInclusionStatesResponse.create(instance.abi.getNewInclusionStateStatement(trans, tps));
                }
                case "getNeighbors": {
                    return GetNeighborsResponse.create(instance.node.getNeighbors());
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
                    return GetTipsResponse.create(instance.tipsViewModel.getTips().stream().map(Hash::toString).collect(Collectors.toList()));
                }
                case "getTransactionsToApprove": {
                    final int depth;
                    try {
                        depth = ((Double) request.get("depth")).intValue();
                    } catch (ClassCastException e) {
                        return ErrorResponse.create("Invalid depth input");
                    }
                    final Object referenceObj = request.get("reference");
                    final String reference = referenceObj == null? null: (String) referenceObj;
                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operations cannot be executed: The subtangle has not been updated yet.");
                    }
                    final Object numWalksObj = request.get("numWalks");
                    int numWalks = numWalksObj == null? 1 : ((Double) numWalksObj).intValue();
                    if(numWalks < minRandomWalks) {
                        numWalks = minRandomWalks;
                    }
                    final Hash[] tips = instance.abi.getTransactionToApproveStatement(depth, reference, numWalks);
                    if(tips == null) {
                        return ErrorResponse.create("The subtangle is not solid");
                    }
                    return GetTransactionsToApproveResponse.create(tips[0], tips[1]);
                }
                case "getTrytes": {
                    if (!request.containsKey("hashes")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> hashes = (List<String>) request.get("hashes");
                    if (hashes == null) {
                        return ErrorResponse.create("Wrong arguments");
                    }
                    for (final String hash : hashes) {
                        if (!validTrytes(hash, HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED))  {
                            return ErrorResponse.create("Invalid hash input");
                        }
                    }
                    return GetTrytesResponse.create(instance.abi.getTrytesStatement(hashes));
                }

                case "interruptAttachingToTangle": {
                    instance.abi.pearlDiver.cancel();
                    return AbstractResponse.createEmptyResponse();
                }
                case "removeNeighbors": {
                    if (!request.containsKey("uris")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    final List<String> uris = (List<String>) request.get("uris");
                    log.debug("Invoking 'removeNeighbors' with {}", uris);
                    return RemoveNeighborsResponse.create(instance.abi.removeNeighborsStatement(uris));
                }

                case "storeTransactions": {
                    if (!request.containsKey("trytes")) {
                        return ErrorResponse.create("Invalid params");
                    }
                    List<String> trytes = (List<String>) request.get("trytes");
                    log.debug("Invoking 'storeTransactions' with {}", trytes);
                    if(instance.abi.storeTransactionStatement(trytes)) {
                        return AbstractResponse.createEmptyResponse();
                    } else {
                        return ErrorResponse.create("Invalid trytes input");
                    }
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

        } catch (final Exception e) {
            log.error("API Exception: ", e);
            return ExceptionResponse.create(e.getLocalizedMessage());
        }
    }

    public boolean invalidSubtangleStatus() {
        return (instance.milestone.latestSolidSubtangleMilestoneIndex == Milestone.MILESTONE_START_INDEX);
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

    private boolean validTrytes(String trytes, int minimalLength, char zeroAllowed) {
        if (trytes.length() == 0 && zeroAllowed == ZERO_LENGTH_ALLOWED) {
            return true;
        }
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
}