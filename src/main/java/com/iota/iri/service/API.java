package com.iota.iri.service;

import static io.undertow.Handlers.path;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.streams.ChannelInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.IRI;
import com.iota.iri.Milestone;
import com.iota.iri.Neighbor;
import com.iota.iri.Snapshot;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.PearlDiver;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.dto.AbstractResponse;
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
import com.iota.iri.service.storage.Storage;
import com.iota.iri.service.storage.StorageAddresses;
import com.iota.iri.service.storage.StorageApprovers;
import com.iota.iri.service.storage.StorageBundle;
import com.iota.iri.service.storage.StorageScratchpad;
import com.iota.iri.service.storage.StorageTags;
import com.iota.iri.service.storage.StorageTransactions;
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

	public void init() throws IOException {
		
		int apiPort = Configuration.integer(DefaultConfSettings.API_PORT);
		
		server = Undertow.builder().addHttpListener(apiPort, "localhost")
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
        final AbstractResponse response = process(body);
        sendResponse(exchange, response, beginningTime);
	}

	private AbstractResponse process(final String requestString) throws UnsupportedEncodingException {

		try {

			final Map<String, Object> request = gson.fromJson(requestString, Map.class);
			if (request == null) {
				return ExceptionResponse.create("Invalid request payload: '" + requestString + "'");
			}
			
			final String command = (String) request.get("command");
			if (command == null) {
				return ErrorResponse.create("COMMAND parameter has not been specified in the request.");
			}
			
			log.info("-> Requesting command {}", command);
			
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
				return getInclusionStateStatement(trans, tps);
			}
			case "getNeighbors": {
				return getNeighborsStatement();
			}
			case "getNodeInfo": {
				return GetNodeInfoResponse.create(IRI.NAME, IRI.VERSION, 
						Runtime.getRuntime().availableProcessors(),
				        Runtime.getRuntime().freeMemory(), 
				        Runtime.getRuntime().maxMemory(),
				        Runtime.getRuntime().totalMemory(), 
				        Milestone.latestMilestone, 
				        Milestone.latestMilestoneIndex,
				        Milestone.latestSolidSubtangleMilestone, 
				        Milestone.latestSolidSubtangleMilestoneIndex,
				        Node.instance().howManyNeighbors(), 
				        Node.instance().queuedTransactionsSize(), 
				        System.currentTimeMillis(),
				        StorageTransactions.instance().tips().size(), 
				        StorageScratchpad.instance().getNumberOfTransactionsToRequest());
			}
			case "getTips": {
				return getTipsStatement();
			}
			case "getTransactionsToApprove": {
				final int depth = ((Double) request.get("depth")).intValue();
				return getTransactionToApproveStatement(depth);
			}
			case "getTrytes": {
				final List<String> hashes = (List<String>) request.get("hashes");
				log.debug("Executing getTrytesStatement: {}", hashes);
				return getTrytesStatement(hashes);
			}

			case "interruptAttachingToTangle": {
				pearlDiver.interrupt();
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
			default:
				return ErrorResponse.create("Command [" + command + "] is unknown");
			}

		} catch (final Exception e) {
			log.error("API Exception: ", e);
			return ExceptionResponse.create(e.getLocalizedMessage());
		}
	}

	private AbstractResponse removeNeighborsStatement(List<String> uris) throws URISyntaxException {
		final AtomicInteger numberOfRemovedNeighbors = new AtomicInteger(0);
		uris.stream()
			.map(API::uri)
			.map(Optional::get)
			.filter(u -> "udp".equals(u.getScheme()))
			.forEach(u -> {
				if (Node.instance().removeNeighbor(u)) {
					numberOfRemovedNeighbors.incrementAndGet();
				}
			});
		return RemoveNeighborsResponse.create(numberOfRemovedNeighbors.get());
	}
	
	public static Optional<URI> uri(final String uri) {
		try {
			return Optional.of(new URI(uri));
		} catch (URISyntaxException e) {
			log.error("Uri {} raised URI Syntax Exception", uri);
		}
		return Optional.empty();
	}

	private AbstractResponse getTrytesStatement(List<String> hashes) {
		final List<String> elements = new LinkedList<>();
		for (final String hash : hashes) {
			final Transaction transaction = StorageTransactions.instance().loadTransaction((new Hash(hash)).bytes());
			if (transaction != null) {
				elements.add(Converter.trytes(transaction.trits()));
			}
		}
		return GetTrytesResponse.create(elements);
	}

	private AbstractResponse getTransactionToApproveStatement(final int depth) {
		final Hash trunkTransactionToApprove = TipsManager.transactionToApprove(null, depth);
		if (trunkTransactionToApprove == null) {
			return ErrorResponse.create("The subtangle is not solid");
		}
		final Hash branchTransactionToApprove = TipsManager.transactionToApprove(trunkTransactionToApprove, depth);
		if (branchTransactionToApprove == null) {
			return ErrorResponse.create("The subtangle is not solid");
		}
		return GetTransactionsToApproveResponse.create(trunkTransactionToApprove, branchTransactionToApprove);
	}

	private AbstractResponse getTipsStatement() {
		return GetTipsResponse.create(
				StorageTransactions.instance().tips()
					.stream()
					.map(Hash::toString)
					.collect(Collectors.toList()));
	}

	private AbstractResponse storeTransactionStatement(final List<String> trys) {
		for (final String trytes : trys) {
			final Transaction transaction = new Transaction(Converter.trits(trytes));
			StorageTransactions.instance().storeTransaction(transaction.hash, transaction, false);
		}
		return AbstractResponse.createEmptyResponse();
	}

	private AbstractResponse getNeighborsStatement() {
		return GetNeighborsResponse.create(Node.instance().getNeighbors());
	}

	private AbstractResponse getInclusionStateStatement(final List<String> trans, final List<String> tps) {
		
		final List<Hash> transactions = trans.stream().map(s -> new Hash(s)).collect(Collectors.toList());
		final List<Hash> tips = tps.stream().map(s -> new Hash(s)).collect(Collectors.toList());

		int numberOfNonMetTransactions = transactions.size();
		final boolean[] inclusionStates = new boolean[numberOfNonMetTransactions];

		synchronized (StorageScratchpad.instance().getAnalyzedTransactionsFlags()) {

			StorageScratchpad.instance().clearAnalyzedTransactionsFlags();

			final Queue<Long> nonAnalyzedTransactions = new LinkedList<>();
			for (final Hash tip : tips) {

				final long pointer = StorageTransactions.instance().transactionPointer(tip.bytes());
				if (pointer <= 0) {
					return ErrorResponse.create("One of the tips absents");
				}
				nonAnalyzedTransactions.offer(pointer);
			}

			{
				Long pointer;
				MAIN_LOOP: while ((pointer = nonAnalyzedTransactions.poll()) != null) {

					if (StorageScratchpad.instance().setAnalyzedTransactionFlag(pointer)) {

						final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
						if (transaction.type == Storage.PREFILLED_SLOT) {
							return ErrorResponse.create("The subtangle is not solid");
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
				return GetInclusionStatesResponse.create(inclusionStates);
			}
		}
	}

	private AbstractResponse findTransactionStatement(final Map<String, Object> request) {
		final Set<Long> bundlesTransactions = new HashSet<>();
		if (request.containsKey("bundles")) {
			for (final String bundle : (List<String>) request.get("bundles")) {
				bundlesTransactions
				        .addAll(StorageBundle.instance().bundleTransactions(StorageBundle.instance().bundlePointer((new Hash(bundle)).bytes())));
			}
		}

		final Set<Long> addressesTransactions = new HashSet<>();
		if (request.containsKey("addresses")) {
			final List<String> addresses = (List<String>) request.get("addresses");
			log.debug("Searching: {}", addresses.stream().reduce((a,b) -> a+= ',' + b));
			
			for (final String address : addresses) {
				if (address.length() != 81) {
					log.error("Address {} doesn't look a valid address", address);
				}
				addressesTransactions
				        .addAll(StorageAddresses.instance().addressTransactions(StorageAddresses.instance().addressPointer((new Hash(address)).bytes())));
			}
		}

		final Set<Long> tagsTransactions = new HashSet<>();
		if (request.containsKey("tags")) {
			for (String tag : (List<String>) request.get("tags")) {
				while (tag.length() < Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE) {
					tag += Converter.TRYTE_ALPHABET.charAt(0);
				}
				tagsTransactions.addAll(StorageTags.instance().tagTransactions(StorageTags.instance().tagPointer((new Hash(tag)).bytes())));
			}
		}

		final Set<Long> approveeTransactions = new HashSet<>();

		if (request.containsKey("approvees")) {
			for (final String approvee : (List<String>) request.get("approvees")) {
				approveeTransactions
				        .addAll(StorageApprovers.instance().approveeTransactions(StorageApprovers.instance().approveePointer((new Hash(approvee)).bytes())));
			}
		}

		// need refactoring
		final Set<Long> foundTransactions = bundlesTransactions.isEmpty() ? (addressesTransactions.isEmpty()
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

		final List<String> elements = foundTransactions.stream().map(pointer -> new Hash(StorageTransactions.instance().loadTransaction(pointer).hash, 0, Transaction.HASH_SIZE).toString()).collect(Collectors.toCollection(LinkedList::new));

		return FindTransactionsResponse.create(elements);
	}

	private AbstractResponse broadcastTransactionStatement(final List<String> trytes2) {
		for (final String tryte : trytes2) {
			final Transaction transaction = new Transaction(Converter.trits(tryte));
			transaction.weightMagnitude = Curl.HASH_LENGTH;
			Node.instance().broadcast(transaction);
		}
		return AbstractResponse.createEmptyResponse();
	}

	private AbstractResponse getBalancesStatement(final List<String> addrss, final int threshold) {

		if (threshold <= 0 || threshold > 100) {
			return ErrorResponse.create("Illegal 'threshold'");
		}

		final List<Hash> addresses = addrss.stream().map(address -> (new Hash(address))).collect(Collectors.toCollection(LinkedList::new));

		final Map<Hash, Long> balances = new HashMap<>();
		for (final Hash address : addresses) {
			balances.put(address,
			        Snapshot.initialState.containsKey(address) ? Snapshot.initialState.get(address) : Long.valueOf(0));
		}

		final Hash milestone = Milestone.latestSolidSubtangleMilestone;
		final int milestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;

		synchronized (StorageScratchpad.instance().getAnalyzedTransactionsFlags()) {

			StorageScratchpad.instance().clearAnalyzedTransactionsFlags();

			final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(
			        Collections.singleton(StorageTransactions.instance().transactionPointer(milestone.bytes())));
			Long pointer;
			while ((pointer = nonAnalyzedTransactions.poll()) != null) {

				if (StorageScratchpad.instance().setAnalyzedTransactionFlag(pointer)) {

					final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);

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

		final List<String> elements = addresses.stream().map(address -> balances.get(address).toString()).collect(Collectors.toCollection(LinkedList::new));

		return GetBalancesResponse.create(elements, milestone, milestoneIndex);
	}

	private AbstractResponse attachToTangleStatement(final Hash trunkTransaction, final Hash branchTransaction,
	        final int minWeightMagnitude, final List<String> trytes) {
		final List<Transaction> transactions = new LinkedList<>();

		Hash prevTransaction = null;

		for (final String tryte : trytes) {

			final int[] transactionTrits = Converter.trits(tryte);
			System.arraycopy((prevTransaction == null ? trunkTransaction : prevTransaction).trits(), 0,
					transactionTrits, Transaction.TRUNK_TRANSACTION_TRINARY_OFFSET,
					Transaction.TRUNK_TRANSACTION_TRINARY_SIZE);
			System.arraycopy((prevTransaction == null ? branchTransaction : trunkTransaction).trits(), 0,
					transactionTrits, Transaction.BRANCH_TRANSACTION_TRINARY_OFFSET,
					Transaction.BRANCH_TRANSACTION_TRINARY_SIZE);

			if (pearlDiver.search(transactionTrits, minWeightMagnitude, 0)) {
				transactions.clear();
				break;
			}
			final Transaction transaction = new Transaction(transactionTrits);
			transactions.add(transaction);
			prevTransaction = new Hash(transaction.hash, 0, Transaction.HASH_SIZE);
		}

		final List<String> elements = new LinkedList<>();
		for (int i = transactions.size(); i-- > 0;) {
			elements.add(Converter.trytes(transactions.get(i).trits()));
		}
		return AttachToTangleResponse.create(elements);
	}

	private AbstractResponse addNeighborsStatement(final List<String> uris) throws URISyntaxException {

		int numberOfAddedNeighbors = 0;
		for (final String uriString : uris) {
			final URI uri = new URI(uriString);
			if ("udp".equals(uri.getScheme())) {
				final Neighbor neighbor = new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort()));
				if (!Node.instance().getNeighbors().contains(neighbor)) {
					Node.instance().getNeighbors().add(neighbor);
					numberOfAddedNeighbors++;
				}
			}
		}
		return AddedNeighborsResponse.create(numberOfAddedNeighbors);
	}
	
	private void sendResponse(final HttpServerExchange exchange, final AbstractResponse res, final long beginningTime) throws IOException {
		res.setDuration((int) (System.currentTimeMillis() - beginningTime));
		final String response = gson.toJson(res);
		
		if (res instanceof ErrorResponse) {
			exchange.setStatusCode(400); // bad request
		} else if (res instanceof ExceptionResponse) {
			exchange.setStatusCode(500); // internall error	
		}
		
		setupResponseHeaders(exchange);
		
		final int writtenBytes = exchange.getResponseChannel().write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
		exchange.endExchange();
		log.debug("Sent {} bytes back in the response.", writtenBytes);
	}

	private static void setupResponseHeaders(final HttpServerExchange exchange) {
		final HeaderMap headerMap = exchange.getResponseHeaders();
		headerMap.add(new HttpString("Access-Control-Allow-Origin"), Configuration.string(DefaultConfSettings.CORS_ENABLED));
		headerMap.add(new HttpString("Keep-Alive"), "timeout=500, max=100");
	}

	public void shutDown() {
		if (server != null) {
			server.stop();
		}
    }
	
	private static API instance = new API();
	
	public static API instance() {
		return instance;
	}
}
