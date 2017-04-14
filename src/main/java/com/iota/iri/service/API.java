package com.iota.iri.service;

import static io.undertow.Handlers.path;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.iota.iri.*;

import com.iota.iri.service.dto.*;
import com.iota.iri.service.viewModels.*;
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
import com.iota.iri.utils.Converter;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

@SuppressWarnings("unchecked")
public class API {

    private static final Logger log = LoggerFactory.getLogger(API.class);

    private Undertow server;

    private final Gson gson = new GsonBuilder().create();
    private final PearlDiver pearlDiver = new PearlDiver();

    private final AtomicInteger counter = new AtomicInteger(0);

    public void init() throws IOException {

        final int apiPort = Configuration.integer(DefaultConfSettings.API_PORT);
        final String apiHost = Configuration.string(DefaultConfSettings.API_HOST);

        log.debug("Binding JSON-REST API Undertown server on {}:{}", apiHost, apiPort);

        server = Undertow.builder().addHttpListener(apiPort, apiHost)
                .setHandler(path().addPrefixPath("/", new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        if (exchange.isInIoThread()) {
                            exchange.dispatch(this);
                            return;
                        }
                        processRequest(exchange);
                    }
                })).build();
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

            if (Configuration.string(DefaultConfSettings.REMOTEAPILIMIT).contains(command) &&
                    !sourceAddress.getAddress().isLoopbackAddress()) {
                return AccessLimitedResponse.create("COMMAND " + command + " is not available on this node");
            }

            log.debug("# {} -> Requesting command '{}'", counter.incrementAndGet(), command);

            switch (command) {

                case "addNeighbors": {
                    final List<String> uris = (List<String>) request.get("uris");
                    log.debug("Invoking 'addNeighbors' with {}", uris);
                    return addNeighborsStatement(uris);
                }
                case "attachToTangle": {
                    final Hash trunkTransaction = new Hash((String) request.get("trunkTransaction"));
                    final Hash branchTransaction = new Hash((String) request.get("branchTransaction"));
                    final int minWeightMagnitude = ((Double) request.get("minWeightMagnitude")).intValue();
                    final List<String> trytes = (List<String>) request.get("trytes");

                    return attachToTangleStatement(trunkTransaction, branchTransaction, minWeightMagnitude, trytes);
                }
                case "broadcastTransactions": {
                    final List<String> trytes = (List<String>) request.get("trytes");
                    log.debug("Invoking 'broadcastTransactions' with {}", trytes);
                    return broadcastTransactionStatement(trytes);
                }
                case "findTransactions": {
                    return findTransactionStatement(request);
                }
                case "getBalances": {
                    final List<String> addresses = (List<String>) request.get("addresses");
                    final int threshold = ((Double) request.get("threshold")).intValue();
                    return getBalancesStatement(addresses, threshold);
                }
                case "getInclusionStates": {
                    final List<String> trans = (List<String>) request.get("transactions");
                    final List<String> tps = (List<String>) request.get("tips");

                    if (trans == null || tps == null) {
                        return ErrorResponse.create("getInclusionStates Bad Request.");
                    }

                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operations cannot be executed: The subtangle has not been updated yet.");
                    }
                    return getInclusionStateStatement(trans, tps);
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
                            System.currentTimeMillis(), TipsViewModel.getTipHashes().length,
                            TransactionRequester.instance().numberOfTransactionsToRequest());
                }
                case "getTips": {
                    return getTipsStatement();
                }
                case "getTransactionsToApprove": {
                    final int depth = ((Double) request.get("depth")).intValue();
                    //if (invalidSubtangleStatus()) {
                    //    return ErrorResponse
                    //            .create("This operations cannot be executed: The subtangle has not been updated yet.");
                    //}
                    return getTransactionToApproveStatement(depth);
                }
                case "getTrytes": {
                    final List<String> hashes = (List<String>) request.get("hashes");
                    log.debug("Executing getTrytesStatement: {}", hashes);
                    return getTrytesStatement(hashes);
                }

                case "interruptAttachingToTangle": {
                    pearlDiver.cancel();
                    return AbstractResponse.createEmptyResponse();
                }
                case "removeNeighbors": {
                    final List<String> uris = (List<String>) request.get("uris");
                    log.debug("Invoking 'removeNeighbors' with {}", uris);
                    return removeNeighborsStatement(uris);
                }

                case "storeTransactions": {
                    List<String> trytes = (List<String>) request.get("trytes");
                    log.debug("Invoking 'storeTransactions' with {}", trytes);
                    return storeTransactionStatement(trytes);
                }
                case "rescanTransactions": {
                    TransactionRequester.instance().rescanTransactionsToRequest();
                    return AbstractResponse.createEmptyResponse();
                }
                case "getMissingTransactions": {
                    TransactionRequester.instance().rescanTransactionsToRequest();
                    synchronized (TransactionRequester.class) {
                        return GetTipsResponse.create(
                                Arrays.stream(
                                        TransactionRequester.instance().getRequestedTransactions()
                                ).map(Hash::toString).collect(Collectors.toList()));
                    }
                }
                default:
                    return ErrorResponse.create("Command [" + command + "] is unknown");
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
        uris.stream().map(Node::uri).map(Optional::get).filter(u -> "udp".equals(u.getScheme())).forEach(u -> {
            if (Node.instance().removeNeighbor(u,true)) {
                numberOfRemovedNeighbors.incrementAndGet();
            }
        });
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
        SecureRandom random = new SecureRandom();
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
        return GetTipsResponse.create(
                Arrays.stream(TipsViewModel.getTipHashes()).map(Hash::toString).collect(Collectors.toList()));
    }

    private AbstractResponse storeTransactionStatement(final List<String> trys) throws Exception {
        for (final String trytes : trys) {
            final TransactionViewModel transactionViewModel = new TransactionViewModel(Converter.trits(trytes));
            transactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
            transactionViewModel.store();
            if (Configuration.booling(DefaultConfSettings.EXPORT)) {
                String filename = "./export/" + String.valueOf(Node.getFileNumber()) + ".tx";
                PrintWriter writer = null;
                try {
                    writer = new PrintWriter(filename, "UTF-8");
                } catch (FileNotFoundException e) {
                    log.error("File export failed", e);
                } catch (UnsupportedEncodingException e) {
                    log.error("File export failed", e);
                }
                if (writer != null) {
                    writer.println(transactionViewModel.getHash().toString());
                    writer.println(Converter.trytes(transactionViewModel.trits()));
                    writer.println("local");
                    writer.close();
                }
            }
        }
        return AbstractResponse.createEmptyResponse();
    }

    private AbstractResponse getNeighborsStatement() {
        return GetNeighborsResponse.create(Node.instance().getNeighbors());
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
                bundlesTransactions.addAll(Arrays.stream(BundleViewModel.fromHash(new Hash(bundle)).getTransactionViewModels()).map(TransactionViewModel::getHash).collect(Collectors.toSet()));
            }
        }

        final Set<Hash> addressesTransactions = new HashSet<>();
        if (request.containsKey("addresses")) {
            final List<String> addresses = (List<String>) request.get("addresses");
            log.debug("Searching: {}", addresses.stream().reduce((a, b) -> a += ',' + b));

            for (final String address : addresses) {
                if (address.length() != 81) {
                    log.error("Address {} doesn't look a valid address", address);
                }
                addressesTransactions.addAll(Arrays.stream(new AddressViewModel(new Hash(address)).getTransactionHashes()).collect(Collectors.toSet()));
            }
        }

        final Set<Hash> tagsTransactions = new HashSet<>();
        if (request.containsKey("tags")) {
            for (String tag : (List<String>) request.get("tags")) {
                while (tag.length() < Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE) {
                    tag += Converter.TRYTE_ALPHABET.charAt(0);
                }
                tagsTransactions.addAll(Arrays.stream(new TagViewModel(new Hash(tag)).getTransactionHashes()).collect(Collectors.toSet()));
            }
        }

        final Set<Hash> approveeTransactions = new HashSet<>();

        if (request.containsKey("approvees")) {
            for (final String approvee : (List<String>) request.get("approvees")) {
                approveeTransactions.addAll(Arrays.stream(TransactionViewModel.fromHash(new Hash(approvee)).getApprovers()).collect(Collectors.toSet()));
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
            final TransactionViewModel transactionViewModel = new TransactionViewModel(Converter.trits(tryte));
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
        for (final Hash address : addresses) {
            balances.put(address,
                    Snapshot.latestSnapshot.getState().containsKey(address) ?
                            Snapshot.latestSnapshot.getState().get(address) : Long.valueOf(0));
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

                    if(!transactionViewModel.hasSnapshot()) {
                        if (transactionViewModel.value() != 0) {

                            final Hash address = transactionViewModel.getAddress().getHash();
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
                final TransactionViewModel transactionViewModel = new TransactionViewModel(transactionTrits);
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
                // 3rd parameter false (not tcp), 4th parameter true (configured tethering)
                final Neighbor neighbor = new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort()),false,true);
                if (!Node.instance().getNeighbors().contains(neighbor)) {
                    Node.instance().getNeighbors().add(neighbor);
                    numberOfAddedNeighbors++;
                }
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

    private static void setupResponseHeaders(final HttpServerExchange exchange) {
        final HeaderMap headerMap = exchange.getResponseHeaders();
        headerMap.add(new HttpString("Access-Control-Allow-Origin"),
                Configuration.string(DefaultConfSettings.CORS_ENABLED));
        headerMap.add(new HttpString("Keep-Alive"), "timeout=500, max=100");
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