package com.iota.iri.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.*;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.controllers.AddressViewModel;
import com.iota.iri.controllers.BundleViewModel;
import com.iota.iri.controllers.TagViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.PearlDiver;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Neighbor;
import com.iota.iri.network.TCPNeighbor;
import com.iota.iri.network.UDPNeighbor;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ABI {

    private static final Logger log = LoggerFactory.getLogger(ABI.class);

    protected volatile PearlDiver pearlDiver = new PearlDiver();

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

    public ABI(Iota instance) {
        this.instance = instance;
        minRandomWalks = instance.configuration.integer(DefaultConfSettings.MIN_RANDOM_WALKS);
        maxRandomWalks = instance.configuration.integer(DefaultConfSettings.MAX_RANDOM_WALKS);
        maxFindTxs = instance.configuration.integer(DefaultConfSettings.MAX_FIND_TRANSACTIONS);
        maxGetTrytes = instance.configuration.integer(DefaultConfSettings.MAX_GET_TRYTES);

    }

    public int removeNeighborsStatement(List<String> uris) throws Exception {
        final AtomicInteger numberOfRemovedNeighbors = new AtomicInteger(0);
        
        for (final String uriString : uris) {
            final URI uri = new URI(uriString);
            
            if ("udp".equals(uri.getScheme()) || "tcp".equals(uri.getScheme())) {
                log.info("Removing neighbor: "+uriString);
                if (instance.node.removeNeighbor(uri,true)) {
                    numberOfRemovedNeighbors.incrementAndGet();
                }
            }
            else {
                throw new Exception("Invalid uri scheme");
            }
        }
        return numberOfRemovedNeighbors.get();
    }

    public List<String> getTrytesStatement(List<String> hashes) throws Exception {
        final List<String> elements = new LinkedList<>();
        for (final String hash : hashes) {
            final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, new Hash(hash));
            if (transactionViewModel != null) {
                elements.add(Converter.trytes(transactionViewModel.trits()));
            }
        }
        if (elements.size() > maxGetTrytes){
            throw new Exception("Could not complete request");
        }
        return elements;
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

    public synchronized Hash[] getTransactionToApproveStatement(final int depth, final String reference, final int numWalks) throws Exception {
        int tipsToApprove = 2;
        Hash[] tips = new Hash[tipsToApprove];
        final SecureRandom random = new SecureRandom();
        final int randomWalkCount = numWalks > maxRandomWalks || numWalks < 1 ? maxRandomWalks:numWalks;
        Hash referenceHash = null;
        if(reference != null) {
            referenceHash = new Hash(reference);
            if(!TransactionViewModel.exists(instance.tangle, referenceHash)) {
                referenceHash = null;
            }
        }
        for(int i = 0; i < tipsToApprove; i++) {
            tips[i] = instance.tipsManager.transactionToApprove(referenceHash, tips[0], depth, randomWalkCount, random);
            if (tips[i] == null) {
                return null;
            }
        }
        ABI.incCounter_getTxToApprove();
        if ( ( getCounter_getTxToApprove() % 100) == 0 ) {
            String sb = "Last 100 getTxToApprove consumed " +
                    ABI.getEllapsedTime_getTxToApprove() / 1000000000L +
                    " seconds processing time.";
            log.info(sb);
            counter_getTxToApprove = 0;
            ellapsedTime_getTxToApprove = 0L;
        }
        return tips;
    }

    private synchronized List<String> getTipsStatement() throws Exception {
        return instance.tipsViewModel.getTips().stream().map(Hash::toString).collect(Collectors.toList());
    }

    public boolean storeTransactionStatement(final List<String> trys) throws Exception {
        for (final String trytes : trys) {

            if (!validTrytes(trytes, TRYTES_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                return false;
            }
            final TransactionViewModel transactionViewModel = instance.transactionValidator.validate(Converter.trits(trytes),
                    instance.transactionValidator.getMinWeightMagnitude());
            if(transactionViewModel.store(instance.tangle)) {
                transactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                instance.transactionValidator.updateStatus(transactionViewModel);
                transactionViewModel.updateSender("local");
                transactionViewModel.update(instance.tangle, "sender");
            }
        }
        return true;
    }

    private List<Neighbor> getNeighborsStatement() {
        return instance.node.getNeighbors();
    }

    public boolean[] getNewInclusionStateStatement(final List<String> trans, final List<String> tps) throws Exception {
        final List<Hash> transactions = trans.stream().map(Hash::new).collect(Collectors.toList());
        final List<Hash> tips = tps.stream().map(Hash::new).collect(Collectors.toList());
        int numberOfNonMetTransactions = transactions.size();
        final int[] inclusionStates = new int[numberOfNonMetTransactions];

        List<Integer> tipsIndex = new LinkedList<>();
        {
            for(Hash hash: tips) {
                TransactionViewModel tx = TransactionViewModel.fromHash(instance.tangle, hash);
                if (tx.getType() != TransactionViewModel.PREFILLED_SLOT) {
                    tipsIndex.add(tx.snapshotIndex());
                }
            }
        }
        int minTipsIndex = tipsIndex.stream().reduce((a,b) -> a < b ? a : b).orElse(0);
        if(minTipsIndex > 0) {
            int maxTipsIndex = tipsIndex.stream().reduce((a,b) -> a > b ? a : b).orElse(0);
            for(Hash hash: transactions) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(instance.tangle, hash);
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
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, tip);
            if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT){
                throw new Exception("One of the tips absents");
            }
            sameIndexTips.putIfAbsent(transactionViewModel.snapshotIndex(), new HashSet<>());
            sameIndexTips.get(transactionViewModel.snapshotIndex()).add(tip);
            nonAnalyzedTransactionsMap.putIfAbsent(transactionViewModel.snapshotIndex(), new LinkedList<>());
            nonAnalyzedTransactionsMap.get(transactionViewModel.snapshotIndex()).offer(tip);
        }
        for(int i = 0; i < inclusionStates.length; i++) {
            if(inclusionStates[i] == 0) {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, transactions.get(i));
                sameIndexTransactions.putIfAbsent(transactionViewModel.snapshotIndex(), new HashSet<>());
                sameIndexTransactions.get(transactionViewModel.snapshotIndex()).add(transactionViewModel.getHash());
            }
        }
        for(Map.Entry<Integer, Set<Hash>> entry: sameIndexTransactions.entrySet()) {
            if(!exhaustiveSearchWithinIndex(nonAnalyzedTransactionsMap.get(entry.getKey()), analyzedTips, transactions, inclusionStates, entry.getValue().size(), entry.getKey())) {
                throw new Exception("The subtangle is not solid");
            }
        }
        final boolean[] inclusionStatesBoolean = new boolean[inclusionStates.length];
        for(int i = 0; i < inclusionStates.length; i++) {
            inclusionStatesBoolean[i] = inclusionStates[i] == 1;
        }
        {
            return inclusionStatesBoolean;
        }
    }
    private boolean exhaustiveSearchWithinIndex(Queue<Hash> nonAnalyzedTransactions, Set<Hash> analyzedTips, List<Hash> transactions, int[] inclusionStates, int count, int index) throws Exception {
        Hash pointer;
        MAIN_LOOP:
        while ((pointer = nonAnalyzedTransactions.poll()) != null) {


            if (analyzedTips.add(pointer)) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, pointer);
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

    public synchronized List<String> findTransactionStatement(final Map<String, Object> request) throws Exception {
        final Set<Hash> bundlesTransactions = new HashSet<>();

        if (request.containsKey("bundles")) {
            for (final String bundle : (List<String>) request.get("bundles")) {
                if (!validTrytes(bundle, HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                    throw new Exception("Invalid bundle hash");
                }
                bundlesTransactions.addAll(BundleViewModel.load(instance.tangle, new Hash(bundle)).getHashes());
            }
        }

        final Set<Hash> addressesTransactions = new HashSet<>();
        if (request.containsKey("addresses")) {
            final List<String> addresses = (List<String>) request.get("addresses");
            log.debug("Searching: {}", addresses.stream().reduce((a, b) -> a += ',' + b));

            for (final String address : addresses) {
                if (!validTrytes(address, HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                    throw new Exception("Invalid address input");
                }
                addressesTransactions.addAll(AddressViewModel.load(instance.tangle, new Hash(address)).getHashes());
            }
        }

        final Set<Hash> tagsTransactions = new HashSet<>();
        if (request.containsKey("tags")) {
            for (String tag : (List<String>) request.get("tags")) {
                if (!validTrytes(tag,tag.length(), ZERO_LENGTH_NOT_ALLOWED)) {
                    throw new Exception("Invalid tag input");
                }
                while (tag.length() < Curl.HASH_LENGTH / Converter.NUMBER_OF_TRITS_IN_A_TRYTE) {
                    tag += Converter.TRYTE_ALPHABET.charAt(0);
                }
                tagsTransactions.addAll(TagViewModel.load(instance.tangle, new Hash(tag)).getHashes());
            }
        }

        final Set<Hash> approveeTransactions = new HashSet<>();

        if (request.containsKey("approvees")) {
            for (final String approvee : (List<String>) request.get("approvees")) {
                if (!validTrytes(approvee,HASH_SIZE, ZERO_LENGTH_NOT_ALLOWED)) {
                    throw new Exception("Invalid approvees hash");
                }
                approveeTransactions.addAll(TransactionViewModel.fromHash(instance.tangle, new Hash(approvee)).getApprovers(instance.tangle).getHashes());
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
        if (foundTransactions.size() > maxFindTxs){
            throw new Exception("Could not complete request");
        }

        final List<String> elements = foundTransactions.stream()
                .map(Hash::toString)
                .collect(Collectors.toCollection(LinkedList::new));

        return elements;
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

    public Pair<List<String>, Pair<Hash, Integer>> getBalancesStatement(final List<String> addrss, final int threshold) throws Exception {

        if (threshold <= 0 || threshold > 100) {
            throw new Exception("Illegal 'threshold'");
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
                    //Collections.singleton(StorageTransactions.instance().transactionPointer(milestone.value())));
            Hash hash;
            while ((hash = nonAnalyzedTransactions.poll()) != null) {

                if (analyzedTips.add(hash)) {

                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(instance.tangle, hash);

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

        return new Pair<>(elements, new Pair<>(milestone, milestoneIndex));
    }

    private static int counter_PoW = 0;
    public static int getCounter_PoW() {
        return counter_PoW;
    }
    public static void incCounter_PoW() {
        ABI.counter_PoW++;
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
                //tag - copy the obsolete tag to the attachment tag field
                System.arraycopy(transactionTrits, TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET,
                        transactionTrits, TransactionViewModel.TAG_TRINARY_OFFSET,
                        TransactionViewModel.TAG_TRINARY_SIZE);

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
                ABI.incEllapsedTime_PoW(System.nanoTime() - startTime);
                ABI.incCounter_PoW();
                if ( ( ABI.getCounter_PoW() % 100) == 0 ) {
                    String sb = "Last 100 PoW consumed " +
                            ABI.getEllapsedTime_PoW() / 1000000000L +
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

    public int addNeighborsStatement(final List<String> uris) throws URISyntaxException {

        int numberOfAddedNeighbors = 0;
        for (final String uriString : uris) {
            final URI uri = new URI(uriString);
            
            if ("udp".equals(uri.getScheme()) || "tcp".equals(uri.getScheme())) {
                log.info("Adding neighbor: "+uriString);
                // 3rd parameter true if tcp, 4th parameter true (configured tethering)
                final Neighbor neighbor;
                switch(uri.getScheme()) {
                    case "tcp":
                        neighbor = new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()),true);
                        break;
                    case "udp":
                        neighbor = new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), instance.node.getUdpSocket(), true);
                        break;
                    default:
                        throw new Error("Invalid uri scheme");
                }
                if (!instance.node.getNeighbors().contains(neighbor)) {
                    instance.node.getNeighbors().add(neighbor);
                    numberOfAddedNeighbors++;
                }
            }
            else {
                throw new Error("Invalid uri scheme");
            }
        }
        return numberOfAddedNeighbors;
    }

    public boolean validTrytes(String trytes, int minimalLength, char zeroAllowed) {
        if (trytes.length() == 0 && zeroAllowed == ZERO_LENGTH_ALLOWED) {
            return true;
        }
        if (trytes.length() < minimalLength) {
            return false;
        }
        Matcher matcher = trytesPattern.matcher(trytes);
        return matcher.matches();
    }
}