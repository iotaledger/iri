package com.iota.iri.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.*;
import com.iota.iri.conf.APIConfig;
import com.iota.iri.conf.ConsensusConfig;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.BundleViewModel;
import com.iota.iri.controllers.TagViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.PearlDiver;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Neighbor;
import com.iota.iri.service.dto.*;
import com.iota.iri.service.tipselection.impl.WalkValidatorImpl;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.IotaIOUtils;
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
import io.undertow.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.streams.ChannelInputStream;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.undertow.Handlers.path;

@SuppressWarnings("unchecked")
public class API {

    public static final String REFERENCE_TRANSACTION_NOT_FOUND = "reference transaction not found";
    public static final String REFERENCE_TRANSACTION_TOO_OLD = "reference transaction is too old";
    private static final Logger log = LoggerFactory.getLogger(API.class);
    private final IXI ixi;
    private final int milestoneStartIndex;

    private Undertow server;

    private final Gson gson = new GsonBuilder().create();
    private volatile PearlDiver pearlDiver = new PearlDiver();

    private final AtomicInteger counter = new AtomicInteger(0);

    private Pattern trytesPattern = Pattern.compile("[9A-Z]*");

    private final static int HASH_SIZE = 81;
    private final static int TRYTES_SIZE = 2673;

    private final static long MAX_TIMESTAMP_VALUE = (long) (Math.pow(3, 27) - 1) / 2; // max positive 27-trits value

    private final int maxFindTxs;
    private final int maxRequestList;
    private final int maxGetTrytes;
    private final int maxBodyLength;
    private final boolean testNet;

    private final static String overMaxErrorMessage = "Could not complete request";
    private final static String invalidParams = "Invalid parameters";

    private ConcurrentHashMap<Hash, Boolean> previousEpochsSpentAddresses;

    private final static char ZERO_LENGTH_ALLOWED = 'Y';
    private final static char ZERO_LENGTH_NOT_ALLOWED = 'N';
    private Iota instance;

    public API(Iota instance, IXI ixi) {
        this.instance = instance;
        this.ixi = ixi;
        APIConfig configuration = instance.configuration;
        maxFindTxs = configuration.getMaxFindTransactions();
        maxRequestList = configuration.getMaxRequestsList();
        maxGetTrytes = configuration.getMaxGetTrytes();
        maxBodyLength = configuration.getMaxBodyLength();
        testNet = configuration.isTestnet();
        milestoneStartIndex = ((ConsensusConfig) configuration).getMilestoneStartIndex();

        previousEpochsSpentAddresses = new ConcurrentHashMap<>();
    }

