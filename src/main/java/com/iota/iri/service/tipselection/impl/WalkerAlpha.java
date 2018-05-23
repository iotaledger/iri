package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.service.tipselection.Walker;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class WalkerAlpha implements Walker {

    private final int maxDepth;
    private double alpha;
    private final Random random;

    private final Tangle tangle;
    private final MessageQ messageQ;
    private final Logger log = LoggerFactory.getLogger(Walker.class);

    private final TailFinder tailFinder;

    private final LedgerValidator ledgerValidator;
    private final TransactionValidator transactionValidator;


    public WalkerAlpha(double alpha, Random random, Tangle tangle, LedgerValidator ledgerValidator, TransactionValidator transactionValidator, MessageQ messageQ, int maxDepth, TailFinder tailFinder) {

        this.alpha = alpha;
        //TODO, check if random (secureRandom) is thread safe
        this.random = random;

        this.tangle = tangle;
        this.messageQ = messageQ;

        this.ledgerValidator = ledgerValidator;
        this.transactionValidator = transactionValidator;

        this.maxDepth = maxDepth;
        this.tailFinder = tailFinder;

    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public Hash walk(Hash entryPoint, Map<Hash, Integer> ratings, WalkValidator walkValidator) throws Exception {

        Hash tailId;
        Optional<Hash> nextTailId;
        boolean tailIdisTip = false;
        int traversedTails = 0;

        // check entryPoint is valid
        if (!walkValidator.isValid(entryPoint)) {
            throw new RuntimeException("entry point failed consistency check: " + entryPoint.toString());
        }

        tailId = entryPoint;

        //Walk
        while (!tailIdisTip) {
            Set<Hash> approvers = ApproveeViewModel.load(tangle, tailId).getHashes();

            nextTailId = findNextValidTail(ratings, approvers, walkValidator);
            if (nextTailId.isPresent()) {
                tailId = nextTailId.get();
                traversedTails++;
            } else {
                //if no next step available = tip
                tailIdisTip = true;
            }
        }

        //TODO remove/rewrite "rts" log messages
        log.debug("Reason to stop: TransactionViewModel is a tip");
        messageQ.publish("rtst %s", tailId);

        log.info("Tx traversed to find tip: " + traversedTails);
        messageQ.publish("mctn %d", traversedTails);

        return tailId;
    }

    private Optional<Hash> findNextValidTail(Map<Hash, Integer> ratings, Set<Hash> approvers, WalkValidator walkValidator) throws Exception {
        Optional<Hash> nextTxId;
        Optional<Hash> nextTailId;

        //select next tail to step to
        do {
            nextTxId = select(ratings, approvers);
            if (!nextTxId.isPresent()) {
                return Optional.empty();
            }

            nextTailId = findTailIfValid(nextTxId.get(), walkValidator);
            approvers.remove(nextTxId.get());

            //if next tail is not valid, re-select while removing it from approvers set
        } while (!nextTailId.isPresent());
        return nextTailId;
    }

    Optional<Hash> select(Map<Hash, Integer> ratings, Set<Hash> approversSet) {

        if (approversSet.size() == 0) {
            return Optional.empty();
        }

        //filter based on tangle state when starting the walk
        List<Hash> approvers = approversSet.stream().filter(ratings::containsKey).collect(Collectors.toList());

        //calculate the probabilities
        List<Integer> walkRatings = approvers.stream().map(ratings::get).collect(Collectors.toList());

        //TODO: Integer stream
        Integer maxRating = walkRatings.stream().max(Integer::compareTo).orElse(0);
        //walkRatings.stream().reduce(0, Integer::max);

        //transition probability function (normalize ratings based on Hmax)
        List<Integer> normalizedWalkRatings = walkRatings.stream().map(w -> w - maxRating).collect(Collectors.toList());
        List<Double> weights = normalizedWalkRatings.stream().map(w -> Math.exp(alpha * w)).collect(Collectors.toList());

        //select the next transaction
        //TODO: Double stream
        Double weightsSum = weights.stream().reduce(0.0, Double::sum);
        double target = random.nextDouble() * weightsSum;

        int approverIndex;
        for (approverIndex = 0; approverIndex < weights.size() - 1; approverIndex++) {
            target -= weights.get(approverIndex);
            if (target <= 0) {
                break;
            }
        }

        return Optional.of(approvers.get(approverIndex));

    }

    private Optional<Hash> findTailIfValid(Hash transactionId, WalkValidator validator) throws Exception {
        Optional<Hash> tailId = tailFinder.findTail(transactionId);
        if (tailId.isPresent()) {
            if (validator.isValid(tailId.get())) {
                return tailId;
            }
        }
        return Optional.empty();
    }
}
