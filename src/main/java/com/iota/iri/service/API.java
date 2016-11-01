package com.iota.iri.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
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

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.iota.iri.service.dto.FindTransactionesponse;
import com.iota.iri.service.dto.GetBalancesResponse;
import com.iota.iri.service.dto.GetInclusionStatesResponse;
import com.iota.iri.service.dto.GetNeighborsResponse;
import com.iota.iri.service.dto.GetNodeInfoResponse;
import com.iota.iri.service.dto.GetTipsResponse;
import com.iota.iri.service.dto.GetTransactionsToApproveResponse;
import com.iota.iri.service.dto.GetTrytesResponse;
import com.iota.iri.service.dto.RemoveNeighborsResponse;
import com.iota.iri.utils.Converter;

@SuppressWarnings("unchecked")
public class API {

	private static final Logger log = LoggerFactory.getLogger(API.class);

    public static final int PORT = 14265;

    private static AsynchronousServerSocketChannel serverChannel;
    
    private static final PearlDiver pearlDiver = new PearlDiver();

    public static void launch() throws IOException {
        serverChannel = AsynchronousServerSocketChannel.open(AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool()));
        serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT));
        serverChannel.accept(null, handler);
    }

    private static class Request {

        static final int READING_BUFFER_SIZE = 4096;

        final AsynchronousSocketChannel channel;
        final ByteBuffer buffer = ByteBuffer.allocateDirect(READING_BUFFER_SIZE);
        final ByteArrayOutputStream accumulatedData = new ByteArrayOutputStream();

        public Request(final AsynchronousSocketChannel channel) {

            this.channel = channel;
            
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

                                            final long beginningTime = System.currentTimeMillis();
                                            AbstractResponse response = process(body);
                                            sendResponse(response, beginningTime);
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
                        log.error("Error reading channel", e);
                    } finally {
                    	IOUtils.closeQuietly(channel);
                    }
                }

                @Override
                public void failed(final Throwable e, final Request request) {
                    log.error("Failed communication", e);
                    IOUtils.closeQuietly(channel);
                }
            });
        }

        private final Gson gson = new GsonBuilder().create();
        
		public AbstractResponse process(final String requestString) throws UnsupportedEncodingException {

            try {
            	
            	final Map<String, Object> request = gson.fromJson(requestString, Map.class);
                
            	final String command = (String)request.get("command");
                if (command == null) {
                	return ErrorResponse.create("'command' parameter has not been specified");
                } 
                
				switch (command) {

                    case "addNeighbors": { 
                    	final List<String> uris = (List<String>)request.get("uris");
                        return addNeighborsStatement(uris);
                	}
                    case "attachToTangle": {
                        final Hash trunkTransaction = new Hash((String)request.get("trunkTransaction"));
                        final Hash branchTransaction = new Hash((String)request.get("branchTransaction"));
                        final int minWeightMagnitude = (Integer)request.get("minWeightMagnitude");
                        final List<String> trytes = (List<String>)request.get("trytes");
                        
                        return attachToTangleStatement(trunkTransaction, branchTransaction, minWeightMagnitude, trytes);
                    }
                    case "broadcastTransactions": {
                    	final List<String> trytes = (List<String>)request.get("trytes");
                    	return broadcastTransactionStatement(trytes);
                    }
                    case "findTransactions": {
                        return findTransactionStatement(request);
                    }

                    case "getBalances": {
                    	final List<String> addresses = (List<String>)request.get("addresses");
                    	final Integer threshold = (Integer)request.get("threshold");
                        return getBalancesStatement(addresses, threshold);
                    }

                    case "getInclusionStates": {
                    	final List<String> trans = (List<String>)request.get("transactions");
                    	final List<String> tps = (List<String>)request.get("tips");
                        return getInclusionStateStatement(trans, tps);
                    }

                    case "getNeighbors": {
                        return getNeighborsStatement();
                    }

                    case "getNodeInfo": {

                        return GetNodeInfoResponse.create(IRI.NAME,
                        		IRI.VERSION,
                        		Runtime.getRuntime().availableProcessors(),
                        		Runtime.getRuntime().freeMemory(),
                        		Runtime.getRuntime().maxMemory(),
                        		Runtime.getRuntime().totalMemory(),
                        		Milestone.latestMilestone,
                        		Milestone.latestMilestoneIndex,
                        		Milestone.latestSolidSubtangleMilestone,
                        		Milestone.latestSolidSubtangleMilestoneIndex,
                        		Node.neighbors.size(),
                        		Node.queuedTransactions.size(),
                        		System.currentTimeMillis(),
                        		Storage.tips().size(),
                        		Storage.numberOfTransactionsToRequest);
                    }

                    case "getTips": {
                        return getTipsStatement();
                    }
                    case "getTransactionsToApprove": {
                        final int depth = (Integer)request.get("depth");
                        return getTransactionToApproveStatement(depth);
                    }
                    case "getTrytes": {
                    	final List<String> hashes = (List<String>)request.get("hashes");                  
                    	getTrytesStatement(hashes);
                    }

                    case "interruptAttachingToTangle": {
                        pearlDiver.interrupt();
                        return AbstractResponse.createEmptyResponse();
                    }
                    case "removeNeighbors": {
                    	List<String> uris = (List<String>)request.get("uris");
                        return removeNeighborsStatement(uris);
                    }

                    case "storeTransactions": {
                    	List<String> trytes = (List<String>)request.get("trytes");
                        return storeTransactionStatement(trytes);
                    }
                    default: 
                    	return ErrorResponse.create("Command '" + command + "' is unknown");
                }
                
            } catch (final Exception e) {
                log.error("API Exception: ", e);
                return ExceptionResponse.create(e.getLocalizedMessage());
            }
        }

		private AbstractResponse removeNeighborsStatement(List<String> uris) throws URISyntaxException {
			int numberOfRemovedNeighbors = 0;
			for (final String uriString : uris) {

			    final URI uri = new URI(uriString);
			    if (uri.getScheme() != null && uri.getScheme().equals("udp")) {

			        if (Node.neighbors.remove(new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort())))) {
			            numberOfRemovedNeighbors++;
			        }
			    }
			}
			return RemoveNeighborsResponse.create(numberOfRemovedNeighbors);
		}

		private void getTrytesStatement(List<String> hashes) {
			final List<String> elements = new LinkedList<>();
			for (final String hash : hashes) {
			    final Transaction transaction = Storage.loadTransaction((new Hash(hash)).bytes());
			    elements.add(transaction == null ? "null" : (Converter.trytes(transaction.trits())));
			}
			GetTrytesResponse.create(elements);
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
			final List<String> elements = new LinkedList<>();
			for (final Hash tip : Storage.tips()) {
			    elements.add(tip.toString());
			}
			return GetTipsResponse.create(elements);
		}

		private AbstractResponse storeTransactionStatement(List<String> trys) {
			for (final String trytes : trys) {
			    final Transaction transaction = new Transaction(Converter.trits(trytes));
			    Storage.storeTransaction(transaction.hash, transaction, false);
			}

			return AbstractResponse.createEmptyResponse();
		}

		private AbstractResponse getNeighborsStatement() {
			final List<String> elements = new LinkedList<>();
			for (final Neighbor neighbor : Node.neighbors) {
			    elements.add("{" + neighbor + "}");
			}
			return GetNeighborsResponse.create(elements);
		}

		private AbstractResponse getInclusionStateStatement(final List<String> trans, final List<String> tps) {
			final List<Hash> transactions = new LinkedList<>();
			for (final String transaction : trans) {
			    transactions.add((new Hash(transaction)));
			}

			final List<Hash> tips = new LinkedList<>();
			for (final String tip : tps) {
			    tips.add(new Hash(tip));
			}

			int numberOfNonMetTransactions = transactions.size();
			final boolean[] inclusionStates = new boolean[numberOfNonMetTransactions];

			synchronized (Storage.analyzedTransactionsFlags) {

			    Storage.clearAnalyzedTransactionsFlags();

			    final Queue<Long> nonAnalyzedTransactions = new LinkedList<>();
			    for (final Hash tip : tips) {

			        final long pointer = Storage.transactionPointer(tip.bytes());
			        if (pointer <= 0) {
			        	return ErrorResponse.create("One of the tips absents");
			        }
			        nonAnalyzedTransactions.offer(pointer);
			    }

			    {
			        Long pointer;
			    MAIN_LOOP:
			        while ((pointer = nonAnalyzedTransactions.poll()) != null) {

			            if (Storage.setAnalyzedTransactionFlag(pointer)) {

			                final Transaction transaction = Storage.loadTransaction(pointer);
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
			    for (final String bundle : (List<String>)request.get("bundles")) {
			        bundlesTransactions.addAll(Storage.bundleTransactions(Storage.bundlePointer((new Hash(bundle)).bytes())));
			    }
			}

			final Set<Long> addressesTransactions = new HashSet<>();
			if (request.containsKey("addresses")) {
			    for (final String address : (List<String>)request.get("addresses")) {
			        addressesTransactions.addAll(Storage.addressTransactions(Storage.addressPointer((new Hash(address)).bytes())));
			    }
			}

			final Set<Long> tagsTransactions = new HashSet<>();
			if (request.containsKey("tags")) {
			    for (String tag : (List<String>)request.get("tags")) {
			        while (tag.length() < Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE) {
			            tag += Converter.TRYTE_ALPHABET.charAt(0);
			        }
			        tagsTransactions.addAll(Storage.tagTransactions(Storage.tagPointer((new Hash(tag)).bytes())));
			    }
			}

			final Set<Long> approveeTransactions = new HashSet<>();;
			if (request.containsKey("approvees")) {
			    for (final String approvee : (List<String>)request.get("approvees")) {
			        approveeTransactions.addAll(Storage.approveeTransactions(Storage.approveePointer((new Hash(approvee)).bytes())));
			    }
			}
			
			// jesus...
			final Set<Long> foundTransactions = bundlesTransactions.isEmpty() ?
					(addressesTransactions.isEmpty() ?
							(tagsTransactions.isEmpty() ?
									(approveeTransactions.isEmpty() ? new HashSet<>() : 
										approveeTransactions) : 
											tagsTransactions) : 
												addressesTransactions) : 
													bundlesTransactions;
			
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
			    elements.add(new Hash(Storage.loadTransaction(pointer).hash, 0, Transaction.HASH_SIZE).toString());
			}

			return FindTransactionesponse.create(elements);
		}

		private AbstractResponse broadcastTransactionStatement(final List<String> trytes2) {
			for (final String tryte : trytes2) {

			    final Transaction transaction = new Transaction(Converter.trits(tryte));
			    transaction.weightMagnitude = Curl.HASH_LENGTH;
			    Node.broadcast(transaction);
			}

			return AbstractResponse.createEmptyResponse();
		}

		private AbstractResponse getBalancesStatement(final List<String> addrss, final int threshold) {

			if (threshold <= 0 || threshold > 100) {
			    return ErrorResponse.create("Illegal 'threshold'");
			}
			
			final List<Hash> addresses = new LinkedList<>();
			for (final String address : addrss) {
			    addresses.add((new Hash(address)));
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
			    elements.add(balances.get(address).toString());
			}

			return GetBalancesResponse.create(elements, milestone, milestoneIndex);
		}

		private AbstractResponse attachToTangleStatement(final Hash trunkTransaction, final Hash branchTransaction,
		        final int minWeightMagnitude, final List<String> trytes) {
			final List<Transaction> transactions = new LinkedList<>();

			Hash prevTransaction = null;
			
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
			return AttachToTangleResponse.create(elements);
		}

		private void sendResponse(final AbstractResponse res, final long beginningTime) throws UnsupportedEncodingException {
			final String response = gson.toJson(res);
			String wrapResponse = "{" + response + (response.length() == 0 ? "" : ", ") + "\"duration\": " + (System.currentTimeMillis() - beginningTime) + "}";
            wrapResponse = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " + response.getBytes("UTF-8").length + "\r\nConnection: close\r\n\r\n" + response;
            
            final byte[] responseBytes = wrapResponse.getBytes("UTF-8");
            final ByteBuffer bufferres = ByteBuffer.allocateDirect(responseBytes.length);
            bufferres.put(responseBytes);
            bufferres.flip();
            channel.write(bufferres, this, new CompletionHandler<Integer, Request>() {

                @Override
                public void completed(final Integer numberOfBytes, final Request request) {

                    if (bufferres.hasRemaining()) {
                        channel.write(bufferres, request, this);
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

		private AbstractResponse addNeighborsStatement(final List<String> uris) throws URISyntaxException {
			
			int numberOfAddedNeighbors = 0;
			for (final String uriString : uris) {

			    final URI uri = new URI(uriString);
			    if (uri.getScheme() != null && uri.getScheme().equals("udp")) {

			        final Neighbor neighbor = new Neighbor(new InetSocketAddress(uri.getHost(), uri.getPort()));
			        if (!Node.neighbors.contains(neighbor)) {

			            Node.neighbors.add(neighbor);
			            numberOfAddedNeighbors++;
			        }
			    }
			}

			return AddedNeighborsResponse.create(numberOfAddedNeighbors);
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
