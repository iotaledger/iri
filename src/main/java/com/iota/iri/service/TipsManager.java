package com.iota.iri.service;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.iota.iri.LedgerValidator;
import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.*;
import com.iota.iri.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 75; // Must be in [0..100] range

    public static void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }
    
    static Hash transactionToApprove(final Hash extraTip, final int depth, Random seed) {

        int milestoneDepth = depth;

        long startTime = System.nanoTime();

        if(Milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX ||
                Milestone.latestMilestoneIndex == Milestone.MILESTONE_START_INDEX) {
            final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;

            Map<Hash, Long> ratings = new HashMap<>();
            Set<Hash> analyzedTips = new HashSet<>();
            try {
                int traversedTails = 0;
                Hash tip = preferableMilestone;
                Hash tail = tip;
                if (extraTip != null) {
                    int milestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex - milestoneDepth;
                    if(milestoneIndex < 0) {
                        milestoneIndex = 0;
                    }
                    if(!MilestoneViewModel.load(milestoneIndex)) {
                        Map.Entry<Integer, Hash> closestGreaterMilestone = Milestone.findMilestone(milestoneIndex);
                        new MilestoneViewModel(closestGreaterMilestone.getKey(), closestGreaterMilestone.getValue()).store();
                    }
                }

                updateRatings(tip, ratings, analyzedTips);
                analyzedTips.clear();

                Hash[] tips;
                TransactionViewModel transactionViewModel;
                int carlo;
                double monte;
                while (tip != null) {
                    tips = TransactionViewModel.fromHash(tip).getApprovers();
                    if (tips.length == 0) {
                        break;
                    }
                    if (!ratings.containsKey(tip)) {
                        updateRatings(tip, ratings, analyzedTips);
                        analyzedTips.clear();
                    }
                    monte = seed.nextDouble() * ratings.get(tip);
                    for (carlo = tips.length; carlo-- > 1; ) {
                        if (ratings.containsKey(tips[carlo])) {
                            monte -= ratings.get(tips[carlo]);
                        }
                        if (monte <= 0) {
                            break;
                        }
                    }
                    transactionViewModel = TransactionViewModel.fromHash(tips[carlo]);
                    if (transactionViewModel == null) {
                        break;
                    } else if (!(checkSolidity(transactionViewModel.getHash()) && LedgerValidator.updateFromSnapshot(transactionViewModel.getHash()))) {
                        break;
                    } else if (transactionViewModel.getHash().equals(extraTip) || transactionViewModel.getHash().equals(tip)) {
                        break;
                    } else {
                        traversedTails++;
                        tip = transactionViewModel.getHash();
                        if(transactionViewModel.getCurrentIndex() == 0) {
                            tail = tip;
                        }
                    }
                }
                log.info("Tx traversed to find tip: " + traversedTails);
                return tail;
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Encountered error: " + e.getLocalizedMessage());
            } finally {
                API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
            }
        }
        return null;
    }


    public static boolean checkSolidity(Hash hash) throws Exception {
        Set<Hash> analyzedHashes = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer, trunkInteger, branchInteger;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashPointer);
                if(!transactionViewModel2.isSolid()) {
                    if (transactionViewModel2.getType() == TransactionViewModel.PREFILLED_SLOT && !hashPointer.equals(Hash.NULL_HASH)) {
                        TransactionRequester.instance().requestTransaction(hashPointer);
                        solid = false;
                        break;

                    } else {
                        trunkInteger = transactionViewModel2.getTrunkTransactionHash();
                        branchInteger = transactionViewModel2.getBranchTransactionHash();
                        nonAnalyzedTransactions.offer(trunkInteger);
                        nonAnalyzedTransactions.offer(branchInteger);
                    }
                }
            }
        }
        if (solid) {
            printNewSolidTransactions(analyzedHashes);
            TransactionViewModel.updateSolidTransactions(analyzedHashes);
        }
        return solid;
    }

    public static void printNewSolidTransactions(Collection<Hash> hashes) {
        if (Configuration.booling(Configuration.DefaultConfSettings.EXPORT)) {
            hashes.remove(Hash.NULL_HASH);
            hashes.forEach(hash -> {
                TransactionViewModel tx;
                try {
                    PrintWriter writer;
                    tx = TransactionViewModel.fromHash(hash);
                    if(!tx.isSolid()) {
                        Path path = Paths.get("export-solid", String.valueOf(getFileNumber()) + ".tx");
                        long height = tx.getHeight();
                        writer = new PrintWriter(path.toString(), "UTF-8");
                        writer.println(tx.getHash().toString());
                        writer.println(Converter.trytes(tx.trits()));
                        writer.println(tx.getSender());                        
                        writer.println("Height: " + String.valueOf(height));
                        writer.close();
                        log.info("Height: " + height);
                    }
                } catch (UnsupportedEncodingException | FileNotFoundException e) {
                    log.error("File export failed", e);
                } catch (Exception e) {
                    log.error("Transaction load failed. ", e);
                }
            });
        }
    }

    private static long updateRatings(Hash txHash, Map<Hash, Long> ratings, Set<Hash> analyzedTips) throws Exception {
        long rating = 1;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(txHash);
            for(Hash approver : transactionViewModel.getApprovers()) {
                rating += updateRatings(approver, ratings, analyzedTips);
            }
            if(rating > Long.MAX_VALUE/2) {
                rating = Long.MAX_VALUE/2;
            }
            ratings.put(txHash, rating);
        } else {
            if(ratings.containsKey(txHash)) {
                rating = ratings.get(txHash);
            } else {
                rating = 0;
            }
        }
        return rating;       
    }

    private static long lastFileNumber = 0L;
    private static Object lock = new Object();

    public static long getFileNumber() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (now < lastFileNumber) {
                return ++lastFileNumber;
            }
            lastFileNumber = now;
        }
        return now;
    }

    public static TipsManager instance() {
        return instance;
    }
    
    private TipsManager() {}
    
    private static final TipsManager instance = new TipsManager();
}
