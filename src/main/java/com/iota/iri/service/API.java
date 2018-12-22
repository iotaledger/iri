package com.iota.iri.service;

import com.iota.iri.*;
import com.iota.iri.conf.APIConfig;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.BundleViewModel;
import com.iota.iri.controllers.TagViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.Curl;
import com.iota.iri.crypto.PearlDiver;
import com.iota.iri.crypto.Sponge;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.Neighbor;
import com.iota.iri.service.dto.*;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.tipselection.TipSelector;
import com.iota.iri.service.tipselection.impl.WalkValidatorImpl;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.IotaIOUtils;
import com.iota.iri.utils.MapIdentityManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

import static io.undertow.Handlers.path;

/**
 * <p>
 *   The API makes it possible to interact with the node by requesting information or actions to be taken.
 *   You can interact with it by passing a JSON object which at least contains a <tt>command</tt>.
 *   Upon successful execution of the command, the API returns your requested information in an {@link AbstractResponse}.
 * </p>
 * <p>
 *   If the request is invalid, an {@link ErrorResponse} is returned.
 *   This, for example, happens when the command does not exist or there is no command section at all.
 *   If there is an error in the given data during the execution of a command, an {@link ErrorResponse} is also sent.
 * </p>
 * <p>
 *   If an Exception is thrown during the execution of a command, an {@link ExceptionResponse} is returned.
 * </p>
 */
@SuppressWarnings("unchecked")
public class API {

    public static final String REFERENCE_TRANSACTION_NOT_FOUND = "reference transaction not found";
    public static final String REFERENCE_TRANSACTION_TOO_OLD = "reference transaction is too old";

    public static final String INVALID_SUBTANGLE = "This operation cannot be executed: "
                                                 + "The subtangle has not been updated yet.";

    private static final Logger log = LoggerFactory.getLogger(API.class);
    private final IXI ixi;

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

    private final String[] features;

    /**
     * Starts loading the IOTA API, parameters do not have to be initialized.
     *
     * @param instance The data source we interact with during any API call.
     * @param ixi If a command is not in the standard API,
     *        we try to process it as a Nashorn JavaScript module through {@link IXI}
     */
    public API(Iota instance, IXI ixi) {
        this.instance = instance;
        this.ixi = ixi;
        APIConfig configuration = instance.configuration;
        maxFindTxs = configuration.getMaxFindTransactions();
        maxRequestList = configuration.getMaxRequestsList();
        maxGetTrytes = configuration.getMaxGetTrytes();
        maxBodyLength = configuration.getMaxBodyLength();
        testNet = configuration.isTestnet();

        previousEpochsSpentAddresses = new ConcurrentHashMap<>();

        features = Feature.calculateFeatureNames(instance.configuration);
    }

    /**
     * Prepares the IOTA API for usage. Until this method is called, no HTTP requests can be made.
     * The order of loading is as follows
     * <ol>
     *    <li>
     *        Read the spend addresses from the previous epoch. Used in {@link #wasAddressSpentFrom(Hash)}.
     *        This only happens if {@link APIConfig#isTestnet()} is <tt>false</tt>
     *        If reading from the previous epoch fails, a log is printed. The API will continue to initialize.
     *    </li>
     *    <li>
     *        Get the {@link APIConfig} from the {@link Iota} instance,
     *        and read {@link APIConfig#getPort()} and {@link APIConfig#getApiHost()}
     *    </li>
     *    <li>
     *        Builds a secure {@link Undertow} server with the port and host.
     *        If {@link APIConfig#getRemoteAuth()} is defined, remote authentication is blocked for anyone except
     *         those defined in {@link APIConfig#getRemoteAuth()} or localhost.
     *        This is done with {@link BasicAuthenticationMechanism} in a {@link AuthenticationMode#PRO_ACTIVE} mode.
     *        By default, this authentication is disabled.
     *    </li>
     *    <li>
     *        Starts the server, opening it for HTTP API requests
     *    </li>
     * </ol>
     *
     * @throws IOException If we are not on the testnet, and the previousEpochsSpentAddresses files cannot be found.
     *                     Currently this exception is caught in {@link #readPreviousEpochsSpentAddresses(boolean)}
     */
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

