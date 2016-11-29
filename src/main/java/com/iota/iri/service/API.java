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
import java.util.stream.Collector;
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
import com.iota.iri.service.storage.StorageBundle;
import com.iota.iri.service.storage.StorageTags;
import com.iota.iri.service.storage.StorageTransactions;
import com.iota.iri.utils.Converter;

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

@SuppressWarnings("unchecked")
public class API {

	private static final Logger log = LoggerFactory.getLogger(API.class);

	private static Undertow server;
	private static final int PORT = 14265;

	private static final PearlDiver pearlDiver = new PearlDiver();

	public static void launch() throws IOException {

		server = Undertow.builder().addHttpListener(PORT, "localhost")
		        .setHandler(path().addPrefixPath("/", exchange -> {

                    final ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

                    final long beginningTime = System.currentTimeMillis();
                    final String body = IOUtils.toString(cis, StandardCharsets.UTF_8);
                    final AbstractResponse response = process(body);
                    sendResponse(exchange, response, beginningTime);
                })).build();
		
		server.start();
	}

	private static final Gson gson = new GsonBuilder().create();

	private static AbstractResponse process(final String requestString) throws UnsupportedEncodingException {

		try {

			final Map<String, Object> request = gson.fromJson(requestString, Map.class);

			final String command = (String) request.get("command");
			if (command == null) {
				return ErrorResponse.create("COMMAND parameter has not been specified in the request.");
			}

			switch (command) {

			case "addNeighbors": {
				final List<String> uris = (List<String>) request.get("uris");
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
				        Storage.numberOfTransactionsToRequest);
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
				getTrytesStatement(hashes);
			}

			case "interruptAttachingToTangle": {
				pearlDiver.interrupt();
				return AbstractResponse.createEmptyResponse();
			}
			case "removeNeighbors": {
				List<String> uris = (List<String>) request.get("uris");
				return removeNeighborsStatement(uris);
			}

			case "storeTransactions": {
				List<String> trytes = (List<String>) request.get("trytes");
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

	private static AbstractResponse removeNeighborsStatement(List<String> uris) throws URISyntaxException {
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

	private static void getTrytesStatement(List<String> hashes) {
		final List<String> elements = new LinkedList<>();
		for (final String hash : hashes) {
			final Transaction transaction = StorageTransactions.instance().loadTransaction((new Hash(hash)).bytes());
			elements.add(transaction == null ? "null" : (Converter.trytes(transaction.trits())));
		}
		GetTrytesResponse.create(elements);
	}

	private static AbstractResponse getTransactionToApproveStatement(final int depth) {
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

	private static AbstractResponse getTipsStatement() {
		return GetTipsResponse.create(
				StorageTransactions.instance().tips()
					.stream()
					.map(Hash::toString)
					.collect(Collectors.toList()));
	}

	private static AbstractResponse storeTransactionStatement(List<String> trys) {
		for (final String trytes : trys) {
			final Transaction transaction = new Transaction(Converter.trits(trytes));
			StorageTransactions.instance().storeTransaction(transaction.hash, transaction, false);
		}

		return AbstractResponse.createEmptyResponse();
	}

	private static AbstractResponse getNeighborsStatement() {
		return GetNeighborsResponse.create(Node.instance().getNeighbors());
	}

	private static AbstractResponse getInclusionStateStatement(final List<String> trans, final List<String> tps) {
		
		final List<Hash> transactions = trans.stream().map(Hash::new).collect(Collectors.toList());
		final List<Hash> tips = tps.stream().map(Hash::new).collect(Collectors.toList());

		int numberOfNonMetTransactions = transactions.size();
		final boolean[] inclusionStates = new boolean[numberOfNonMetTransactions];

		synchronized (Storage.analyzedTransactionsFlags) {

			Storage.instance().clearAnalyzedTransactionsFlags();

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

					if (Storage.instance().setAnalyzedTransactionFlag(pointer)) {

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

	private static AbstractResponse findTransactionStatement(final Map<String, Object> request) {
		final Set<Long> bundlesTransactions = new HashSet<>();
		if (request.containsKey("bundles")) {
			for (final String bundle : (List<String>) request.get("bundles")) {
				bundlesTransactions
				        .addAll(StorageBundle.instance().bundleTransactions(StorageBundle.instance().bundlePointer((new Hash(bundle)).bytes())));
			}
		}

		final Set<Long> addressesTransactions = new HashSet<>();
		if (request.containsKey("addresses")) {
			for (final String address : (List<String>) request.get("addresses")) {
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
				        .addAll(Storage.instance().approveeTransactions(Storage.instance().approveePointer((new Hash(approvee)).bytes())));
			}
		}

		// need refactoring
		final Set<Long> foundTransactions = bundlesTransactions.isEmpty() ? (addressesTransactions.isEmpty()
		        ? (tagsTransactions.isEmpty()
		                ? (approveeTransactions.isEmpty() ? new HashSet<>() : approveeTransactions) : tagsTransactions)
		        : addressesTransactions) : bundlesTransactions;

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
			elements.add(new Hash(StorageTransactions.instance().loadTransaction(pointer).hash, 0, Transaction.HASH_SIZE).toString());
		}

		return FindTransactionsResponse.create(elements);
	}

	private static AbstractResponse broadcastTransactionStatement(final List<String> trytes2) {
		for (final String tryte : trytes2) {

			final Transaction transaction = new Transaction(Converter.trits(tryte));
			transaction.weightMagnitude = Curl.HASH_LENGTH;
			Node.instance().broadcast(transaction);
		}
		return AbstractResponse.createEmptyResponse();
	}

	private static AbstractResponse getBalancesStatement(final List<String> addrss, final int threshold) {

		if (threshold <= 0 || threshold > 100) {
			return ErrorResponse.create("Illegal 'threshold'");
		}

		final List<Hash> addresses = new LinkedList<>();
		for (final String address : addrss) {
			addresses.add((new Hash(address)));
		}

		final Map<Hash, Long> balances = new HashMap<>();
		for (final Hash address : addresses) {
			balances.put(address,
			        Snapshot.initialState.containsKey(address) ? Snapshot.initialState.get(address) : Long.valueOf(0));
		}

		final Hash milestone = Milestone.latestSolidSubtangleMilestone;
		final int milestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;

		synchronized (Storage.analyzedTransactionsFlags) {

			Storage.instance().clearAnalyzedTransactionsFlags();

			final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(
			        Collections.singleton(StorageTransactions.instance().transactionPointer(milestone.bytes())));
			Long pointer;
			while ((pointer = nonAnalyzedTransactions.poll()) != null) {

				if (Storage.instance().setAnalyzedTransactionFlag(pointer)) {

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

		final List<String> elements = new LinkedList<>();
		for (final Hash address : addresses) {
			elements.add(balances.get(address).toString());
		}

		return GetBalancesResponse.create(elements, milestone, milestoneIndex);
	}

	private static AbstractResponse attachToTangleStatement(final Hash trunkTransaction, final Hash branchTransaction,
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

	private static AbstractResponse addNeighborsStatement(final List<String> uris) throws URISyntaxException {

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
	
	private static void sendResponse(final HttpServerExchange exchange, final AbstractResponse res, final long beginningTime) throws IOException {
		res.setDuration((int) (System.currentTimeMillis() - beginningTime));
		final String response = gson.toJson(res);
		
		if (res instanceof ErrorResponse || res instanceof ExceptionResponse) {
			exchange.setResponseCode(400); // bad request
		}
		exchange.getResponseChannel().write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
		
		exchange.endExchange();
	}

	public static void shutDown() {
		if (server != null) {
			server.stop();
		}
    }
}