    public void init() throws IOException {
        readPreviousEpochsSpentAddresses(testNet);

        APIConfig configuration = instance.configuration;
        final int apiPort = configuration.getPort();
        final String apiHost = configuration.getApiHost();

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

    private void readPreviousEpochsSpentAddresses(boolean isTestnet) throws IOException {
        if (isTestnet) {
            return;
        }

        String[] previousEpochsSpentAddressesFiles = instance
                .configuration
                .getPreviousEpochSpentAddressesFiles()
                .split(" ");
        for (String previousEpochsSpentAddressesFile : previousEpochsSpentAddressesFiles) {
            InputStream in = Snapshot.class.getResourceAsStream(previousEpochsSpentAddressesFile);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    this.previousEpochsSpentAddresses.put(new Hash(line), true);
                }
            } catch (Exception e) {
                log.error("Failed to load resource: {}.", previousEpochsSpentAddressesFile, e);
            }
        }
    }

    private void processRequest(final HttpServerExchange exchange) throws IOException {
        final ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

        final long beginningTime = System.currentTimeMillis();
        final String body = IotaIOUtils.toString(cis, StandardCharsets.UTF_8);
        final AbstractResponse response;

        if (!exchange.getRequestHeaders().contains("X-IOTA-API-Version")) {
            response = ErrorResponse.create("Invalid API Version");
        } else if (body.length() > maxBodyLength) {
            response = ErrorResponse.create("Request too long");
        } else {
            response = process(body, exchange.getSourceAddress());
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

            if (instance.configuration.getRemoteLimitApi().contains(command) &&
                    !sourceAddress.getAddress().isLoopbackAddress()) {
                return AccessLimitedResponse.create("COMMAND " + command + " is not available on this node");
            }

            log.debug("# {} -> Requesting command '{}'", counter.incrementAndGet(), command);

            switch (command) {
                case "storeMessage": {
                    if (!testNet) {
                        return AccessLimitedResponse.create("COMMAND storeMessage is only available on testnet");
                    }

                    if (!request.containsKey("address") || !request.containsKey("message")) {
                        return ErrorResponse.create("Invalid params");
                    }

                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operation cannot be executed: The subtangle has not been updated yet.");
                    }

                    final String address = (String) request.get("address");
                    final String message = (String) request.get("message");

                    storeMessageStatement(address, message);
                    return AbstractResponse.createEmptyResponse();
                }

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
                    final List<String> trytes = getParameterAsList(request,"trytes", TRYTES_SIZE);
                    broadcastTransactionsStatement(trytes);
                    return AbstractResponse.createEmptyResponse();
                }
                case "findTransactions": {
                    return findTransactionsStatement(request);
                }
                case "getBalances": {
                    final List<String> addresses = getParameterAsList(request,"addresses", HASH_SIZE);
                    final List<String> tips = request.containsKey("tips") ?
                            getParameterAsList(request,"tips", HASH_SIZE):
                            null;
                    final int threshold = getParameterAsInt(request, "threshold");
                    return getBalancesStatement(addresses, tips, threshold);
                }
                case "getInclusionStates": {
                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operation cannot be executed: The subtangle has not been updated yet.");
                    }
                    final List<String> transactions = getParameterAsList(request,"transactions", HASH_SIZE);
                    final List<String> tips = getParameterAsList(request,"tips", HASH_SIZE);

                    return getInclusionStatesStatement(transactions, tips);
                }
                case "getNeighbors": {
                    return getNeighborsStatement();
                }
                case "getNodeInfo": {
                    return getNodeInfoStatement();
                }
                case "getTips": {
                    return getTipsStatement();
                }
                case "getTransactionsToApprove": {
                    final Optional<Hash> reference = request.containsKey("reference") ?
                            Optional.of(new Hash (getParameterAsStringAndValidate(request,"reference", HASH_SIZE)))
                            : Optional.empty();
                    final int depth = getParameterAsInt(request, "depth");
                    if (depth < 0 || depth > instance.configuration.getMaxDepth()) {
                        return ErrorResponse.create("Invalid depth input");
                    }

                    try {
                        List<Hash> tips = getTransactionsToApproveStatement(depth, reference);
                        return GetTransactionsToApproveResponse.create(tips.get(0), tips.get(1));

                    } catch (RuntimeException e) {
                        log.info("Tip selection failed: " + e.getLocalizedMessage());
                        return ErrorResponse.create(e.getLocalizedMessage());
                    }
                }
                case "getTrytes": {
                    final List<String> hashes = getParameterAsList(request,"hashes", HASH_SIZE);
                    return getTrytesStatement(hashes);
                }

                case "interruptAttachingToTangle": {
                    return interruptAttachingToTangleStatement();
                }
                case "removeNeighbors": {
                    List<String> uris = getParameterAsList(request,"uris",0);
                    log.debug("Invoking 'removeNeighbors' with {}", uris);
                    return removeNeighborsStatement(uris);
                }

                case "storeTransactions": {
                    try {
                        final List<String> trytes = getParameterAsList(request,"trytes", TRYTES_SIZE);
                        storeTransactionsStatement(trytes);
                        return AbstractResponse.createEmptyResponse();
                    } catch (RuntimeException e) {
                        //transaction not valid
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
                case "checkConsistency": {
                    if (invalidSubtangleStatus()) {
                        return ErrorResponse
                                .create("This operation cannot be executed: The subtangle has not been updated yet.");
                    }
                    final List<String> transactions = getParameterAsList(request,"tails", HASH_SIZE);
                    return checkConsistencyStatement(transactions);
                }
                case "wereAddressesSpentFrom": {
                    final List<String> addresses = getParameterAsList(request,"addresses", HASH_SIZE);
                    return wereAddressesSpentFromStatement(addresses);
                }
                default: {
                    AbstractResponse response = ixi.processCommand(command, request);
                    return response == null ?
                            ErrorResponse.create("Command [" + command + "] is unknown") :
                            response;
                }
            }

        } catch (final ValidationException e) {
            log.info("API Validation failed: " + e.getLocalizedMessage());
            return ErrorResponse.create(e.getLocalizedMessage());
        } catch (final InvalidAlgorithmParameterException e) {
             log.info("API InvalidAlgorithmParameter passed: " + e.getLocalizedMessage());
             return ErrorResponse.create(e.getLocalizedMessage());
        } catch (final Exception e) {
            log.error("API Exception: {}", e.getLocalizedMessage(), e);
            return ExceptionResponse.create(e.getLocalizedMessage());
        }
    }

    /**
      * Check if a list of addresses was ever spent from, in the current epoch, or in previous epochs.
      *
      * @param addresses List of addresses to check if they were ever spent from.
      * @return {@link com.iota.iri.service.dto.WereAddressesSpentFrom}
      **/
    private AbstractResponse wereAddressesSpentFromStatement(List<String> addresses) throws Exception {
        final List<Hash> addressesHash = addresses.stream().map(Hash::new).collect(Collectors.toList());
        final boolean[] states = new boolean[addressesHash.size()];
        int index = 0;

        for (Hash address : addressesHash) {
            states[index++] = wasAddressSpentFrom(address);
        }
        return WereAddressesSpentFrom.create(states);
    }

    private boolean wasAddressSpentFrom(Hash address) throws Exception {
        if (previousEpochsSpentAddresses.containsKey(address)) {
            return true;
        }
        Set<Hash> hashes = AddressViewModel.load(instance.tangle, address).getHashes();
        for (Hash hash : hashes) {
            final TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, hash);
            //spend
            if (tx.value() < 0) {
                //confirmed
                if (tx.snapshotIndex() != 0) {
                    return true;
                }
                //pending
                Hash tail = findTail(hash);
                if (tail != null && BundleValidator.validate(instance.tangle, tail).size() != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private Hash findTail(Hash hash) throws Exception {
        TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, hash);
        final Hash bundleHash = tx.getBundleHash();
        long index = tx.getCurrentIndex();
        boolean foundApprovee = false;
        while (index-- > 0 && tx.getBundleHash().equals(bundleHash)) {
            Set<Hash> approvees = tx.getApprovers(instance.tangle).getHashes();
            for (Hash approvee : approvees) {
                TransactionViewModel nextTx = TransactionViewModel.fromHash(instance.tangle, approvee);
                if (nextTx.getBundleHash().equals(bundleHash)) {
                    tx = nextTx;
                    foundApprovee = true;
                    break;
                }
            }
            if (!foundApprovee) {
                break;
            }
        }
        if (tx.getCurrentIndex() == 0) {
            return tx.getHash();
        }
        return null;
    }


    /**
      * Checks the consistency of the transactions.
      * Marks state as false on the following checks<br/>
      * - Transaction does not exist<br/>
      * - Transaction is not a tail<br/>
      * - Missing a reference transaction<br/>
      * - Invalid bundle<br/>
      * - Tails of tails are invalid<br/>
      *
      * @param tails List of transactions you want to check the consistency for
      * @return {@link com.iota.iri.service.dto.CheckConsistency}
      **/
    private AbstractResponse checkConsistencyStatement(List<String> tails) throws Exception {
        final List<Hash> transactions = tails.stream().map(Hash::new).collect(Collectors.toList());
        boolean state = true;
        String info = "";

        //check transactions themselves are valid
        for (Hash transaction : transactions) {
            TransactionViewModel txVM = TransactionViewModel.fromHash(instance.tangle, transaction);
            if (txVM.getType() == TransactionViewModel.PREFILLED_SLOT) {
                return ErrorResponse.create("Invalid transaction, missing: " + transaction);
            }
            if (txVM.getCurrentIndex() != 0) {
                return ErrorResponse.create("Invalid transaction, not a tail: " + transaction);
            }


            if (!txVM.isSolid()) {
                state = false;
                info = "tails are not solid (missing a referenced tx): " + transaction;
                break;
            } else if (BundleValidator.validate(instance.tangle, txVM.getHash()).size() == 0) {
                state = false;
                info = "tails are not consistent (bundle is invalid): " + transaction;
                break;
            }
        }

        if (state) {
            instance.milestoneTracker.latestSnapshot.rwlock.readLock().lock();
            try {
                WalkValidatorImpl walkValidator = new WalkValidatorImpl(instance.tangle, instance.ledgerValidator,
                        instance.milestoneTracker, instance.configuration);
                for (Hash transaction : transactions) {
                    if (!walkValidator.isValid(transaction)) {
                        state = false;
                        info = "tails are not consistent (would lead to inconsistent ledger state or below max depth)";
                        break;
                    }
                }
            } finally {
                instance.milestoneTracker.latestSnapshot.rwlock.readLock().unlock();
            }
        }

        return CheckConsistency.create(state, info);
    }

    private double getParameterAsDouble(Map<String, Object> request, String paramName) throws ValidationException {
        validateParamExists(request, paramName);
        final double result;
        try {
                result = ((Double) request.get(paramName));
            } catch (ClassCastException e) {
                throw new ValidationException("Invalid " + paramName + " input");
            }
        return result;
    }


    private int getParameterAsInt(Map<String, Object> request, String paramName) throws ValidationException {
        validateParamExists(request, paramName);
        final int result;
        try {
            result = ((Double) request.get(paramName)).intValue();
        } catch (ClassCastException e) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
        return result;
    }

    private String getParameterAsStringAndValidate(Map<String, Object> request, String paramName, int size) throws ValidationException {
        validateParamExists(request, paramName);
        String result = (String) request.get(paramName);
        validateTrytes(paramName, size, result);
        return result;
    }

    private void validateTrytes(String paramName, int size, String result) throws ValidationException {
        if (!validTrytes(result,size,ZERO_LENGTH_NOT_ALLOWED)) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
    }

    private void validateParamExists(Map<String, Object> request, String paramName) throws ValidationException {
        if (!request.containsKey(paramName)) {
            throw new ValidationException(invalidParams);
        }
    }

    private List<String> getParameterAsList(Map<String, Object> request, String paramName, int size) throws ValidationException {
        validateParamExists(request, paramName);
        final List<String> paramList = (List<String>) request.get(paramName);
        if (paramList.size() > maxRequestList) {
            throw new ValidationException(overMaxErrorMessage);
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
        return (instance.milestoneTracker.latestSolidSubtangleMilestoneIndex == milestoneStartIndex);
    }

    /**
      * Temporarily removes a list of neighbors from your node.
      * The added neighbors will be added again after relaunching IRI.
      * Remove the neighbors from your config file or make sure you don't supply them in the -n command line option if you want to keep them removed after restart.
      *
      * The URI (Unique Resource Identification) for removing neighbors is:
      * <b>udp://IPADDRESS:PORT</b>
      *
      * Returns an {@link com.iota.iri.service.dto.ErrorResponse} if the URI scheme is wrong
      *
      * @param uris List of URI elements.
      * @return {@link com.iota.iri.service.dto.RemoveNeighborsResponse}
      **/
    private AbstractResponse removeNeighborsStatement(List<String> uris) {
        int numberOfRemovedNeighbors = 0;
        try {
            for (final String uriString : uris) {
                log.info("Removing neighbor: " + uriString);
                if (instance.node.removeNeighbor(new URI(uriString),true)) {
                    numberOfRemovedNeighbors++;
                }
            }
        } catch (URISyntaxException|RuntimeException e) {
            return ErrorResponse.create("Invalid uri scheme: " + e.getLocalizedMessage());
        }
        return RemoveNeighborsResponse.create(numberOfRemovedNeighbors);
    }

    /**
      * Returns the raw transaction data (trytes) of a specific transaction.
      * These trytes can then be easily converted into the actual transaction object.
      * See utility functions for more details.
      *
      * @param hashes List of transaction hashes you want to get trytes from.
      * @return {@link com.iota.iri.service.dto.GetTrytesResponse}
      **/
    private synchronized AbstractResponse getTrytesStatement(List<String> hashes) throws Exception {
        final List<String> elements = new LinkedList<>();
        for (final String hash : hashes) {
            final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, new Hash(hash));
            if (transactionViewModel != null) {
                elements.add(Converter.trytes(transactionViewModel.trits()));
            }
        }
        if (elements.size() > maxGetTrytes){
            return ErrorResponse.create(overMaxErrorMessage);
        }
        return GetTrytesResponse.create(elements);
    }

    private static int counterGetTxToApprove = 0;
    public static int getCounterGetTxToApprove() {
        return counterGetTxToApprove;
    }
    public static void incCounteGetTxToApprove() {
        counterGetTxToApprove++;
    }

    private static long ellapsedTime_getTxToApprove = 0L;
    public static long getEllapsedTimeGetTxToApprove() {
        return ellapsedTime_getTxToApprove;
    }
    public static void incEllapsedTimeGetTxToApprove(long ellapsedTime) {
        ellapsedTime_getTxToApprove += ellapsedTime;
    }

    /**
      * Tip selection which returns <code>trunkTransaction</code> and <code>branchTransaction</code>.
      * The input value <code>depth</code> determines how many milestones to go back for finding the transactions to approve.
      * The higher your <code>depth</code> value, the more work you have to do as you are confirming more transactions.
      * If the <code>depth</code> is too large (usually above 15, it depends on the node's configuration) an error will be returned.
      * The <code>reference</code> is an optional hash of a transaction you want to approve.
      * If it can't be found at the specified <code>depth</code> then an error will be returned.
      *
      * @param depth Number of bundles to go back to determine the transactions for approval.
      * @param reference Hash of transaction to start random-walk from, used to make sure the tips returned reference a given transaction in their past.
      * @return {@link com.iota.iri.service.dto.GetTransactionsToApproveResponse}
      **/
    public synchronized List<Hash> getTransactionsToApproveStatement(int depth, Optional<Hash> reference) throws Exception {

        if (invalidSubtangleStatus()) {
            throw new IllegalStateException("This operations cannot be executed: The subtangle has not been updated yet.");
        }

        List<Hash> tips = instance.tipsSelector.getTransactionsToApprove(depth, reference);

        if (log.isDebugEnabled()) {
            gatherStatisticsOnTipSelection();
        }

        return tips;
    }

    private void gatherStatisticsOnTipSelection() {
        API.incCounteGetTxToApprove();
        if ((getCounterGetTxToApprove() % 100) == 0) {
            String sb = "Last 100 getTxToApprove consumed " + API.getEllapsedTimeGetTxToApprove() / 1000000000L + " seconds processing time.";
            log.debug(sb);
            counterGetTxToApprove = 0;
            ellapsedTime_getTxToApprove = 0L;
        }
    }

    /**
      * Returns the list of tips.
      *
      * @return {@link com.iota.iri.service.dto.GetTipsResponse}
      **/
    private synchronized AbstractResponse getTipsStatement() throws Exception {
        return GetTipsResponse.create(instance.tipsViewModel.getTips().stream().map(Hash::toString).collect(Collectors.toList()));
    }

    /**
      * Store transactions into the local storage.
      * The trytes to be used for this call are returned by <code>attachToTangle</code>.
      *
      * @param trytes List of raw data of transactions to be rebroadcast.
      **/
    public void storeTransactionsStatement(final List<String> trytes) throws Exception {
        final List<TransactionViewModel> elements = new LinkedList<>();
        byte[] txTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);
        for (final String trytesPart : trytes) {
            //validate all trytes
            Converter.trits(trytesPart, txTrits, 0);
            final TransactionViewModel transactionViewModel = instance.transactionValidator.validateTrits(txTrits,
                    instance.transactionValidator.getMinWeightMagnitude());
            elements.add(transactionViewModel);
        }
        for (final TransactionViewModel transactionViewModel : elements) {
            //store transactions
            if(transactionViewModel.store(instance.tangle)) {
                transactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                instance.transactionValidator.updateStatus(transactionViewModel);
                transactionViewModel.updateSender("local");
                transactionViewModel.update(instance.tangle, "sender");
            }
        }
    }

    /**
      * Returns the set of neighbors you are connected with, as well as their activity statistics (or counters).
      * The activity counters are reset after restarting IRI.
      *
      * @return {@link com.iota.iri.service.dto.GetNeighborsResponse}
      **/
    private AbstractResponse getNeighborsStatement() {
        return GetNeighborsResponse.create(instance.node.getNeighbors());
    }

    /**
      * Interrupts and completely aborts the <code>attachToTangle</code> process.
      *
      * @return {@link com.iota.iri.service.dto.AbstractResponse}
      **/
    private AbstractResponse interruptAttachingToTangleStatement(){
        pearlDiver.cancel();
        return AbstractResponse.createEmptyResponse();
    }

    /**
      * Returns information about your node.
      *
      * @return {@link com.iota.iri.service.dto.GetNodeInfoResponse}
      **/
    private AbstractResponse getNodeInfoStatement(){
        String name = instance.configuration.isTestnet() ? IRI.TESTNET_NAME : IRI.MAINNET_NAME;
        return GetNodeInfoResponse.create(name, IRI.VERSION, Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().freeMemory(), System.getProperty("java.version"), Runtime.getRuntime().maxMemory(),
                Runtime.getRuntime().totalMemory(), instance.milestoneTracker.latestMilestone, instance.milestoneTracker.latestMilestoneIndex,
                instance.milestoneTracker.latestSolidSubtangleMilestone, instance.milestoneTracker.latestSolidSubtangleMilestoneIndex, instance.milestoneTracker.milestoneStartIndex,
                instance.node.howManyNeighbors(), instance.node.queuedTransactionsSize(),
                System.currentTimeMillis(), instance.tipsViewModel.size(),
                instance.transactionRequester.numberOfTransactionsToRequest());
    }

    /**
      * Get the inclusion states of a set of transactions.
      * This is for determining if a transaction was accepted and confirmed by the network or not.
      * You can search for multiple tips (and thus, milestones) to get past inclusion states of transactions.
      *
      * This API call simply returns a list of boolean values in the same order as the transaction list you submitted, thus you get a true/false whether a transaction is confirmed or not.
      * Returns an {@link com.iota.iri.service.dto.ErrorResponse} if a tip is missing or the subtangle is not solid
      *
      * @param transactions List of transactions you want to get the inclusion state for.
      * @param tips List of tips (including milestones) you want to search for the inclusion state.
      * @return {@link com.iota.iri.service.dto.GetInclusionStatesResponse}
      **/
    private AbstractResponse getInclusionStatesStatement(final List<String> transactions, final List<String> tips) throws Exception {
        final List<Hash> trans = transactions.stream().map(Hash::new).collect(Collectors.toList());
        final List<Hash> tps = tips.stream().map(Hash::new).collect(Collectors.toList());
        int numberOfNonMetTransactions = transactions.size();
        final byte[] inclusionStates = new byte[numberOfNonMetTransactions];

        List<Integer> tipsIndex = new LinkedList<>();
        {
            for(Hash tip: tps) {
                TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, tip);
                if (tx.getType() != TransactionViewModel.PREFILLED_SLOT) {
                    tipsIndex.add(tx.snapshotIndex());
                }
            }
        }
        int minTipsIndex = tipsIndex.stream().reduce((a,b) -> a < b ? a : b).orElse(0);
        if(minTipsIndex > 0) {
            int maxTipsIndex = tipsIndex.stream().reduce((a,b) -> a > b ? a : b).orElse(0);
            int count = 0;
            for(Hash hash: trans) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(instance.tangle, hash);
                if(transaction.getType() == TransactionViewModel.PREFILLED_SLOT || transaction.snapshotIndex() == 0) {
                    inclusionStates[count] = -1;
                } else if(transaction.snapshotIndex() > maxTipsIndex) {
                    inclusionStates[count] = -1;
                } else if(transaction.snapshotIndex() < maxTipsIndex) {
                    inclusionStates[count] = 1;
                }
                count++;
            }
        }

        Set<Hash> analyzedTips = new HashSet<>();
        Map<Integer, Integer> sameIndexTransactionCount = new HashMap<>();
        Map<Integer, Queue<Hash>> sameIndexTips = new HashMap<>();
        for (final Hash tip : tps) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, tip);
            if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT){
                return ErrorResponse.create("One of the tips is absent");
            }
            int snapshotIndex = transactionViewModel.snapshotIndex();
            sameIndexTips.putIfAbsent(snapshotIndex, new LinkedList<>());
            sameIndexTips.get(snapshotIndex).add(tip);
        }
        for(int i = 0; i < inclusionStates.length; i++) {
            if(inclusionStates[i] == 0) {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, trans.get(i));
                int snapshotIndex = transactionViewModel.snapshotIndex();
                sameIndexTransactionCount.putIfAbsent(snapshotIndex, 0);
                sameIndexTransactionCount.put(snapshotIndex, sameIndexTransactionCount.get(snapshotIndex) + 1);
            }
        }
        for(Integer index : sameIndexTransactionCount.keySet()) {
            Queue<Hash> sameIndexTip = sameIndexTips.get(index);
            if (sameIndexTip != null) {
                //has tips in the same index level
                if (!exhaustiveSearchWithinIndex(sameIndexTip, analyzedTips, trans, inclusionStates, sameIndexTransactionCount.get(index), index)) {
                    return ErrorResponse.create("The subtangle is not solid");
                }
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
    private boolean exhaustiveSearchWithinIndex(Queue<Hash> nonAnalyzedTransactions, Set<Hash> analyzedTips, List<Hash> transactions, byte[] inclusionStates, int count, int index) throws Exception {
        Hash pointer;
        MAIN_LOOP:
        while ((pointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedTips.add(pointer)) {
                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, pointer);
                if (transactionViewModel.snapshotIndex() == index) {
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        return false;
                    } else {
                        for (int i = 0; i < inclusionStates.length; i++) {
                            if (inclusionStates[i] < 1 && pointer.equals(transactions.get(i))) {
                                inclusionStates[i] = 1;
                                if (--count <= 0) {
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

    /**
      * Find the transactions which match the specified input and return.
      * All input values are lists, for which a list of return values (transaction hashes), in the same order, is returned for all individual elements.
      * The input fields can either be <code>bundles<code>, <code>addresses</code>, <code>tags</code> or <code>approvees</code>.
      * <b>Using multiple of these input fields returns the intersection of the values.</b>
      *
      * Returns an {@link com.iota.iri.service.dto.ErrorResponse} if more than maxFindTxs was found
      *
      * @param request the map with input fields
      * @return {@link com.iota.iri.service.dto.FindTransactionsResponse}
      **/
    private synchronized AbstractResponse findTransactionsStatement(final Map<String, Object> request) throws Exception {

        final Set<Hash> foundTransactions =  new HashSet<>();
        boolean containsKey = false;

        final Set<Hash> bundlesTransactions = new HashSet<>();
        if (request.containsKey("bundles")) {
            final HashSet<String> bundles = getParameterAsSet(request,"bundles",HASH_SIZE);
            for (final String bundle : bundles) {
                bundlesTransactions.addAll(BundleViewModel.load(instance.tangle, new Hash(bundle)).getHashes());
            }
            foundTransactions.addAll(bundlesTransactions);
            containsKey = true;
        }

        final Set<Hash> addressesTransactions = new HashSet<>();
        if (request.containsKey("addresses")) {
            final HashSet<String> addresses = getParameterAsSet(request,"addresses",HASH_SIZE);
            for (final String address : addresses) {
                addressesTransactions.addAll(AddressViewModel.load(instance.tangle, new Hash(address)).getHashes());
            }
            foundTransactions.addAll(addressesTransactions);
            containsKey = true;
        }

        final Set<Hash> tagsTransactions = new HashSet<>();
        if (request.containsKey("tags")) {
            final HashSet<String> tags = getParameterAsSet(request,"tags",0);
            for (String tag : tags) {
                tag = padTag(tag);
                tagsTransactions.addAll(TagViewModel.load(instance.tangle, new Hash(tag)).getHashes());
            }
            if (tagsTransactions.isEmpty()) {
                for (String tag : tags) {
                    tag = padTag(tag);
                    tagsTransactions.addAll(TagViewModel.loadObsolete(instance.tangle, new Hash(tag)).getHashes());
                }
            }
            foundTransactions.addAll(tagsTransactions);
            containsKey = true;
        }

        final Set<Hash> approveeTransactions = new HashSet<>();

        if (request.containsKey("approvees")) {
            final HashSet<String> approvees = getParameterAsSet(request,"approvees",HASH_SIZE);
            for (final String approvee : approvees) {
                approveeTransactions.addAll(TransactionViewModel.fromHash(instance.tangle, new Hash(approvee)).getApprovers(instance.tangle).getHashes());
            }
            foundTransactions.addAll(approveeTransactions);
            containsKey = true;
        }

        if (!containsKey) {
            throw new ValidationException(invalidParams);
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

    private String padTag(String tag) throws ValidationException {
        while (tag.length() < HASH_SIZE) {
            tag += Converter.TRYTE_ALPHABET.charAt(0);
        }
        if (tag.equals(Hash.NULL_HASH.toString())) {
            throw new ValidationException("Invalid tag input");
        }
        return tag;
    }

    private HashSet<String> getParameterAsSet(Map<String, Object> request, String paramName, int size) throws ValidationException {

        HashSet<String> result = getParameterAsList(request,paramName,size).stream().collect(Collectors.toCollection(HashSet::new));
        if (result.contains(Hash.NULL_HASH.toString())) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
        return result;
    }

    /**
      * Broadcast a list of transactions to all neighbors.
      * The input trytes for this call are provided by <code>attachToTangle</code>.
      *
      * @param trytes the list of transaction
      **/
    public void broadcastTransactionsStatement(final List<String> trytes) {
        final List<TransactionViewModel> elements = new LinkedList<>();
        byte[] txTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);
        for (final String tryte : trytes) {
            //validate all trytes
            Converter.trits(tryte, txTrits, 0);
            final TransactionViewModel transactionViewModel = instance.transactionValidator.validateTrits(txTrits, instance.transactionValidator.getMinWeightMagnitude());
            elements.add(transactionViewModel);
        }
        for (final TransactionViewModel transactionViewModel : elements) {
            //push first in line to broadcast
            transactionViewModel.weightMagnitude = Curl.HASH_LENGTH;
            instance.node.broadcast(transactionViewModel);
        }
    }


    /**
      * Returns the confirmed balance, as viewed by the specified <code>tips</code>. If you do not specify the referencing <code>tips</code>, the returned balance is based on the latest confirmed milestone.
      * In addition to the balances, it also returns the referencing <code>tips</code> (or milestone), as well as the index with which the confirmed balance was determined.
      * The balances are returned as a list in the same order as the addresses were provided as input.
      *
      * Returns an {@link com.iota.iri.service.dto.ErrorResponse} if tips are not found or inconsistent, or the treshold is invalid
      *
      * @param addresses the address to get the balance for
      * @param tips the tips to find the balance through
      * @param threshold the confirmation threshold between 0 and 100(incl)
      * @return {@link com.iota.iri.service.dto.GetBalancesResponse}
      **/
    private AbstractResponse getBalancesStatement(final List<String> addresses, final List<String> tips, final int threshold) throws Exception {

        if (threshold <= 0 || threshold > 100) {
            return ErrorResponse.create("Illegal 'threshold'");
        }

        final List<Hash> addressList = addresses.stream().map(address -> (new Hash(address)))
                .collect(Collectors.toCollection(LinkedList::new));
        final List<Hash> hashes;
        final Map<Hash, Long> balances = new HashMap<>();
        instance.milestoneTracker.latestSnapshot.rwlock.readLock().lock();
        final int index = instance.milestoneTracker.latestSnapshot.index();
        if (tips == null || tips.size() == 0) {
            hashes = Collections.singletonList(instance.milestoneTracker.latestSolidSubtangleMilestone);
        } else {
            hashes = tips.stream().map(address -> (new Hash(address)))
                    .collect(Collectors.toCollection(LinkedList::new));
        }
        try {
            for (final Hash address : addressList) {
                Long value = instance.milestoneTracker.latestSnapshot.getBalance(address);
                if (value == null) {
                    value = 0L;
                }
                balances.put(address, value);
            }

            final Set<Hash> visitedHashes;
            final Map<Hash, Long> diff;

            visitedHashes = new HashSet<>();
            diff = new HashMap<>();
            for (Hash tip : hashes) {
                if (!TransactionViewModel.exists(instance.tangle, tip)) {
                    return ErrorResponse.create("Tip not found: " + tip.toString());
                }
                if (!instance.ledgerValidator.updateDiff(visitedHashes, diff, tip)) {
                    return ErrorResponse.create("Tips are not consistent");
                }
            }
            diff.forEach((key, value) -> balances.computeIfPresent(key, (hash, aLong) -> value + aLong));
        } finally {
            instance.milestoneTracker.latestSnapshot.rwlock.readLock().unlock();
        }

        final List<String> elements = addresses.stream().map(address -> balances.get(address).toString())
                .collect(Collectors.toCollection(LinkedList::new));

        return GetBalancesResponse.create(elements, hashes.stream().map(h -> h.toString()).collect(Collectors.toList()), index);
    }

    private static int counter_PoW = 0;
    public static int getCounterPoW() {
        return counter_PoW;
    }
    public static void incCounterPoW() {
        API.counter_PoW++;
    }

    private static long ellapsedTime_PoW = 0L;
    public static long getEllapsedTimePoW() {
        return ellapsedTime_PoW;
    }
    public static void incEllapsedTimePoW(long ellapsedTime) {
        ellapsedTime_PoW += ellapsedTime;
    }

    /**
      * Attaches the specified transactions (trytes) to the Tangle by doing Proof of Work.
      * You need to supply <code>branchTransaction</code> as well as <code>trunkTransaction</code> (the tips which you're going to validate and reference with this transaction) - both of which you'll get through the <code>getTransactionsToApprove</code> API call.
      *
      * The returned value is a different set of tryte values which you can input into <code>broadcastTransactions</code> and <code>storeTransactions</code>.
      * The last 243 trytes of the return value consist of the: <code>trunkTransaction</code> + <code>branchTransaction</code> + <code>nonce</code>.
      * These are valid trytes which are then accepted by the network.
      *
      * @param trunkTransaction the trunk transaction
      * @param branchTransaction the branch transaction
      * @param minWeightMagnitude the minimum weight magnitute
      * @param trytes the list of trytes to attach
      * @return trytes the list of transactions in trytes
      **/
    public synchronized List<String> attachToTangleStatement(final Hash trunkTransaction, final Hash branchTransaction,
                                                                  final int minWeightMagnitude, final List<String> trytes) {
        final List<TransactionViewModel> transactionViewModels = new LinkedList<>();

        Hash prevTransaction = null;
        pearlDiver = new PearlDiver();

        byte[] transactionTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);

        for (final String tryte : trytes) {
            long startTime = System.nanoTime();
            long timestamp = System.currentTimeMillis();
            try {
                Converter.trits(tryte, transactionTrits, 0);
                //branch and trunk
                System.arraycopy((prevTransaction == null ? trunkTransaction : prevTransaction).trits(), 0,
                        transactionTrits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                        TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
                System.arraycopy((prevTransaction == null ? branchTransaction : trunkTransaction).trits(), 0,
                        transactionTrits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                        TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);

                //attachment fields: tag and timestamps
                //tag - copy the obsolete tag to the attachment tag field only if tag isn't set.
                if(IntStream.range(TransactionViewModel.TAG_TRINARY_OFFSET, TransactionViewModel.TAG_TRINARY_OFFSET + TransactionViewModel.TAG_TRINARY_SIZE).allMatch(idx -> transactionTrits[idx]  == ((byte) 0))) {
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
                final TransactionViewModel transactionViewModel = instance.transactionValidator.validateTrits(transactionTrits, instance.transactionValidator.getMinWeightMagnitude());

                transactionViewModels.add(transactionViewModel);
                prevTransaction = transactionViewModel.getHash();
            } finally {
                API.incEllapsedTimePoW(System.nanoTime() - startTime);
                API.incCounterPoW();
                if ( ( API.getCounterPoW() % 100) == 0 ) {
                    String sb = "Last 100 PoW consumed " +
                            API.getEllapsedTimePoW() / 1000000000L +
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

    /**
      * Temporarily add a list of neighbors to your node.
      * The added neighbors will be removed after relaunching IRI.
      * Add the neighbors to your config file or supply them in the -n command line option if you want to keep them after restart.
      *
      * The URI (Unique Resource Identification) for adding neighbors is:
      * <b>udp://IPADDRESS:PORT</b>
      *
      * @param uris list of neighbors to add
      * @return {@link com.iota.iri.service.dto.AddedNeighborsResponse}
      **/
    private AbstractResponse addNeighborsStatement(final List<String> uris) {
        int numberOfAddedNeighbors = 0;
        try {
            for (final String uriString : uris) {
                log.info("Adding neighbor: " + uriString);
                final Neighbor neighbor = instance.node.newNeighbor(new URI(uriString), true);
                if (!instance.node.getNeighbors().contains(neighbor)) {
                    instance.node.getNeighbors().add(neighbor);
                    numberOfAddedNeighbors++;
                }
            }
        } catch (URISyntaxException|RuntimeException e) {
            return ErrorResponse.create("Invalid uri scheme: " + e.getLocalizedMessage());
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
            if (responseBuf.remaining() > 0) {
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
        String credentials = instance.configuration.getRemoteAuth();
        if (credentials == null || credentials.isEmpty()) {
            return toWrap;
        }

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

    /**
      * <b>Only available on testnet.</b>
      * Creates, attaches, and broadcasts a transaction with this message
      *
      * @param address The address to add the message to
      * @param message The message to store
      **/
    private synchronized void storeMessageStatement(final String address, final String message) throws Exception {
        final List<Hash> txToApprove = getTransactionsToApproveStatement(3, Optional.empty());

        final int txMessageSize = TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE / 3;

        final int txCount = (message.length() + txMessageSize - 1) / txMessageSize;

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
                tx = message.substring(i * txMessageSize, (i + 1) * txMessageSize);
            } else {
                tx = message.substring(i * txMessageSize);
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

// let's calculate the bundle essence :S
        int startIdx = TransactionViewModel.ESSENCE_TRINARY_OFFSET / 3;
        Sponge sponge = SpongeFactory.create(SpongeFactory.Mode.KERL);

        for (String tx : transactions) {
            String essence = tx.substring(startIdx);
            byte[] essenceTrits = new byte[essence.length() * Converter.NUMBER_OF_TRITS_IN_A_TRYTE];
            Converter.trits(essence, essenceTrits, 0);
            sponge.absorb(essenceTrits, 0, essenceTrits.length);
        }

        byte[] essenceTrits = new byte[243];
        sponge.squeeze(essenceTrits, 0, essenceTrits.length);
        final String bundleHash = Converter.trytes(essenceTrits, 0, essenceTrits.length);

        transactions = transactions.stream().map(tx -> StringUtils.rightPad(tx + bundleHash, TRYTES_SIZE, '9')).collect(Collectors.toList());

// do pow
        List<String> powResult = attachToTangleStatement(txToApprove.get(0), txToApprove.get(1), 9, transactions);
        broadcastTransactionsStatement(powResult);
    }
}