    /**
     * Read the spend addresses from the previous epoch. Used in {@link #wasAddressSpentFrom(Hash)}.
     * If this fails, a log is printed. The API will continue to initialize.
     *
     * @param isTestnet If this node is running on the testnet. If this is <tt>true</tt>, nothing is loaded.
     * @throws IOException If we are not on the testnet and previousEpochsSpentAddresses files cannot be found.
     *                     Currently this exception is caught in {@link #readPreviousEpochsSpentAddresses(boolean)}
     */
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
                    this.previousEpochsSpentAddresses.put(HashFactory.ADDRESS.create(line), true);
                }
            } catch (Exception e) {
                log.error("Failed to load resource: {}.", previousEpochsSpentAddressesFile, e);
            }
        }
    }

    /**
     * Sends the API response back as JSON to the requester.
     * Status code of the HTTP request is also set according to the type of response.
     * <ul>
     *     <li>{@link ErrorResponse}: 400</li>
     *     <li>{@link AccessLimitedResponse}: 401</li>
     *     <li>{@link ExceptionResponse}: 500</li>
     *     <li>Default: 200</li>
     * </ul>
     *
     * @param exchange Contains information about what the client sent to us
     * @param res The response of the API.
     *            See {@link #processRequest(HttpServerExchange)}
     *            and {@link #process(String, InetSocketAddress)} for the different responses in each case.
     * @param beginningTime The time when we received the request, in milliseconds.
     *                      This will be used to set the response duration in {@link AbstractResponse#setDuration(Integer)}
     * @throws IOException When connection to client has been lost - Currently being caught.
     */
    private void sendResponse(HttpServerExchange exchange, AbstractResponse res, long beginningTime) throws IOException {
        res.setDuration((int) (System.currentTimeMillis() - beginningTime));
        final String response = gson.toJson(res);

        if (res instanceof ErrorResponse) {
            // bad request or invalid parameters
            exchange.setStatusCode(400);
        } else if (res instanceof AccessLimitedResponse) {
            // API method not allowed
            exchange.setStatusCode(401);
        } else if (res instanceof ExceptionResponse) {
            // internal error
            exchange.setStatusCode(500);
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

    /**
     * <p>
     *     Processes an API HTTP request.
     *     No checks have been done until now, except that it is not an OPTIONS request.
     *     We can be sure that we are in a thread that allows blocking.
     * </p>
     * <p>
     *     The request process duration is recorded.
     *     During this the request gets verified. If it is incorrect, an {@link ErrorResponse} is made.
     *     Otherwise it is processed in {@link #process(String, InetSocketAddress)}.
     *     The result is sent back to the requester.
     * </p>
     *
     * @param exchange Contains the data the client sent to us
     * @throws IOException If the body of this HTTP request cannot be read
     */
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

    /**
     * Handles an API request body.
     * Its returned {@link AbstractResponse} is created using the following logic
     * <ul>
     *     <li>
     *         {@link ExceptionResponse} if the body cannot be parsed.
     *     </li>
     *     <li>
     *         {@link ErrorResponse} if the body does not contain a '<tt>command</tt>' section.
     *     </li>
     *     <li>
     *         {@link AccessLimitedResponse} if the command is not allowed on this node.
     *     </li>
     *     <li>
     *         {@link ErrorResponse} if the command contains invalid parameters.
     *     </li>
     *     <li>
     *         {@link ExceptionResponse} if we encountered an unexpected exception during command processing.
     *     </li>
     *     <li>
     *         {@link AbstractResponse} when the command is successfully processed.
     *         The response class depends on the command executed.
     *     </li>
     * </ul>
     *
     * @param requestString The JSON encoded data of the request.
     *                      This String is attempted to be converted into a {@code Map<String, Object>}.
     * @param sourceAddress The address from the sender of this API request.
     * @return The result of this request.
     * @throws UnsupportedEncodingException If the requestString cannot be parsed into a Map.
                                            Currently caught and turned into a {@link ExceptionResponse}.
     */
    private AbstractResponse process(final String requestString, InetSocketAddress sourceAddress)
            throws UnsupportedEncodingException {

        try {
            // Request JSON data into map
            final Map<String, Object> request = gson.fromJson(requestString, Map.class);
            if (request == null) {
                return ExceptionResponse.create("Invalid request payload: '" + requestString + "'");
            }

            // Did the requester ask for a command?
            final String command = (String) request.get("command");
            if (command == null) {
                return ErrorResponse.create("COMMAND parameter has not been specified in the request.");
            }

            // Is this command allowed to be run from this request address?
            // We check the remote limit API configuration.
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

                    String address = (String) request.get("address");
                    String message = (String) request.get("message");
                    return storeMessageStatement(address, message);
                }

                case "addNeighbors": {
                    List<String> uris = getParameterAsList(request,"uris",0);
                    log.debug("Invoking 'addNeighbors' with {}", uris);
                    return addNeighborsStatement(uris);
                }
                case "attachToTangle": {
                    final Hash trunkTransaction  = HashFactory.TRANSACTION.create(getParameterAsStringAndValidate(request,"trunkTransaction", HASH_SIZE));
                    final Hash branchTransaction = HashFactory.TRANSACTION.create(getParameterAsStringAndValidate(request,"branchTransaction", HASH_SIZE));
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
                        return ErrorResponse.create(INVALID_SUBTANGLE);
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
                    Optional<Hash> reference = request.containsKey("reference") ?
                        Optional.of(HashFactory.TRANSACTION.create(getParameterAsStringAndValidate(request,"reference", HASH_SIZE)))
                        : Optional.empty();
                    int depth = getParameterAsInt(request, "depth");

                    return getTransactionsToApproveStatement(depth, reference);
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
                        return ErrorResponse.create(INVALID_SUBTANGLE);
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
     * If an address has a pending transaction, it is also marked as spend.
     *
     * @param addresses List of addresses to check if they were ever spent from.
     **/
    private AbstractResponse wereAddressesSpentFromStatement(List<String> addresses) throws Exception {
        final List<Hash> addressesHash = addresses.stream()
                .map(HashFactory.ADDRESS::create)
                .collect(Collectors.toList());

        final boolean[] states = new boolean[addressesHash.size()];
        int index = 0;

        for (Hash address : addressesHash) {
            states[index++] = wasAddressSpentFrom(address);
        }
        return WereAddressesSpentFrom.create(states);
    }

    /**
     * Checks if the address was ever spent from, in the current epoch, or in previous epochs.
     * If an address has a pending transaction, it is also marked as spent.
     *
     * @param address The address to check if it was ever spent from.
     * @return <tt>true</tt> if it was spent from, otherwise <tt>false</tt>
     * @throws Exception When a model could not be loaded.
     */
    private boolean wasAddressSpentFrom(Hash address) throws Exception {
        if (previousEpochsSpentAddresses.containsKey(address)) {
            return true;
        }

        Set<Hash> hashes = AddressViewModel.load(instance.tangle, address).getHashes();
        for (Hash hash : hashes) {
            final TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, hash);
            // Check for spending transactions
            if (tx.value() < 0) {
                // Transaction is confirmed
                if (tx.snapshotIndex() != 0) {
                    return true;
                }

                // Transaction is pending
                Hash tail = findTail(hash);
                if (tail != null && BundleValidator.validate(instance.tangle, instance.snapshotProvider.getInitialSnapshot(), tail).size() != 0) {
                    return true;
                }
            }
        }

        // No spending transaction found
        return false;
    }

    /**
     * Walks back from the hash until a tail transaction has been found or transaction aprovee is not found.
     * A tail transaction is the first transaction in a bundle, thus with <code>index = 0</code>
     *
     * @param hash The transaction hash where we start the search from. If this is a tail, its hash is returned.
     * @return The transaction hash of the tail
     * @throws Exception When a model could not be loaded.
     */
    private Hash findTail(Hash hash) throws Exception {
        TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, hash);
        final Hash bundleHash = tx.getBundleHash();
        long index = tx.getCurrentIndex();
        boolean foundApprovee = false;

        // As long as the index is bigger than 0 and we are still traversing the same bundle
        // If the hash we asked about is already a tail, this loop never starts
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
     *
     * Checks the consistency of the transactions.
     * Marks state as false on the following checks:
     * <ul>
     *     <li>Missing a reference transaction</li>
     *     <li>Invalid bundle</li>
     *     <li>Tails of tails are invalid</li>
     * </ul>
     *
     * If a transaction does not exist, or it is not a tail, an {@link ErrorResponse} is returned.
     *
     * @param transactionsList Transactions you want to check the consistency for
     * @return {@link CheckConsistency}
     **/
    private AbstractResponse checkConsistencyStatement(List<String> transactionsList) throws Exception {
        final List<Hash> transactions = transactionsList.stream().map(HashFactory.TRANSACTION::create).collect(Collectors.toList());
        boolean state = true;
        String info = "";

        // Check if the transactions themselves are valid
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
            } else if (BundleValidator.validate(instance.tangle, instance.snapshotProvider.getInitialSnapshot(), txVM.getHash()).size() == 0) {
                state = false;
                info = "tails are not consistent (bundle is invalid): " + transaction;
                break;
            }
        }

        // Transactions are valid, lets check ledger consistency
        if (state) {
            instance.snapshotProvider.getLatestSnapshot().lockRead();
            try {
                WalkValidatorImpl walkValidator = new WalkValidatorImpl(instance.tangle, instance.snapshotProvider, instance.ledgerService,
                        instance.configuration);
                for (Hash transaction : transactions) {
                    if (!walkValidator.isValid(transaction)) {
                        state = false;
                        info = "tails are not consistent (would lead to inconsistent ledger state or below max depth)";
                        break;
                    }
                }
            } finally {
                instance.snapshotProvider.getLatestSnapshot().unlockRead();
            }
        }

        return CheckConsistency.create(state, info);
    }

    /**
     * Compares the last received confirmed milestone with the last global snapshot milestone.
     * If these are equal, it means the tangle is empty and therefore invalid.
     *
     * @return <tt>false</tt> if we received at least a solid milestone, otherwise <tt>true</tt>
     */
    public boolean invalidSubtangleStatus() {
        return (instance.snapshotProvider.getLatestSnapshot().getIndex() == instance.snapshotProvider.getInitialSnapshot().getIndex());
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
     * Temporarily add a list of neighbors to your node.
     * The added neighbors will not be available after restart.
     * Add the neighbors to your config file
     * or supply them in the <tt>-n</tt> command line option if you want to add them permanently.
     *
     * The URI (Unique Resource Identification) for adding neighbors is:
     * <b>udp://IPADDRESS:PORT</b>
     *
     * @param uris list of neighbors to add
     * @return {@link com.iota.iri.service.dto.AddedNeighborsResponse}
     **/
   private AbstractResponse addNeighborsStatement(List<String> uris) {
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
      * @param uris The URIs of the neighbors we want to remove.
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
      * See utility and {@link Transaction} functions in an IOTA library for more details.
      *
      * @param hashes The transaction hashes you want to get trytes from.
      * @return {@link com.iota.iri.service.dto.GetTrytesResponse}
      **/
    private synchronized AbstractResponse getTrytesStatement(List<String> hashes) throws Exception {
        final List<String> elements = new LinkedList<>();
        for (final String hash : hashes) {
            final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, HashFactory.TRANSACTION.create(hash));
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

    /**
     * Can be 0 or more, and is set to 0 every 100 requests.
     * Each increase indicates another 2 tips send.
     *
     * @return The current amount of times this node has returned transactions to approve
     */
    private static int getCounterGetTxToApprove() {
        return counterGetTxToApprove;
    }

    /**
     * Increases the amount of tips send for transactions to approve by one
     */
    private static void incCounterGetTxToApprove() {
        counterGetTxToApprove++;
    }

    private static long ellapsedTime_getTxToApprove = 0L;

    /**
     * Can be 0 or more, and is set to 0 every 100 requests.
     *
     * @return The current amount of time spent on sending transactions to approve in milliseconds
     */
    private static long getEllapsedTimeGetTxToApprove() {
        return ellapsedTime_getTxToApprove;
    }

    /**
     * Increases the current amount of time spent on sending transactions to approve
     *
     * @param ellapsedTime the time to add, in milliseconds
     */
    private static void incEllapsedTimeGetTxToApprove(long ellapsedTime) {
        ellapsedTime_getTxToApprove += ellapsedTime;
    }

    /**
      * Tip selection which returns <tt>trunkTransaction</tt> and <tt>branchTransaction</tt>.
      * The input value <tt>depth</tt> determines how many milestones to go back for finding the transactions to approve.
      * The higher your <tt>depth</tt> value, the more work you have to do as you are confirming more transactions.
      * If the <tt>depth</tt> is too large (usually above 15, it depends on the node's configuration) an error will be returned.
      * The <tt>reference</tt> is an optional hash of a transaction you want to approve.
      * If it can't be found at the specified <tt>depth</tt> then an error will be returned.
      *
      * @param depth Number of bundles to go back to determine the transactions for approval.
      * @param reference Hash of transaction to start random-walk from, used to make sure the tips returned reference a given transaction in their past.
      * @return {@link com.iota.iri.service.dto.GetTransactionsToApproveResponse}
      * @throws Exception When tip selection has failed. Currently caught and returned as an {@link ErrorResponse}.
      **/
    private synchronized AbstractResponse getTransactionsToApproveStatement(int depth, Optional<Hash> reference) throws Exception {
        if (depth < 0 || depth > instance.configuration.getMaxDepth()) {
            return ErrorResponse.create("Invalid depth input");
        }

        try {
            List<Hash> tips = getTransactionToApproveTips(depth, reference);
            return GetTransactionsToApproveResponse.create(tips.get(0), tips.get(1));

        } catch (Exception e) {
            log.info("Tip selection failed: " + e.getLocalizedMessage());
            return ErrorResponse.create(e.getLocalizedMessage());
        }
    }

    /**
     * Gets tips which can be used by new transactions to approve.
     * If debug is enabled, statistics on tip selection will be gathered.
     *
     * @param depth The milestone depth for finding the transactions to approve.
     * @param reference An optional transaction hash to be referenced by tips.
     * @return The tips which can be approved.
     * @throws Exception if the subtangle is out of date or if we fail to retrieve transaction tips.
     * @see TipSelector
     */
    List<Hash> getTransactionToApproveTips(int depth, Optional<Hash> reference) throws Exception {
        if (invalidSubtangleStatus()) {
            throw new IllegalStateException(INVALID_SUBTANGLE);
        }

        List<Hash> tips = instance.tipsSelector.getTransactionsToApprove(depth, reference);

        if (log.isDebugEnabled()) {
            gatherStatisticsOnTipSelection();
        }
        return tips;
    }

    /**
     * <p>
     *     Handles statistics on tip selection.
     *     Increases the tip selection by one use.
     * </p>
     * <p>
     *     If the {@link #getCounterGetTxToApprove()} is a power of 100, a log is send and counters are reset.
     * </p>
     */
    private void gatherStatisticsOnTipSelection() {
        API.incCounterGetTxToApprove();
        if ((getCounterGetTxToApprove() % 100) == 0) {
            String sb = "Last 100 getTxToApprove consumed "
                    + API.getEllapsedTimeGetTxToApprove() / 1000000000L
                    + " seconds processing time.";

            log.debug(sb);
            counterGetTxToApprove = 0;
            ellapsedTime_getTxToApprove = 0L;
        }
    }

    /**
      * Returns all tips currently known by this node.
      *
      * @return {@link com.iota.iri.service.dto.GetTipsResponse}
      **/
    private synchronized AbstractResponse getTipsStatement() throws Exception {
        return GetTipsResponse.create(instance.tipsViewModel.getTips()
                .stream()
                .map(Hash::toString)
                .collect(Collectors.toList()));
    }

    /**
      * Stores transactions in the local storage.
      * The trytes to be used for this call should be valid, attached transaction trytes.
      * These trytes are returned by <tt>attachToTangle</tt>, or by doing proof of work somewhere else.
      *
      * @param trytes Transaction data to be stored.
      * @throws Exception When storing or updating a transaction fails
      **/
    public void storeTransactionsStatement(List<String> trytes) throws Exception {
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
            if(transactionViewModel.store(instance.tangle, instance.snapshotProvider.getInitialSnapshot())) {
                transactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                instance.transactionValidator.updateStatus(transactionViewModel);
                transactionViewModel.updateSender("local");
                transactionViewModel.update(instance.tangle, instance.snapshotProvider.getInitialSnapshot(), "sender");
            }
        }
    }

    /**
      * Interrupts and completely aborts the <tt>attachToTangle</tt> process.
      *
      * @return {@link com.iota.iri.service.dto.AbstractResponse.Emptyness}
      **/
    private AbstractResponse interruptAttachingToTangleStatement(){
        pearlDiver.cancel();
        return AbstractResponse.createEmptyResponse();
    }

    /**
      * Returns information about this node.
      *
      * @return {@link com.iota.iri.service.dto.GetNodeInfoResponse}
      **/
    private AbstractResponse getNodeInfoStatement(){
        String name = instance.configuration.isTestnet() ? IRI.TESTNET_NAME : IRI.MAINNET_NAME;
        return GetNodeInfoResponse.create(name, IRI.VERSION,
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().freeMemory(),
                System.getProperty("java.version"),
                Runtime.getRuntime().maxMemory(),
                Runtime.getRuntime().totalMemory(),
                instance.latestMilestoneTracker.getLatestMilestoneHash(),
                instance.latestMilestoneTracker.getLatestMilestoneIndex(),
                instance.snapshotProvider.getLatestSnapshot().getHash(),
                instance.snapshotProvider.getLatestSnapshot().getIndex(),
                instance.snapshotProvider.getInitialSnapshot().getIndex(),
                instance.node.howManyNeighbors(),
                instance.node.queuedTransactionsSize(),
                System.currentTimeMillis(),
                instance.tipsViewModel.size(),
                instance.transactionRequester.numberOfTransactionsToRequest(),
                features,
                instance.configuration.getCoordinator());
    }

    /**
     * <p>
     *     Get the inclusion states of a set of transactions.
     *     This is for determining if a transaction was accepted and confirmed by the network or not.
     *     You can search for multiple tips (and thus, milestones) to get past inclusion states of transactions.
     * </p>
     * <p>
     *     This API call returns a list of boolean values in the same order as the submitted transactions.<br/>
     *     Boolean values will be <tt>true</tt> for confirmed transactions, otherwise <tt>false</tt>.
     * </p>
     * Returns an {@link com.iota.iri.service.dto.ErrorResponse} if a tip is missing or the subtangle is not solid
     *
     * @param transactions List of transactions you want to get the inclusion state for.
     * @param tips List of tips (including milestones) you want to search for the inclusion state.
     * @return {@link com.iota.iri.service.dto.GetInclusionStatesResponse}
     * @throws Exception When a transaction cannot be loaded from hash
     **/
    private AbstractResponse getInclusionStatesStatement(
            final List<String> transactions,
            final List<String> tips) throws Exception {

        final List<Hash> trans = transactions.stream()
                .map(HashFactory.TRANSACTION::create)
                .collect(Collectors.toList());

        final List<Hash> tps = tips.stream().
                map(HashFactory.TRANSACTION::create)
                .collect(Collectors.toList());

        int numberOfNonMetTransactions = trans.size();
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

        // Finds the lowest tips index, or 0
        int minTipsIndex = tipsIndex.stream().reduce((a,b) -> a < b ? a : b).orElse(0);

        // If the lowest tips index (minTipsIndex) is 0 (or lower),
        // we can't check transactions against snapshots because there were no tips,
        // or tips have not been confirmed by a snapshot yet
        if(minTipsIndex > 0) {
            // Finds the highest tips index, or 0
            int maxTipsIndex = tipsIndex.stream().reduce((a,b) -> a > b ? a : b).orElse(0);
            int count = 0;

            // Checks transactions with indexes of tips, and sets inclusionStates byte to 1 or -1 accordingly
            // Sets to -1 if the transaction is only known by hash,
            // or has no index, or index is above the max tip index (not included).

            // Sets to 1 if the transaction index is below the max index of tips (included).
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

        // Sorts all tips per snapshot index. Stops if a tip is not in our database, or just as a hash.
        for (final Hash tip : tps) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, tip);
            if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT){
                return ErrorResponse.create("One of the tips is absent");
            }
            int snapshotIndex = transactionViewModel.snapshotIndex();
            sameIndexTips.putIfAbsent(snapshotIndex, new LinkedList<>());
            sameIndexTips.get(snapshotIndex).add(tip);
        }

        // Loop over all transactions without a state, and counts the amount per snapshot index
        for(int i = 0; i < inclusionStates.length; i++) {
            if(inclusionStates[i] == 0) {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, trans.get(i));
                int snapshotIndex = transactionViewModel.snapshotIndex();
                sameIndexTransactionCount.putIfAbsent(snapshotIndex, 0);
                sameIndexTransactionCount.put(snapshotIndex, sameIndexTransactionCount.get(snapshotIndex) + 1);
            }
        }

        // Loop over all snapshot indexes of transactions that were not confirmed.
        // If we encounter an invalid tangle, stop this function completely.
        for(Integer index : sameIndexTransactionCount.keySet()) {
            // Get the tips from the snapshot indexes we are missing
            Queue<Hash> sameIndexTip = sameIndexTips.get(index);

            // We have tips on the same level as transactions, do a manual search.
            if (sameIndexTip != null && !exhaustiveSearchWithinIndex(
                        sameIndexTip, analyzedTips, trans,
                        inclusionStates, sameIndexTransactionCount.get(index), index)) {

                return ErrorResponse.create(INVALID_SUBTANGLE);
            }
        }
        final boolean[] inclusionStatesBoolean = new boolean[inclusionStates.length];
        for(int i = 0; i < inclusionStates.length; i++) {
            // If a state is 0 by now, we know nothing so assume not included
            inclusionStatesBoolean[i] = inclusionStates[i] == 1;
        }

        {
            return GetInclusionStatesResponse.create(inclusionStatesBoolean);
        }
    }

    /**
     * Traverses down the tips until all transactions we wish to validate have been found or transaction data is missing.
     *
     * @param nonAnalyzedTransactions Tips we will analyze.
     * @param analyzedTips The hashes of tips we have analyzed.
     *                     Hashes specified here won't be analyzed again.
     * @param transactions All transactions we are validating.
     * @param inclusionStates The state of each transaction.
     *                        1 means confirmed, -1 means unconfirmed, 0 is unknown confirmation.
     *                        Should be of equal length as <tt>transactions</tt>.
     * @param count The amount of transactions on the same index level as <tt>nonAnalyzedTransactions</tt>.
     * @param index The snapshot index of the tips in <tt>nonAnalyzedTransactions</tt>.
     * @return <tt>true</tt> if all <tt>transactions</tt> are directly or indirectly references by
     *         <tt>nonAnalyzedTransactions</tt>.
     *         If at some point we are missing transaction data <tt>false</tt> is returned immediately.
     * @throws Exception If a {@link TransactionViewModel} cannot be loaded.
     */
    private boolean exhaustiveSearchWithinIndex(
                Queue<Hash> nonAnalyzedTransactions,
                Set<Hash> analyzedTips,
                List<Hash> transactions,
                byte[] inclusionStates, int count, int index) throws Exception {

        Hash pointer;
        MAIN_LOOP:
        // While we have nonAnalyzedTransactions in the Queue
        while ((pointer = nonAnalyzedTransactions.poll()) != null) {
            // Only analyze tips we haven't analyzed yet
            if (analyzedTips.add(pointer)) {

                // Check if the transactions have indeed this index. Otherwise ignore.
                // Starts off with the tips in nonAnalyzedTransactions, but transaction trunk & branch gets added.
                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, pointer);
                if (transactionViewModel.snapshotIndex() == index) {
                    // Do we have the complete transaction?
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        // Incomplete transaction data, stop search.
                        return false;
                    } else {
                        // check all transactions we wish to verify confirmation for
                        for (int i = 0; i < inclusionStates.length; i++) {
                            if (inclusionStates[i] < 1 && pointer.equals(transactions.get(i))) {
                                // A tip, or its branch/trunk points to this transaction.
                                // That means this transaction is confirmed by this tip.
                                inclusionStates[i] = 1;

                                // Only stop search when we have found all transactions we were looking for
                                if (--count <= 0) {
                                    break MAIN_LOOP;
                                }
                            }
                        }

                        // Add trunk and branch to the queue for the transaction confirmation check
                        nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                    }
                }
            }
        }
        return true;
    }

    /**
      * <p>
      *     Find the transactions which match the specified input and return.
      *     All input values are lists, for which a list of return values (transaction hashes), in the same order, is returned for all individual elements.
      *     The input fields can either be <tt>bundles</tt>, <tt>addresses</tt>, <tt>tags</tt> or <tt>approvees</tt>.
      * </p>
      *
      * Using multiple of these input fields returns the intersection of the values.
      * Returns an {@link com.iota.iri.service.dto.ErrorResponse} if more than maxFindTxs was found.
      *
      * @param request The map with input fields
      *                Must contain at least one of 'bundles', 'addresses', 'tags' or 'approvees'.
      * @return {@link com.iota.iri.service.dto.FindTransactionsResponse}.
      * @throws Exception If a model cannot be loaded, no valid input fields were supplied
      *                   or the total transactions to find exceeds {@link APIConfig#getMaxFindTransactions()}.
      **/
    private synchronized AbstractResponse findTransactionsStatement(final Map<String, Object> request) throws Exception {

        final Set<Hash> foundTransactions =  new HashSet<>();
        boolean containsKey = false;

        final Set<Hash> bundlesTransactions = new HashSet<>();
        if (request.containsKey("bundles")) {
            final Set<String> bundles = getParameterAsSet(request,"bundles",HASH_SIZE);
            for (final String bundle : bundles) {
                bundlesTransactions.addAll(
                        BundleViewModel.load(instance.tangle, HashFactory.BUNDLE.create(bundle))
                        .getHashes());
            }
            foundTransactions.addAll(bundlesTransactions);
            containsKey = true;
        }

        final Set<Hash> addressesTransactions = new HashSet<>();
        if (request.containsKey("addresses")) {
            final Set<String> addresses = getParameterAsSet(request,"addresses",HASH_SIZE);
            for (final String address : addresses) {
                addressesTransactions.addAll(
                        AddressViewModel.load(instance.tangle, HashFactory.ADDRESS.create(address))
                        .getHashes());
            }
            foundTransactions.addAll(addressesTransactions);
            containsKey = true;
        }

        final Set<Hash> tagsTransactions = new HashSet<>();
        if (request.containsKey("tags")) {
            final Set<String> tags = getParameterAsSet(request,"tags",0);
            for (String tag : tags) {
                tag = padTag(tag);
                tagsTransactions.addAll(
                        TagViewModel.load(instance.tangle, HashFactory.TAG.create(tag))
                        .getHashes());
            }
            if (tagsTransactions.isEmpty()) {
                for (String tag : tags) {
                    tag = padTag(tag);
                    tagsTransactions.addAll(
                            TagViewModel.loadObsolete(instance.tangle, HashFactory.OBSOLETETAG.create(tag))
                            .getHashes());
                }
            }
            foundTransactions.addAll(tagsTransactions);
            containsKey = true;
        }

        final Set<Hash> approveeTransactions = new HashSet<>();

        if (request.containsKey("approvees")) {
            final Set<String> approvees = getParameterAsSet(request,"approvees",HASH_SIZE);
            for (final String approvee : approvees) {
                approveeTransactions.addAll(
                        TransactionViewModel.fromHash(instance.tangle, HashFactory.TRANSACTION.create(approvee))
                        .getApprovers(instance.tangle)
                        .getHashes());
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

    /**
     * Adds '9' until the String is of {@link #HASH_SIZE} length.
     *
     * @param tag The String to fill.
     * @return The updated String.
     * @throws ValidationException If the <tt>tag</tt> is a {@link Hash#NULL_HASH}.
     */
    private String padTag(String tag) throws ValidationException {
        while (tag.length() < HASH_SIZE) {
            tag += Converter.TRYTE_ALPHABET.charAt(0);
        }
        if (tag.equals(Hash.NULL_HASH.toString())) {
            throw new ValidationException("Invalid tag input");
        }
        return tag;
    }

    /**
     * Runs {@link #getParameterAsList(Map, String, int)} and transforms it into a {@link Set}.
     *
     * @param request All request parameters.
     * @param paramName The name of the parameter we want to turn into a list of Strings.
     * @param size the length each String must have.
     * @return the list of valid Tryte Strings.
     * @throws ValidationException If the requested parameter does not exist or
     *                             the string is not exactly trytes of <tt>size</tt> length or
     *                             the amount of Strings in the list exceeds {@link APIConfig#getMaxRequestsList}
     */
    private Set<String> getParameterAsSet(
            Map<String, Object> request,
            String paramName, int size) throws ValidationException {

        HashSet<String> result = getParameterAsList(request,paramName,size)
                .stream()
                .collect(Collectors.toCollection(HashSet::new));

        if (result.contains(Hash.NULL_HASH.toString())) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
        return result;
    }

    /**
      * Broadcast a list of transactions to all neighbors.
      * The trytes to be used for this call should be valid, attached transaction trytes.
      * These trytes are returned by <tt>attachToTangle</tt>, or by doing proof of work somewhere else.
      *
      * @param trytes the list of transaction trytes to broadcast
      **/
    public void broadcastTransactionsStatement(List<String> trytes) {
        final List<TransactionViewModel> elements = new LinkedList<>();
        byte[] txTrits = Converter.allocateTritsForTrytes(TRYTES_SIZE);
        for (final String tryte : trytes) {
            //validate all trytes
            Converter.trits(tryte, txTrits, 0);
            final TransactionViewModel transactionViewModel = instance.transactionValidator.validateTrits(
                    txTrits, instance.transactionValidator.getMinWeightMagnitude());

            elements.add(transactionViewModel);
        }
        for (final TransactionViewModel transactionViewModel : elements) {
            //push first in line to broadcast
            transactionViewModel.weightMagnitude = Curl.HASH_LENGTH;
            instance.node.broadcast(transactionViewModel);
        }
    }


    /**
      * <p>
      *     Calculates the confirmed balance, as viewed by the specified <tt>tips</tt>.
      *     If you do not specify the referencing <tt>tips</tt>,
      *     the returned balance is based on the latest confirmed milestone.
      *     In addition to the balances, it also returns the referencing <tt>tips</tt> (or milestone),
      *     as well as the index with which the confirmed balance was determined.
      *     The balances are returned as a list in the same order as the addresses were provided as input.
      * </p>
      * Returns an {@link ErrorResponse} if tips are not found, inconsistent or the threshold is invalid.
      *
      * @param addresses The addresses where we will find the balance for.
      * @param tips The optional tips to find the balance through.
      * @param threshold The confirmation threshold between 0 and 100(inclusive).
      *                  Should be set to 100 for getting balance by counting only confirmed transactions.
      * @return {@link com.iota.iri.service.dto.GetBalancesResponse}
      * @throws Exception When the database has encountered an error
      **/
    private AbstractResponse getBalancesStatement(List<String> addresses,
                                                  List<String> tips,
                                                  int threshold) throws Exception {

        if (threshold <= 0 || threshold > 100) {
            return ErrorResponse.create("Illegal 'threshold'");
        }

        final List<Hash> addressList = addresses.stream()
                .map(address -> (HashFactory.ADDRESS.create(address)))
                .collect(Collectors.toCollection(LinkedList::new));

        final List<Hash> hashes;
        final Map<Hash, Long> balances = new HashMap<>();
        instance.snapshotProvider.getLatestSnapshot().lockRead();
        final int index = instance.snapshotProvider.getLatestSnapshot().getIndex();

        if (tips == null || tips.size() == 0) {
            hashes = Collections.singletonList(instance.snapshotProvider.getLatestSnapshot().getHash());
        } else {
            hashes = tips.stream()
                    .map(tip -> (HashFactory.TRANSACTION.create(tip)))
                    .collect(Collectors.toCollection(LinkedList::new));
        }

        try {
            // Get the balance for each address at the last snapshot
            for (final Hash address : addressList) {
                Long value = instance.snapshotProvider.getLatestSnapshot().getBalance(address);
                if (value == null) {
                    value = 0L;
                }
                balances.put(address, value);
            }

            final Set<Hash> visitedHashes = new HashSet<>();
            final Map<Hash, Long> diff = new HashMap<>();

            // Calculate the difference created by the non-verified transactions which tips approve.
            // This difference is put in a map with address -> value changed
            for (Hash tip : hashes) {
                if (!TransactionViewModel.exists(instance.tangle, tip)) {
                    return ErrorResponse.create("Tip not found: " + tip.toString());
                }
                if (!instance.ledgerService.isBalanceDiffConsistent(visitedHashes, diff, tip)) {
                    return ErrorResponse.create("Tips are not consistent");
                }
            }

            // Update the found balance according to 'diffs' balance changes
            diff.forEach((key, value) -> balances.computeIfPresent(key, (hash, aLong) -> value + aLong));
        } finally {
            instance.snapshotProvider.getLatestSnapshot().unlockRead();
        }

        final List<String> elements = addressList.stream()
                .map(address -> balances.get(address).toString())
                .collect(Collectors.toCollection(LinkedList::new));

        return GetBalancesResponse.create(elements, hashes.stream()
                .map(h -> h.toString())
                .collect(Collectors.toList()), index);
    }

    private static int counter_PoW = 0;

    /**
     * Can be 0 or more, and is set to 0 every 100 requests.
     * Each increase indicates another 2 tips sent.
     *
     * @return The current amount of times this node has done proof of work.
     *         Doesn't distinguish between remote and local proof of work.
     */
    public static int getCounterPoW() {
        return counter_PoW;
    }

    /**
     * Increases the amount of times this node has done proof of work by one.
     */
    public static void incCounterPoW() {
        API.counter_PoW++;
    }

    private static long ellapsedTime_PoW = 0L;

    /**
     * Can be 0 or more, and is set to 0 every 100 requests.
     *
     * @return The current amount of time spent on doing proof of work in milliseconds.
     *         Doesn't distinguish between remote and local proof of work.
     */
    public static long getEllapsedTimePoW() {
        return ellapsedTime_PoW;
    }

    /**
     * Increases the current amount of time spent on doing proof of work.
     *
     * @param ellapsedTime the time to add, in milliseconds.
     */
    public static void incEllapsedTimePoW(long ellapsedTime) {
        ellapsedTime_PoW += ellapsedTime;
    }

    /**
      * <p>
      *     Prepares the specified transactions (trytes) for attachment to the Tangle by doing Proof of Work.
      *     You need to supply <tt>branchTransaction</tt> as well as <tt>trunkTransaction</tt>.
      *     These are the tips which you're going to validate and reference with this transaction.
      *     These are obtainable by the <tt>getTransactionsToApprove</tt> API call.
      * </p>
      * <p>
      *     The returned value is a different set of tryte values which you can input into
      *     <tt>broadcastTransactions</tt> and <tt>storeTransactions</tt>.
      *     The last 243 trytes of the return value consist of the following:
      *     <ul>
      *         <li><code>trunkTransaction</code></li>
      *         <li><code>branchTransaction</code></li>
      *         <li><code>nonce</code></li>
      *     </ul>
      *     These are valid trytes which are then accepted by the network.
      * </p>
      * @param trunkTransaction A reference to an external transaction (tip) used as trunk.
      *                         The transaction with index 0 will have this tip in its trunk.
      *                         All other transactions reference the previous transaction in the bundle (Their index-1).
      *
      * @param branchTransaction A reference to an external transaction (tip) used as branch.
      *                          Each Transaction in the bundle will have this tip as their branch, except the last.
      *                          The last one will have the branch in its trunk.
      * @param minWeightMagnitude The amount of work we should do to confirm this transaction.
      *                           Each 0-trit on the end of the transaction represents 1 magnitude.
      *                           A 9-tryte represents 3 magnitudes, since a 9 is represented by 3 0-trits.
      *                           Transactions with a different minWeightMagnitude are compatible.
      * @param trytes the list of trytes to prepare for network attachment, by doing proof of work.
      * @return The list of transactions in trytes, ready to be broadcast to the network.
      **/
    public synchronized List<String> attachToTangleStatement(Hash trunkTransaction, Hash branchTransaction,
                                                             int minWeightMagnitude, List<String> trytes) {

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
                if(IntStream.range(TransactionViewModel.TAG_TRINARY_OFFSET,
                                   TransactionViewModel.TAG_TRINARY_OFFSET + TransactionViewModel.TAG_TRINARY_SIZE)
                        .allMatch(idx -> transactionTrits[idx]  == ((byte) 0))) {

                    System.arraycopy(transactionTrits, TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET,
                    transactionTrits, TransactionViewModel.TAG_TRINARY_OFFSET,
                    TransactionViewModel.TAG_TRINARY_SIZE);
                }

                Converter.copyTrits(timestamp, transactionTrits,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_TRINARY_SIZE);
                Converter.copyTrits(0, transactionTrits,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE);
                Converter.copyTrits(MAX_TIMESTAMP_VALUE, transactionTrits,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET,
                        TransactionViewModel.ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE);

                if (!pearlDiver.search(transactionTrits, minWeightMagnitude, instance.configuration.getPowThreads())) {
                    transactionViewModels.clear();
                    break;
                }
                //validate PoW - throws exception if invalid
                final TransactionViewModel transactionViewModel = instance.transactionValidator.validateTrits(
                        transactionTrits, instance.transactionValidator.getMinWeightMagnitude());

                transactionViewModels.add(transactionViewModel);
                prevTransaction = transactionViewModel.getHash();
            } finally {
                API.incEllapsedTimePoW(System.nanoTime() - startTime);
                API.incCounterPoW();
                if ( ( API.getCounterPoW() % 100) == 0 ) {
                    String sb = "Last 100 PoW consumed "
                                + API.getEllapsedTimePoW() / 1000000000L
                                + " seconds processing time.";
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
     * Transforms an object parameter into an int.
     *
     * @param request A map of all request parameters
     * @param paramName The parameter we want to get as an int.
     * @return The integer value of this parameter
     * @throws ValidationException If the requested parameter does not exist or cannot be transformed into an int.
     */
    private int getParameterAsInt(Map<String, Object> request, String paramName) throws ValidationException {
        validateParamExists(request, paramName);
        int result;
        try {
            result = ((Double) request.get(paramName)).intValue();
        } catch (ClassCastException e) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
        return result;
    }

    /**
     * Transforms an object parameter into a String.
     *
     * @param request A map of all request parameters
     * @param paramName The parameter we want to get as a String.
     * @param size The expected length of this String
     * @return The String value of this parameter
     * @throws ValidationException If the requested parameter does not exist or
     *                             the string is not exactly trytes of <tt>size</tt> length
     */
    private String getParameterAsStringAndValidate(Map<String, Object> request, String paramName, int size) throws ValidationException {
        validateParamExists(request, paramName);
        String result = (String) request.get(paramName);
        validateTrytes(paramName, size, result);
        return result;
    }

    /**
     * Checks if a string is non 0 length, and contains exactly <tt>size</tt> amount of trytes.
     * Trytes are Strings containing only A-Z and the number 9.
     *
     * @param paramName The name of the parameter this String came from.
     * @param size The amount of trytes it should contain.
     * @param result The String we validate.
     * @throws ValidationException If the string is not exactly trytes of <tt>size</tt> length
     */
    private void validateTrytes(String paramName, int size, String result) throws ValidationException {
        if (!validTrytes(result,size,ZERO_LENGTH_NOT_ALLOWED)) {
            throw new ValidationException("Invalid " + paramName + " input");
        }
    }

    /**
     * Checks if a parameter exists in the map
     * @param request All request parameters
     * @param paramName The name of the parameter we are looking for
     * @throws ValidationException if <tt>request</tt> does not contain <tt>paramName</tt>
     */
    private void validateParamExists(Map<String, Object> request, String paramName) throws ValidationException {
        if (!request.containsKey(paramName)) {
            throw new ValidationException(invalidParams);
        }
    }

    /**
     * Translates the parameter into a {@link List}.
     * We then validate if the amount of elements does not exceed the maximum allowed.
     * Afterwards we verify if each element is valid according to {@link #validateTrytes(String, int, String)}.
     *
     * @param request All request parameters
     * @param paramName The name of the parameter we want to turn into a list of Strings
     * @param size the length each String must have
     * @return the list of valid Tryte Strings.
     * @throws ValidationException If the requested parameter does not exist or
     *                             the string is not exactly trytes of <tt>size</tt> length or
     *                             the amount of Strings in the list exceeds {@link APIConfig#getMaxRequestsList}
     */
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

    /**
     * Checks if a string is of a certain length, and contains exactly <tt>size</tt> amount of trytes.
     * Trytes are Strings containing only A-Z and the number 9.
     *
     * @param trytes The String we validate.
     * @param length The amount of trytes it should contain.
     * @param zeroAllowed If set to '{@value #ZERO_LENGTH_ALLOWED}', an empty string is also valid.
     * @throws ValidationException If the string is not exactly trytes of <tt>size</tt> length
     * @return <tt>true</tt> if the string is valid, otherwise <tt>false</tt>
     */
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

    /**
     * Updates the {@link HttpServerExchange} {@link HeaderMap} with the proper response settings.
     * @param exchange Contains information about what the client has send to us
     */
    private static void setupResponseHeaders(HttpServerExchange exchange) {
        final HeaderMap headerMap = exchange.getResponseHeaders();
        headerMap.add(new HttpString("Access-Control-Allow-Origin"),"*");
        headerMap.add(new HttpString("Keep-Alive"), "timeout=500, max=100");
    }

    /**
     * Sets up the {@link HttpHandler} to have correct security settings.
     * Remote authentication is blocked for anyone except
     * those defined in {@link APIConfig#getRemoteAuth()} or localhost.
     * This is done with {@link BasicAuthenticationMechanism} in a {@link AuthenticationMode#PRO_ACTIVE} mode.
     *
     * @param toWrap the path handler used in creating the server.
     * @return The updated handler
     */
    private HttpHandler addSecurity(HttpHandler toWrap) {
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
        final List<AuthenticationMechanism> mechanisms =
                Collections.singletonList(new BasicAuthenticationMechanism("Iota Realm"));

        handler = new AuthenticationMechanismsHandler(handler, mechanisms);
        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
        return handler;
    }

    /**
     * If a server is running, stops the server from accepting new incoming requests.
     * Does not remove the instance, so the server may be restarted without having to recreate it.
     */
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
    private synchronized AbstractResponse storeMessageStatement(String address, String message) throws Exception {
        final List<Hash> txToApprove = getTransactionToApproveTips(3, Optional.empty());

        final int txMessageSize = TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE / 3;

        final int txCount = (message.length() + txMessageSize - 1) / txMessageSize;

        final byte[] timestampTrits = new byte[TransactionViewModel.TIMESTAMP_TRINARY_SIZE];
        Converter.copyTrits(System.currentTimeMillis(), timestampTrits, 0, timestampTrits.length);
        final String timestampTrytes = StringUtils.rightPad(
                Converter.trytes(timestampTrits),
                timestampTrits.length / 3, '9');

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

        transactions = transactions.stream()
                .map(tx -> StringUtils.rightPad(tx + bundleHash, TRYTES_SIZE, '9'))
                .collect(Collectors.toList());

        // do pow
        List<String> powResult = attachToTangleStatement(txToApprove.get(0), txToApprove.get(1), 9, transactions);
        broadcastTransactionsStatement(powResult);
        return AbstractResponse.createEmptyResponse();
    }
}
