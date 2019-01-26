package com.iota.iri.network.spam;

import com.iota.iri.network.Neighbor;
import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DropFixedPercentage implements SpamPreventionStrategy {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int dropPercentage;
    private Map<Neighbor, Long> transactionCount;
    private Set<Neighbor> mostActiveNeighbors = new HashSet<>();

    /**
     * Creates a new spam strategy that drops a fixed percentage of neighbors.
     * @param dropPercentage The percentage of neighbors to be dropped. Percentage will be rounded down. So 33% of 3
     *                       neighbors are 0 neighbors that get blocked. 34% of 3 neighbors is one neighbor.
     * @param neighbors The list of neighbors that should be examined for finding spamming nodes.
     */
    public DropFixedPercentage(int dropPercentage, List<Neighbor> neighbors) {
        logger.info("Created fixed percentage spam prevention strategy. Dropping {}% of most active neighbors.", dropPercentage);
        this.dropPercentage = dropPercentage;
        transactionCount = toMap(neighbors);
    }

    /**
     * @inheritDoc
     */
    public void calculateSpam(List<Neighbor> neighbors) {
        List<Pair<Long, Neighbor>> deltas = new ArrayList<>(neighbors.size());
        neighbors.forEach(neighbor -> {
            Long previousCount = transactionCount.computeIfAbsent(neighbor, Neighbor::getNumberOfAllTransactions);
            long delta = neighbor.getNumberOfAllTransactions() - previousCount;
            if (delta > 0) { // only active neighbors count
                deltas.add(new Pair<>(delta, neighbor));
            }
        });
        Collections.shuffle(deltas); // in case we have similar deltas
        int droppedNeighbors = (int) (deltas.size() * (dropPercentage / 100f)); // round down
        mostActiveNeighbors = deltas.stream().sorted(Comparator.comparing(p -> -p.low)).limit(droppedNeighbors).map(p -> p.hi).collect(Collectors.toSet());
        transactionCount = toMap(neighbors);
        debugLog(deltas, droppedNeighbors);
    }

    /**
     * @inheritDoc
     */
    public boolean isSpamming(Neighbor neighbor) {
        return mostActiveNeighbors.contains(neighbor);
    }

    private Map<Neighbor, Long> toMap(List<Neighbor> neighbors) {
        return neighbors.stream().collect(Collectors.toMap(Function.identity(), Neighbor::getNumberOfAllTransactions));
    }

    private void debugLog(List<Pair<Long, Neighbor>> deltas, int droppedNeighbors) {
        if (logger.isDebugEnabled()) {
            logger.debug("Deltas: {}", deltas.stream().map(p -> p.hi.getHostAddress() + "=" + p.low).collect(Collectors.joining(", ")));
            logger.debug("{}% are [{}] neighbor(s).", dropPercentage, droppedNeighbors);
            logger.debug("Most active neighbor(s): {}", mostActiveNeighbors.stream().map(Neighbor::getHostAddress).collect(Collectors.joining(", ")));
        }
    }





}
