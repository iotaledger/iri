package com.iota.iri.network.spam;

import com.iota.iri.network.Neighbor;
import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Simple spam prevention strategy that considers a percentage of the most active neighbors to be spamming.
 */
public class DropFixedPercentage implements SpamPreventionStrategy {

    // needs to be static according to IRI code style
    private static final Logger LOG = LoggerFactory.getLogger(DropFixedPercentage.class);

    private final int dropPercentage;
    private Map<Neighbor, Long> transactionCount;
    private Set<Neighbor> mostActiveNeighbors = Collections.emptySet();

    /**
     * Creates a new spam strategy that marks a fixed percentage of neighbors to be spamming.
     * @param dropPercentage The percentage of neighbors that are considered to be spamming. Percentage will be rounded
     *                       down. So 33% of 3 neighbors are 0 neighbors. 34% of 3 neighbors is one neighbor.
     * @param neighbors The list of neighbors that should be examined for finding spamming nodes.
     */
    public DropFixedPercentage(int dropPercentage, List<Neighbor> neighbors) {
        LOG.info("Created fixed percentage spam prevention strategy. Dropping {}% of most active neighbors.", dropPercentage);
        this.dropPercentage = dropPercentage;
        transactionCount = toMap(neighbors);
    }

    /**
     * Recalculates what neighbors are currently spamming. The most active neighbors since the last call of this
     * method will be marked to be spamming.
     * @param neighbors The list of neighbors that should be examined for spamming neighbors.
     */
    public void calculateSpam(List<Neighbor> neighbors) {
        List<Pair<Long, Neighbor>> deltas = new ArrayList<>(neighbors.size());
        neighbors.forEach(neighbor -> {
            Long previous = transactionCount.getOrDefault(neighbor, neighbor.getNumberOfAllTransactions());
            long delta = neighbor.getNumberOfAllTransactions() - previous;
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
    public boolean broadcastTransactionFrom(Neighbor neighbor) {
        return !mostActiveNeighbors.contains(neighbor);
    }

    private Map<Neighbor, Long> toMap(List<Neighbor> neighbors) {
        return neighbors.stream().collect(Collectors.toMap(Function.identity(), Neighbor::getNumberOfAllTransactions));
    }

    private void debugLog(List<Pair<Long, Neighbor>> deltas, int droppedNeighbors) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Deltas: {}", deltas.stream().map(p -> p.hi.getHostAddress() + "=" + p.low).collect(Collectors.joining(", ")));
            LOG.debug("{}% are [{}] neighbor(s).", dropPercentage, droppedNeighbors);
            LOG.debug("Most active neighbor(s): {}", mostActiveNeighbors.stream().map(Neighbor::getHostAddress).collect(Collectors.joining(", ")));
        }
    }





}
