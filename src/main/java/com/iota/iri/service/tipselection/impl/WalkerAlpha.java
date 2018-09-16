package com.iota.iri.service.tipselection.impl;

import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.service.tipselection.TailFinder;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.service.tipselection.Walker;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import com.iota.iri.zmq.MessageQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of <tt>Walker</tt> that performs a weighted random walk
 * with <CODE>e^(alpha*Hy)</CODE> as the transition function.
 *
 */
public class WalkerAlpha implements Walker {

    private double alpha;
    private final Random random;

    private final Tangle tangle;
    private final MessageQ messageQ;
    private final Logger log = LoggerFactory.getLogger(Walker.class);

    private final TailFinder tailFinder;

    public WalkerAlpha(TailFinder tailFinder, Tangle tangle, MessageQ messageQ, Random random, TipSelConfig config) {
        this.tangle = tangle;
        this.messageQ = messageQ;
        this.tailFinder = tailFinder;
        this.random = random;
        this.alpha = config.getAlpha();
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public Hash walk(Hash entryPoint, UnIterableMap<HashId, Integer> ratings, WalkValidator walkValidator) throws Exception {
        if (!walkValidator.isValid(entryPoint)) {
            throw new IllegalStateException("entry point failed consistency check: " + entryPoint.toString());
        }
        
        Optional<Hash> nextStep;
        Deque<Hash> traversedTails = new LinkedList<>();
        traversedTails.add(entryPoint);

        //Walk
        do {
            nextStep = selectApprover(traversedTails.getLast(), ratings, walkValidator);
            nextStep.ifPresent(traversedTails::add);
         } while (nextStep.isPresent());
        
        log.debug("{} tails traversed to find tip", traversedTails.size());
        messageQ.publish("mctn %d", traversedTails.size());

        return traversedTails.getLast();
    }

    private Optional<Hash> selectApprover(Hash tailHash, UnIterableMap<HashId, Integer> ratings, WalkValidator walkValidator) throws Exception {
        Set<Hash> approvers = getApprovers(tailHash);
        return findNextValidTail(ratings, approvers, walkValidator);
    }

    private Set<Hash> getApprovers(Hash tailHash) throws Exception {
        ApproveeViewModel approveeViewModel = ApproveeViewModel.load(tangle, tailHash);
        return approveeViewModel.getHashes();
    }

    private Optional<Hash> findNextValidTail(UnIterableMap<HashId, Integer> ratings, Set<Hash> approvers, WalkValidator walkValidator) throws Exception {
        Optional<Hash> nextTailHash = Optional.empty();

        //select next tail to step to
        while (!nextTailHash.isPresent()) {
            Optional<Hash> nextTxHash = select(ratings, approvers);
            if (!nextTxHash.isPresent()) {
                //no existing approver = tip
                return Optional.empty();
            }

            nextTailHash = findTailIfValid(nextTxHash.get(), walkValidator);
            approvers.remove(nextTxHash.get());
            //if next tail is not valid, re-select while removing it from approvers set
        }

        return nextTailHash;
    }

    private Optional<Hash> select(UnIterableMap<HashId, Integer> ratings, Set<Hash> approversSet) {

        //filter based on tangle state when starting the walk
        List<Hash> approvers = approversSet.stream().filter(ratings::containsKey).collect(Collectors.toList());

        //After filtering, if no approvers are available, it's a tip.
        if (approvers.size() == 0) {
            return Optional.empty();
        }

        //calculate the probabilities
        List<Integer> walkRatings = approvers.stream().map(ratings::get).collect(Collectors.toList());

        Integer maxRating = walkRatings.stream().max(Integer::compareTo).orElse(0);
        //walkRatings.stream().reduce(0, Integer::max);

        //transition probability function (normalize ratings based on Hmax)
        List<Integer> normalizedWalkRatings = walkRatings.stream().map(w -> w - maxRating).collect(Collectors.toList());
        List<Double> weights = normalizedWalkRatings.stream().map(w -> Math.exp(alpha * w)).collect(Collectors.toList());

        //select the next transaction
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

    private Optional<Hash> findTailIfValid(Hash transactionHash, WalkValidator validator) throws Exception {
        Optional<Hash> tailHash = tailFinder.findTail(transactionHash);
        if (tailHash.isPresent() && validator.isValid(tailHash.get())) {
                return tailHash;
        }
        return Optional.empty();
    }
}
